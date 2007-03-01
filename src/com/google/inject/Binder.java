package com.google.inject;

import com.google.inject.binder.BindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Collect configuration data (primarily <i>bindings</i>) from one or more
 * modules, so that this collected information may then be used to construct a
 * new {@link Injector}. There is no public API to create an instance of this
 * interface; instead, your {@link Module} implementations will simply have one
 * passed in automatically.
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
  <T> BindingBuilder<T> bind(Key<T> key);

  /**
   * Creates a binding to a type.
   */
  <T> BindingBuilder<T> bind(TypeLiteral<T> typeLiteral);

  /**
   * Creates a binding to a type.
   */
  <T> BindingBuilder<T> bind(Class<T> type);

  /**
   * Links a key to another binding.
   */
  <T> LinkedBindingBuilder<T> link(Key<T> key);

  /**
   * Links a type to another binding.
   */
  <T> LinkedBindingBuilder<T> link(Class<T> type);

  /**
   * Links a type to another binding.
   */
  <T> LinkedBindingBuilder<T> link(TypeLiteral<T> type);

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
