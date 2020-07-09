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

package com.google.inject.internal;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Exposed;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provides;
import com.google.inject.internal.InternalProviderInstanceBindingImpl.InitializationTiming;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.ProvidesMethodBinding;
import com.google.inject.spi.ProvidesMethodTargetVisitor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A provider that invokes a method and returns its result.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class ProviderMethod<T> extends InternalProviderInstanceBindingImpl.CyclicFactory<T>
    implements HasDependencies, ProvidesMethodBinding<T>, ProviderWithExtensionVisitor<T> {

  /**
   * Creates a {@link ProviderMethod}.
   *
   * <p>Unless {@code skipFastClassGeneration} is set, this will use bytecode generation to invoke
   * the actual method, since it is significantly faster. However, this may fail if the method is
   * {@code private} or {@code protected}, since this approach is subject to java access policies.
   */
  static <T> ProviderMethod<T> create(
      Key<T> key,
      Method method,
      Object instance,
      ImmutableSet<Dependency<?>> dependencies,
      Class<? extends Annotation> scopeAnnotation,
      boolean skipFastClassGeneration,
      Annotation annotation) {
    int modifiers = method.getModifiers();
    /*if[AOP]*/
    if (!skipFastClassGeneration) {
      try {
        BiFunction<Object, Object[], Object> fastMethod = BytecodeGen.fastMethod(method);
        if (fastMethod != null) {
          return new FastClassProviderMethod<T>(
              key, method, instance, dependencies, scopeAnnotation, annotation, fastMethod);
        }
      } catch (Exception | LinkageError e) {
        /* fall-through */
      }
    }
    /*end[AOP]*/

    if (!Modifier.isPublic(modifiers)
        || !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      method.setAccessible(true);
    }

    return new ReflectionProviderMethod<T>(
        key, method, instance, dependencies, scopeAnnotation, annotation);
  }

  protected final Object instance;
  protected final Method method;

  private final Key<T> key;
  private final Class<? extends Annotation> scopeAnnotation;
  private final ImmutableSet<Dependency<?>> dependencies;
  private final boolean exposed;
  private final Annotation annotation;

  /**
   * Set by {@link #initialize(InjectorImpl, Errors)} so it is always available prior to injection.
   */
  private SingleParameterInjector<?>[] parameterInjectors;

  /** @param method the method to invoke. Its return type must be the same type as {@code key}. */
  ProviderMethod(
      Key<T> key,
      Method method,
      Object instance,
      ImmutableSet<Dependency<?>> dependencies,
      Class<? extends Annotation> scopeAnnotation,
      Annotation annotation) {
    // We can be safely initialized eagerly since our bindings must exist statically and it is an
    // error for them not to.
    super(InitializationTiming.EAGER);
    this.key = key;
    this.scopeAnnotation = scopeAnnotation;
    this.instance = instance;
    this.dependencies = dependencies;
    this.method = method;
    this.exposed = method.isAnnotationPresent(Exposed.class);
    this.annotation = annotation;
  }

  @Override
  public Key<T> getKey() {
    return key;
  }

  @Override
  public Method getMethod() {
    return method;
  }

  // exposed for GIN
  public Object getInstance() {
    return instance;
  }

  @Override
  public Object getEnclosingInstance() {
    return instance;
  }

  @Override
  public Annotation getAnnotation() {
    return annotation;
  }

  public void configure(Binder binder) {
    binder = binder.withSource(method);

    if (scopeAnnotation != null) {
      binder.bind(key).toProvider(this).in(scopeAnnotation);
    } else {
      binder.bind(key).toProvider(this);
    }

    if (exposed) {
      // the cast is safe 'cause the only binder we have implements PrivateBinder. If there's a
      // misplaced @Exposed, calling this will add an error to the binder's error queue
      ((PrivateBinder) binder).expose(key);
    }
  }

  @Override
  void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
    parameterInjectors = injector.getParametersInjectors(dependencies.asList(), errors);
  }

  @Override
  protected T doProvision(InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    try {
      T t = doProvision(SingleParameterInjector.getAll(context, parameterInjectors));
      if (t == null && !dependency.isNullable()) {
        InternalProvisionException.onNullInjectedIntoNonNullableDependency(getMethod(), dependency);
      }
      return t;
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null ? userException.getCause() : userException;
      throw InternalProvisionException.errorInProvider(cause).addSource(getSource());
    }
  }

  /** Extension point for our subclasses to implement the provisioning strategy. */
  abstract T doProvision(Object[] parameters)
      throws IllegalAccessException, InvocationTargetException;

  @Override
  public Set<Dependency<?>> getDependencies() {
    return dependencies;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <B, V> V acceptExtensionVisitor(
      BindingTargetVisitor<B, V> visitor, ProviderInstanceBinding<? extends B> binding) {
    if (visitor instanceof ProvidesMethodTargetVisitor) {
      return ((ProvidesMethodTargetVisitor<T, V>) visitor).visit(this);
    }
    return visitor.visit(binding);
  }

  @Override
  public String toString() {
    String annotationString = annotation.toString();
    // Show @Provides w/o the com.google.inject prefix.
    if (annotation.annotationType() == Provides.class) {
      annotationString = "@Provides";
    } else if (annotationString.endsWith("()")) {
      // Remove the common "()" suffix if there are no values.
      annotationString = annotationString.substring(0, annotationString.length() - 2);
    }
    return annotationString + " " + StackTraceElements.forMember(method);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ProviderMethod) {
      ProviderMethod<?> o = (ProviderMethod<?>) obj;
      return method.equals(o.method)
          && Objects.equal(instance, o.instance)
          && annotation.equals(o.annotation);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    // Avoid calling hashCode on 'instance', which is a user-object
    // that might not be expecting it.
    // (We need to call equals, so we do.  But we can avoid hashCode.)
    return Objects.hashCode(method, annotation);
  }

  /*if[AOP]*/
  /**
   * A {@link ProviderMethod} implementation that uses bytecode generation to invoke the provider
   * method.
   */
  private static final class FastClassProviderMethod<T> extends ProviderMethod<T> {
    final BiFunction<Object, Object[], Object> fastMethod;

    FastClassProviderMethod(
        Key<T> key,
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        Class<? extends Annotation> scopeAnnotation,
        Annotation annotation,
        BiFunction<Object, Object[], Object> fastMethod) {
      super(key, method, instance, dependencies, scopeAnnotation, annotation);
      this.fastMethod = fastMethod;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T doProvision(Object[] parameters) throws InvocationTargetException {
      try {
        return (T) fastMethod.apply(instance, parameters);
      } catch (Throwable e) {
        throw new InvocationTargetException(e); // match JDK reflection behaviour
      }
    }
  }
  /*end[AOP]*/

  /**
   * A {@link ProviderMethod} implementation that invokes the method using normal java reflection.
   */
  private static final class ReflectionProviderMethod<T> extends ProviderMethod<T> {
    ReflectionProviderMethod(
        Key<T> key,
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        Class<? extends Annotation> scopeAnnotation,
        Annotation annotation) {
      super(key, method, instance, dependencies, scopeAnnotation, annotation);
    }

    @SuppressWarnings("unchecked")
    @Override
    T doProvision(Object[] parameters) throws IllegalAccessException, InvocationTargetException {
      return (T) method.invoke(instance, parameters);
    }
  }
}
