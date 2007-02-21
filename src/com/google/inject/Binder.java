package com.google.inject;

import com.google.inject.binder.BindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Used by {@link Module} implementations to configure bindings.
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
   * Upon successful creation, the {@link Container} will inject static fields
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
}
