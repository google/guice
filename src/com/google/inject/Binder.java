/*
 * Copyright (C) 2007 Google Inc.
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

import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Collects configuration information (primarily <i>bindings</i>) which will be
 * used to create an {@link Injector}. Guice provides this object to your
 * application's {@link Module}s so they may each contribute
 * their own bindings.
 *
 * <p>The bindings contributed by {@code Module}s define how the {@code
 * Injector} resolves dependencies. A {@link Key} consisting of a type
 * and optional annotation uniquely identifies a binding within an {@code
 * Injector}.
 *
 * <p>You may bind from a key to:
 *
 * <ul>
 * <li>Another binding, which this binding's key is now "aliased to"
 * <li>Another binding, which references a {@link Provider} for this key
 * <li>A preconstructed instance
 * <li>A preconstructed instance which should be used as the {@link Provider}
 *   for this binding
 * </ul>
 *
 * <p>In addition, a binding may have an associated scope, such as
 * {@link Scopes#SINGLETON}, and singleton bindings may specify eager or lazy
 * initialization.
 *
 * <p>See the users' guide appendix, "How the Injector resolves injection
 * requests," to better understand binding resolution.
 *
 * <p>After an {@code Injector} has been created, its bindings may be
 * examined using methods like {@link Injector#getBinding(Key)}, but this
 * read-only {@link Binding} type is not used when <i>creating</i> the
 * bindings.
 */
public interface Binder {

  /**
   * Binds a method interceptor to methods matched by class and method
   * matchers.
   *
   * @param classMatcher matches classes the interceptor should apply to. For
   *     example: {@code only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For
   *     example: {@code annotatedWith(Transactional.class)}.
   * @param interceptors to bind
   */
  void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors);

  /**
   * Binds a scope to an annotation.
   */
  void bindScope(Class<? extends Annotation> annotationType, Scope scope);

  /**
   * Creates a binding to a key.
   */
  <T> LinkedBindingBuilder<T> bind(Key<T> key);

  /**
   * Creates a binding to a type.
   */
  <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral);

  /**
   * Creates a binding to a type.
   */
  <T> AnnotatedBindingBuilder<T> bind(Class<T> type);

  /**
   * Binds a constant value to an annotation.
   */
  AnnotatedConstantBindingBuilder bindConstant();

  /**
   * Upon successful creation, the {@link Injector} will inject static fields
   * and methods in the given classes.
   *
   * @param types for which static members will be injected
   */
  void requestStaticInjection(Class<?>... types);

  /**
   * Uses the given module to configure more bindings.
   */
  void install(Module module);

  /**
   * Gets the current stage.
   */
  Stage currentStage();

  /**
   * Records an error message which will be presented to the user at a later
   * time. Unlike throwing an exception, this enable us to continue
   * configuring the Injector and discover more errors. Uses {@link
   * String#format(String, Object[])} to insert the arguments into the
   * message.
   */
  void addError(String message, Object... arguments);

  /**
   * Records an exception, the full details of which will be logged, and the
   * message of which will be presented to the user at a later
   * time. If your Module calls something that you worry may fail, you should
   * catch the exception and pass it into this.
   */
  void addError(Throwable t);
}
