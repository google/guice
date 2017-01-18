/*
 * Copyright (C) 2008 Google Inc.
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

import static com.google.common.base.Preconditions.checkState;

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.AnnotatedElementBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * A module whose configuration information is hidden from its environment by default. Only bindings
 * that are explicitly exposed will be available to other modules and to the users of the injector.
 * This module may expose the bindings it creates and the bindings of the modules it installs.
 *
 * <p>A private module can be nested within a regular module or within another private module using
 * {@link Binder#install install()}. Its bindings live in a new environment that inherits bindings,
 * type converters, scopes, and interceptors from the surrounding ("parent") environment. When you
 * nest multiple private modules, the result is a tree of environments where the injector's
 * environment is the root.
 *
 * <p>Guice EDSL bindings can be exposed with {@link #expose(Class) expose()}. {@literal @}{@link
 * com.google.inject.Provides Provides} bindings can be exposed with the {@literal @}{@link Exposed}
 * annotation:
 *
 * <pre>
 * public class FooBarBazModule extends PrivateModule {
 *   protected void configure() {
 *     bind(Foo.class).to(RealFoo.class);
 *     expose(Foo.class);
 *
 *     install(new TransactionalBarModule());
 *     expose(Bar.class).annotatedWith(Transactional.class);
 *
 *     bind(SomeImplementationDetail.class);
 *     install(new MoreImplementationDetailsModule());
 *   }
 *
 *   {@literal @}Provides {@literal @}Exposed
 *   public Baz provideBaz() {
 *     return new SuperBaz();
 *   }
 * }
 * </pre>
 *
 * <p>Private modules are implemented using {@link Injector#createChildInjector(Module[]) parent
 * injectors}. When it can satisfy their dependencies, just-in-time bindings will be created in the
 * root environment. Such bindings are shared among all environments in the tree.
 *
 * <p>The scope of a binding is constrained to its environment. A singleton bound in a private
 * module will be unique to its environment. But a binding for the same type in a different private
 * module will yield a different instance.
 *
 * <p>A shared binding that injects the {@code Injector} gets the root injector, which only has
 * access to bindings in the root environment. An explicit binding that injects the {@code Injector}
 * gets access to all bindings in the child environment.
 *
 * <p>To promote a just-in-time binding to an explicit binding, bind it:
 *
 * <pre>
 *   bind(FooImpl.class);
 * </pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public abstract class PrivateModule implements Module {

  /** Like abstract module, the binder of the current private module */
  private PrivateBinder binder;

  @Override
  public final synchronized void configure(Binder binder) {
    checkState(this.binder == null, "Re-entry is not allowed.");

    // Guice treats PrivateModules specially and passes in a PrivateBinder automatically.
    this.binder = (PrivateBinder) binder.skipSources(PrivateModule.class);
    try {
      configure();
    } finally {
      this.binder = null;
    }
  }

  /**
   * Creates bindings and other configurations private to this module. Use {@link #expose(Class)
   * expose()} to make the bindings in this module available externally.
   */
  protected abstract void configure();

  /** Makes the binding for {@code key} available to other modules and the injector. */
  protected final <T> void expose(Key<T> key) {
    binder().expose(key);
  }

  /**
   * Makes a binding for {@code type} available to other modules and the injector. Use {@link
   * AnnotatedElementBuilder#annotatedWith(Class) annotatedWith()} to expose {@code type} with a
   * binding annotation.
   */
  protected final AnnotatedElementBuilder expose(Class<?> type) {
    return binder().expose(type);
  }

  /**
   * Makes a binding for {@code type} available to other modules and the injector. Use {@link
   * AnnotatedElementBuilder#annotatedWith(Class) annotatedWith()} to expose {@code type} with a
   * binding annotation.
   */
  protected final AnnotatedElementBuilder expose(TypeLiteral<?> type) {
    return binder().expose(type);
  }

  // everything below is copied from AbstractModule

  /** Returns the current binder. */
  protected final PrivateBinder binder() {
    checkState(binder != null, "The binder can only be used inside configure()");
    return binder;
  }

  /** @see Binder#bindScope(Class, Scope) */
  protected final void bindScope(Class<? extends Annotation> scopeAnnotation, Scope scope) {
    binder().bindScope(scopeAnnotation, scope);
  }

  /** @see Binder#bind(Key) */
  protected final <T> LinkedBindingBuilder<T> bind(Key<T> key) {
    return binder().bind(key);
  }

  /** @see Binder#bind(TypeLiteral) */
  protected final <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
    return binder().bind(typeLiteral);
  }

  /** @see Binder#bind(Class) */
  protected final <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
    return binder().bind(clazz);
  }

  /** @see Binder#bindConstant() */
  protected final AnnotatedConstantBindingBuilder bindConstant() {
    return binder().bindConstant();
  }

  /** @see Binder#install(Module) */
  protected final void install(Module module) {
    binder().install(module);
  }

  /** @see Binder#addError(String, Object[]) */
  protected final void addError(String message, Object... arguments) {
    binder().addError(message, arguments);
  }

  /** @see Binder#addError(Throwable) */
  protected final void addError(Throwable t) {
    binder().addError(t);
  }

  /** @see Binder#addError(Message) */
  protected final void addError(Message message) {
    binder().addError(message);
  }

  /** @see Binder#requestInjection(Object) */
  protected final void requestInjection(Object instance) {
    binder().requestInjection(instance);
  }

  /** @see Binder#requestStaticInjection(Class[]) */
  protected final void requestStaticInjection(Class<?>... types) {
    binder().requestStaticInjection(types);
  }

  /*if[AOP]*/
  /**
   * @see Binder#bindInterceptor(com.google.inject.matcher.Matcher,
   *     com.google.inject.matcher.Matcher, org.aopalliance.intercept.MethodInterceptor[])
   */
  protected final void bindInterceptor(
      Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      org.aopalliance.intercept.MethodInterceptor... interceptors) {
    binder().bindInterceptor(classMatcher, methodMatcher, interceptors);
  }
  /*end[AOP]*/

  /** Instructs Guice to require a binding to the given key. */
  protected final void requireBinding(Key<?> key) {
    binder().getProvider(key);
  }

  /** Instructs Guice to require a binding to the given type. */
  protected final void requireBinding(Class<?> type) {
    binder().getProvider(type);
  }

  /** @see Binder#getProvider(Key) */
  protected final <T> Provider<T> getProvider(Key<T> key) {
    return binder().getProvider(key);
  }

  /** @see Binder#getProvider(Class) */
  protected final <T> Provider<T> getProvider(Class<T> type) {
    return binder().getProvider(type);
  }

  /**
   * @see Binder#convertToTypes(com.google.inject.matcher.Matcher,
   *     com.google.inject.spi.TypeConverter)
   */
  protected final void convertToTypes(
      Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter) {
    binder().convertToTypes(typeMatcher, converter);
  }

  /** @see Binder#currentStage() */
  protected final Stage currentStage() {
    return binder().currentStage();
  }

  /** @see Binder#getMembersInjector(Class) */
  protected <T> MembersInjector<T> getMembersInjector(Class<T> type) {
    return binder().getMembersInjector(type);
  }

  /** @see Binder#getMembersInjector(TypeLiteral) */
  protected <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> type) {
    return binder().getMembersInjector(type);
  }

  /**
   * @see Binder#bindListener(com.google.inject.matcher.Matcher, com.google.inject.spi.TypeListener)
   */
  protected void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher, TypeListener listener) {
    binder().bindListener(typeMatcher, listener);
  }

  /**
   * @see Binder#bindListener(Matcher, ProvisionListener...)
   * @since 4.0
   */
  protected void bindListener(
      Matcher<? super Binding<?>> bindingMatcher, ProvisionListener... listeners) {
    binder().bindListener(bindingMatcher, listeners);
  }
}
