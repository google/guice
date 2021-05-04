/*
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

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeListener;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * AbstractModule is a helper class used to add bindings to the Guice injector.
 *
 * <p>Simply extend this class, then you can add bindings by either defining @Provides methods (see
 * https://github.com/google/guice/wiki/ProvidesMethods) or implementing {@link #configure()}, and
 * calling the inherited methods which mirror those found in {@link Binder}. For example:
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

  Binder binder;

  @Override
  public final synchronized void configure(Binder builder) {
    checkState(this.binder == null, "Re-entry is not allowed.");

    this.binder = checkNotNull(builder, "builder");
    try {
      configure();
    } finally {
      this.binder = null;
    }
  }

  /** Configures a {@link Binder} via the exposed methods. */
  protected void configure() {}

  /** Gets direct access to the underlying {@code Binder}. */
  protected Binder binder() {
    checkState(binder != null, "The binder can only be used inside configure()");
    return binder;
  }

  /** @see Binder#bindScope(Class, Scope) */
  protected void bindScope(Class<? extends Annotation> scopeAnnotation, Scope scope) {
    binder().bindScope(scopeAnnotation, scope);
  }

  /** @see Binder#bind(Key) */
  protected <T> LinkedBindingBuilder<T> bind(Key<T> key) {
    return binder().bind(key);
  }

  /** @see Binder#bind(TypeLiteral) */
  protected <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
    return binder().bind(typeLiteral);
  }

  /** @see Binder#bind(Class) */
  protected <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
    return binder().bind(clazz);
  }

  /** @see Binder#bindConstant() */
  protected AnnotatedConstantBindingBuilder bindConstant() {
    return binder().bindConstant();
  }

  /** @see Binder#install(Module) */
  protected void install(Module module) {
    binder().install(module);
  }

  /** @see Binder#addError(String, Object[]) */
  protected void addError(String message, Object... arguments) {
    binder().addError(message, arguments);
  }

  /** @see Binder#addError(Throwable) */
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

  /** @see Binder#requestStaticInjection(Class[]) */
  protected void requestStaticInjection(Class<?>... types) {
    binder().requestStaticInjection(types);
  }

  /**
   * @see {@link Binder#bindInterceptor(com.google.inject.matcher.Matcher, MethodInterceptor[])}.
   */
  protected void bindInterceptor(
      Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      MethodInterceptor... interceptors) {
    binder().bindInterceptor(classMatcher, methodMatcher, interceptors);
  }

  /**
   * Adds a dependency from this module to {@code key}. When the injector is created, Guice will
   * report an error if {@code key} cannot be injected. Note that this requirement may be satisfied
   * by implicit binding, such as a public no-arguments constructor.
   *
   * @since 2.0
   */
  protected void requireBinding(Key<?> key) {
    binder().getProvider(key);
  }

  /**
   * Adds a dependency from this module to {@code type}. When the injector is created, Guice will
   * report an error if {@code type} cannot be injected. Note that this requirement may be satisfied
   * by implicit binding, such as a public no-arguments constructor.
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
  protected void convertToTypes(
      Matcher<? super TypeLiteral<?>> typeMatcher, TypeConverter converter) {
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
   * @see Binder#bindListener(com.google.inject.matcher.Matcher, com.google.inject.spi.TypeListener)
   * @since 2.0
   */
  protected void bindListener(Matcher<? super TypeLiteral<?>> typeMatcher, TypeListener listener) {
    binder().bindListener(typeMatcher, listener);
  }

  /**
   * @see Binder#bindListener(Matcher, ProvisionListener...)
   * @since 4.0
   */
  protected void bindListener(
      Matcher<? super Binding<?>> bindingMatcher, ProvisionListener... listener) {
    binder().bindListener(bindingMatcher, listener);
  }
}
