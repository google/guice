/**
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
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Exposed;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.internal.BytecodeGen.Visibility;
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
import java.util.List;
import java.util.Set;

/**
 * A provider that invokes a method and returns its result.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class ProviderMethod<T> implements ProviderWithExtensionVisitor<T>, HasDependencies,
    ProvidesMethodBinding<T> {

  /**
   * Creates a {@link ProviderMethod}.
   *
   * <p>Ideally, we will use {@link FastClass} to invoke the actual method, since it is
   * significantly faster.  However, this will fail if the method is {@code private} or
   * {@code protected}, since fastclass is subject to java access policies.
   */
  static <T> ProviderMethod<T> create(Key<T> key, Method method, Object instance,
      ImmutableSet<Dependency<?>> dependencies, List<Provider<?>> parameterProviders,
      Class<? extends Annotation> scopeAnnotation) {
    int modifiers = method.getModifiers();
    /*if[AOP]*/
    if (!Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers)) {
      try {
        // We use an index instead of FastMethod to save a stack frame.
        return new FastClassProviderMethod<T>(
            key, method, instance, dependencies, parameterProviders, scopeAnnotation);
      } catch (net.sf.cglib.core.CodeGenerationException e) {/* fall-through */}
    }
    /*end[AOP]*/

    if (!Modifier.isPublic(modifiers) ||
        !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      method.setAccessible(true);
    }

    return new ReflectionProviderMethod<T>(
        key, method, instance, dependencies, parameterProviders, scopeAnnotation);
  }

  protected final Object instance;
  protected final Method method;

  private final Key<T> key;
  private final Class<? extends Annotation> scopeAnnotation;
  private final ImmutableSet<Dependency<?>> dependencies;
  private final List<Provider<?>> parameterProviders;
  private final boolean exposed;

  /**
   * @param method the method to invoke. It's return type must be the same type as {@code key}.
   */
  private ProviderMethod(Key<T> key, Method method, Object instance,
      ImmutableSet<Dependency<?>> dependencies, List<Provider<?>> parameterProviders,
      Class<? extends Annotation> scopeAnnotation) {
    this.key = key;
    this.scopeAnnotation = scopeAnnotation;
    this.instance = instance;
    this.dependencies = dependencies;
    this.method = method;
    this.parameterProviders = parameterProviders;
    this.exposed = method.isAnnotationPresent(Exposed.class);
  }

  public Key<T> getKey() {
    return key;
  }

  public Method getMethod() {
    return method;
  }

  // exposed for GIN
  public Object getInstance() {
    return instance;
  }
  
  public Object getEnclosingInstance() {
    return instance;
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

  public T get() {
    Object[] parameters = new Object[parameterProviders.size()];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterProviders.get(i).get();
    }

    try {
      @SuppressWarnings({ "unchecked", "UnnecessaryLocalVariable" })
      T result = (T) doProvision(parameters);
      return result;
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException e) {
      throw Exceptions.rethrowCause(e);
    }
  }

  /** Extension point for our subclasses to implement the provisioning strategy. */
  abstract Object doProvision(Object[] parameters)
      throws IllegalAccessException, InvocationTargetException;

  public Set<Dependency<?>> getDependencies() {
    return dependencies;
  }
  
  @SuppressWarnings("unchecked")
  public <B, V> V acceptExtensionVisitor(BindingTargetVisitor<B, V> visitor,
      ProviderInstanceBinding<? extends B> binding) {
    if (visitor instanceof ProvidesMethodTargetVisitor) {
      return ((ProvidesMethodTargetVisitor<T, V>)visitor).visit(this);
    }
    return visitor.visit(binding);
  }

  @Override public String toString() {
    return "@Provides " + StackTraceElements.forMember(method);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ProviderMethod) {
      ProviderMethod o = (ProviderMethod)obj;
      return method.equals(o.method)
         && instance.equals(o.instance);
    } else {
      return false;
    }
  }
  
  @Override
  public int hashCode() {
    // Avoid calling hashCode on 'instance', which is a user-object
    // that might not be expecting it.
    // (We need to call equals, so we do.  But we can avoid hashCode.)
    return Objects.hashCode(method);
  }

  /*if[AOP]*/
  /**
   * A {@link ProviderMethod} implementation that uses {@link net.sf.cglib.reflect.FastClass#invoke}
   * to invoke the provider method.
   */
  private static final class FastClassProviderMethod<T> extends ProviderMethod<T> {
    final net.sf.cglib.reflect.FastClass fastClass;
    final int methodIndex;

    FastClassProviderMethod(Key<T> key,
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        List<Provider<?>> parameterProviders,
        Class<? extends Annotation> scopeAnnotation) {
      super(key, method, instance, dependencies, parameterProviders, scopeAnnotation);
      // We need to generate a FastClass for the method's class, not the object's class.
      this.fastClass =
          BytecodeGen.newFastClass(method.getDeclaringClass(), Visibility.forMember(method));
      // Use the Signature overload of getIndex because it properly uses return types to identify
      // particular methods.  This is normally irrelevant, except in the case of covariant overrides
      // which java implements with a compiler generated bridge method to implement the override.
      this.methodIndex = fastClass.getIndex(
          new net.sf.cglib.core.Signature(
              method.getName(), org.objectweb.asm.Type.getMethodDescriptor(method)));
      Preconditions.checkArgument(this.methodIndex >= 0, 
          "Could not find method %s in fast class for class %s", 
          method, 
          method.getDeclaringClass());
    }

    @Override public Object doProvision(Object[] parameters)
        throws IllegalAccessException, InvocationTargetException {
      return fastClass.invoke(methodIndex, instance, parameters);
    }
  }
  /*end[AOP]*/

  /**
   * A {@link ProviderMethod} implementation that invokes the method using normal java reflection. 
   */
  private static final class ReflectionProviderMethod<T> extends ProviderMethod<T> {
    ReflectionProviderMethod(Key<T> key,
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        List<Provider<?>> parameterProviders,
        Class<? extends Annotation> scopeAnnotation) {
      super(key, method, instance, dependencies, parameterProviders, scopeAnnotation);
    }

    @Override Object doProvision(Object[] parameters) throws IllegalAccessException,
        InvocationTargetException {
      return method.invoke(instance, parameters);
    }
  }
}
