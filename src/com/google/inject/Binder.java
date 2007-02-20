package com.google.inject;

import com.google.inject.BinderImpl.BindingBuilder;
import com.google.inject.BinderImpl.ConstantBindingBuilder;
import com.google.inject.BinderImpl.LinkedBindingBuilder;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Javadoc.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public interface Binder {

  /**
   * Applies the given method interceptor to the methods matched by the class
   * and method matchers.
   *
   * @param classMatcher matches classes the interceptor should apply to. For
   *     example: {@code only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For
   *     example: {@code annotatedWith(Transactional.class)}.
   * @param interceptors to apply
   */
  void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors);

  /**
   * Binds a scope to an annotation.
   */
  void bindScope(Class<? extends Annotation> annotationType,
          Scope scope);

  /**
   * Binds the given key.
   */
  <T> BindingBuilder<T> bind(Key<T> key);

  /**
   * Binds the given type.
   */
  <T> BindingBuilder<T> bind(TypeLiteral<T> typeLiteral);

  <T> BindingBuilder<T> bind(Class<T> clazz);

  <T> LinkedBindingBuilder<T> link(Key<T> key);

  <T> LinkedBindingBuilder<T> link(Class<T> type);

  <T> LinkedBindingBuilder<T> link(TypeLiteral<T> type);

  ConstantBindingBuilder bindConstant(Annotation annotation);

  ConstantBindingBuilder bindConstant(
      Class<? extends Annotation> annotationType);

  /**
   * Upon successful creation, the {@link Container} will inject static fields
   * and methods in the given classes.
   *
   * @param types for which static members will be injected
   */
  void requestStaticInjection(Class<?>... types);

  void install(Module module);
}
