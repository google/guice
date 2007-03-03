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
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Collects configuration information (primarily <i>bindings</i>) which will be
 * used to create an {@link Injector}.  Guice provides this object to your
 * application's {@link Module} implementors so they may each contribute their
 * own settings.
 *
 * The bindings contributed to an Injector are what control how the
 * Injector resolves injection requests.  A binding is uniquely identified
 * within an Injector by the combination of a Java type and an <i>optional</i>
 * annotation value.  It matches this key to one of:
 *
 * <ul>
 * <li>Another binding, which this binding's key is now "aliased to"
 * <li>Another binding, which references a {@link Provider} for this key
 * <li>A preconstructed instance which should be used to fulfill requests for
 *     this binding
 * <li>A preconstructed instance which should be used as the {@link Provider} to
 *     fulfill requests for this binding
 * </ul>
 *
 * In addition, a binding may have an associated scope specifier, such as
 * {@link Scopes#SINGLETON}, and singleton bindings may specify eager or lazy
 * initialization.
 *
 * <p>See the user's guide appendix, "How the Injector resolves injection
 * requests" to better understand the effects of bindings.
 *
 * After an Injector has been created, its bindings may be examined using
 * methods like {@link Injector#getBinding(Key)}, but this read-only
 * {@link Binding} type is not used when <i>creating</i> the bindings.
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
  ConstantBindingBuilder bindConstant(Annotation annotation);

  /**
   * Binds a constant value to an annotation.
   */
  ConstantBindingBuilder bindConstant(
      Class<? extends Annotation> annotationType);

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
