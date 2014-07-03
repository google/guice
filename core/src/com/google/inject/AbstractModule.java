/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;

/**
 * A support class for {@link Module}s which reduces repetition and results in
 * a more readable configuration. Simply extend this class, implement {@link
 * #configure()}, and call the inherited methods which mirror those found in
 * {@link Binder}. For example:
 *
 * <pre>
 * public class MyModule extends AbstractModule {
 *   protected void configure() {
 *     bind(Service.class).to(ServiceImpl.class).in(Singleton.class);
 *     bind(CreditCardPaymentService.class);
 *     bind(PaymentService.class).to(CreditCardPaymentService.class);
 *     bindConstant().annotatedWith(Names.named("port")).to(8080);
 *   }
 * }
 * </pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class AbstractModule implements Module {

    protected Binder binder;

    @SuppressWarnings("rawtypes")
    private AnnotatedBindingBuilder noOpAnnotatedBindingBuilder = new NoOpAnnotatedBindingBuilder();
    private AnnotationDatabaseFinder annotationDatabaseFinder;

    /**
     * Sets the {@link AnnotationDatabaseFinder} to filter classes to bind.
     * The {@link AnnotationDatabaseFinder} will know which classes can be injected and can't, 
     * it also knows if a given annotation is used or not.
     * <br/>
     * This method <b>must</b> be called before configure to take effect.
     * @param annotationDatabaseFinder used to filter classes to bind.
     */
    public void setAnnotationDatabaseFinder(AnnotationDatabaseFinder annotationDatabaseFinder) {
        this.annotationDatabaseFinder = annotationDatabaseFinder;
    }

    public final synchronized void configure(Binder builder) {
        checkState(this.binder == null, "Re-entry is not allowed.");

        this.binder = checkNotNull(builder, "builder");
        try {
            configure();
        }
        finally {
            this.binder = null;
        }
    }

    /**
     * Configures a {@link Binder} via the exposed methods.
     */
    protected abstract void configure();

    /**
     * Gets direct access to the underlying {@code Binder}.
     */
    protected Binder binder() {
        checkState(binder != null, "The binder can only be used inside configure()");
        return binder;
    }

    /**
     * @see Binder#bindScope(Class, Scope)
     */
    protected void bindScope(Class<? extends Annotation> scopeAnnotation,
            Scope scope) {
        binder().bindScope(scopeAnnotation, scope);
    }

    /**
     * @see Binder#bind(Key)
     */
    protected <T> LinkedBindingBuilder<T> bind(Key<T> key) {
        return binder().bind(key);
    }

    /**
     * @see Binder#bind(TypeLiteral)
     */
    protected <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
        return binder().bind(typeLiteral);
    }

    /**
     * If an {@link AnnotationDatabaseFinder} is used, this method will only bind
     * the classes that are supposed to be injected.
     * @see Binder#bind(Class)
     */
    @SuppressWarnings("unchecked")
    protected <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
        if( isInjectable(clazz) ) {
            return binder.bind(clazz);
        } else {
            return noOpAnnotatedBindingBuilder;
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> AnnotatedBindingBuilder<T> superbind(Class<T> clazz) {
        return binder.bind(clazz);
    }

    /**
     * @see Binder#bindConstant()
     */
    protected AnnotatedConstantBindingBuilder bindConstant() {
        return binder().bindConstant();
    }

    /**
     * If a module has {@link AnnotationDatabaseFinder} then it is passed 
     * to each submodule prior to installing it.
     * @see Binder#install(Module).
     */
    protected void install(Module module) {
        if( annotationDatabaseFinder != null && module instanceof AbstractModule) {
            ((AbstractModule)module).setAnnotationDatabaseFinder(annotationDatabaseFinder);
        }
        binder().install(module);
    }

    /**
     * @see Binder#addError(String, Object[])
     */
    protected void addError(String message, Object... arguments) {
        binder().addError(message, arguments);
    }

    /**
     * @see Binder#addError(Throwable) 
     */
    protected void addError(Throwable t) {
        binder().addError(t);
    }

    /**
     * @see Binder#addError(Message)
     * @since 2.0
     */
    protected void addError(Message message) {
        binder().addError(message);
    }

    /**
     * @see Binder#requestInjection(Object)
     * @since 2.0
     */
    protected void requestInjection(Object instance) {
        binder().requestInjection(instance);
    }

    /**
     * @see Binder#requestStaticInjection(Class[])
     */
    protected void requestStaticInjection(Class<?>... types) {
        binder().requestStaticInjection(types);
    }

    /*if[AOP]*/
    /**
     * @see Binder#bindInterceptor(com.google.inject.matcher.Matcher,
     *  com.google.inject.matcher.Matcher,
     *  org.aopalliance.intercept.MethodInterceptor[])
     */
    protected void bindInterceptor(Matcher<? super Class<?>> classMatcher,
            Matcher<? super Method> methodMatcher,
            org.aopalliance.intercept.MethodInterceptor... interceptors) {
        binder().bindInterceptor(classMatcher, methodMatcher, interceptors);
    }
    /*end[AOP]*/

    /**
     * Adds a dependency from this module to {@code key}. When the injector is
     * created, Guice will report an error if {@code key} cannot be injected.
     * Note that this requirement may be satisfied by implicit binding, such as
     * a public no-arguments constructor.
     *
     * @since 2.0
     */
    protected void requireBinding(Key<?> key) {
        binder().getProvider(key);
    }

    /**
     * Adds a dependency from this module to {@code type}. When the injector is
     * created, Guice will report an error if {@code type} cannot be injected.
     * Note that this requirement may be satisfied by implicit binding, such as
     * a public no-arguments constructor.
     *
     * @since 2.0
     */
    protected void requireBinding(Class<?> type) {
        binder().getProvider(type);
    }

    /**
     * @see Binder#getProvider(Key)
     * @since 2.0
     */
    protected <T> Provider<T> getProvider(Key<T> key) {
        return binder().getProvider(key);
    }

    /**
     * @see Binder#getProvider(Class)
     * @since 2.0
     */
    protected <T> Provider<T> getProvider(Class<T> type) {
        return binder().getProvider(type);
    }

    /**
     * @see Binder#convertToTypes
     * @since 2.0
     */
    protected void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
            TypeConverter converter) {
        binder().convertToTypes(typeMatcher, converter);
    }

    /**
     * @see Binder#currentStage() 
     * @since 2.0
     */
    protected Stage currentStage() {
        return binder().currentStage();
    }

    /**
     * @see Binder#getMembersInjector(Class)
     * @since 2.0
     */
    protected <T> MembersInjector<T> getMembersInjector(Class<T> type) {
        return binder().getMembersInjector(type);
    }

    /**
     * @see Binder#getMembersInjector(TypeLiteral)
     * @since 2.0
     */
    protected <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> type) {
        return binder().getMembersInjector(type);
    }

    /**
     * @see Binder#bindListener(com.google.inject.matcher.Matcher,
     *  com.google.inject.spi.TypeListener)
     * @since 2.0
     */
    protected void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher,
            TypeListener listener) {
        binder().bindListener(typeMatcher, listener);
    }

    /**
     * @see Binder#bindListener(Matcher, ProvisionListener...)
     * @since 4.0
     */
    protected void bindListener(Matcher<? super Binding<?>> bindingMatcher,
            ProvisionListener... listener) {
        binder().bindListener(bindingMatcher, listener);
    }

    /**
     * Indicates whether or not instances of a given class can possibly be injected or not.
     * @param clazz the class whose instance are injectable or not.
     * @return Indicates whether or not instances of a given class can possibly be injected or not
     * according to the {@link AnnotationDatabaseFinder} used by the module. If no {@link AnnotationDatabaseFinder}
     * is used then this method always return true.
     */
    @SuppressWarnings("rawtypes")
    protected boolean isInjectable(Class clazz) {
        return annotationDatabaseFinder == null || annotationDatabaseFinder.getBindableClassesSet().contains(clazz.getName() );
    }

    /**
     * Indicates if a given annotation is used according to the {@link AnnotationDatabaseFinder}.
     * @param annotationClass the annotation class (like {@link Inject}).
     * @return true or false whether this annotation is used according to the {@link AnnotationDatabaseFinder}.
     * If no {@link AnnotationDatabaseFinder} is used, then this method always return true.
     */
    @SuppressWarnings("rawtypes")
    protected boolean hasInjectionPointsForAnnotation(Class annotationClass) {
        return annotationDatabaseFinder == null 
                || annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedConstructorSet().containsKey(annotationClass.getName())
                || annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedMethodSet().containsKey(annotationClass.getName())
                || annotationDatabaseFinder.getMapAnnotationToMapClassContainingInjectionToInjectedFieldSet().containsKey(annotationClass.getName());
    }

    public AnnotationDatabaseFinder getAnnotationDatabaseFinder() {
        return annotationDatabaseFinder;
    }

}
