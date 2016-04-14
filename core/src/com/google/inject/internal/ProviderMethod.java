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
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Exposed;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;
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
   * <p>Unless {@code skipFastClassGeneration} is set, this will use
   * {@link net.sf.cglib.reflect.FastClass} to invoke the actual method, since it is significantly
   * faster. However, this will fail if the method is {@code private} or {@code protected}, since
   * fastclass is subject to java access policies.
   */
  static <T> ProviderMethod<T> create(Key<T> key, Method method, Object instance,
      ImmutableSet<Dependency<?>> dependencies, List<Provider<?>> parameterProviders,
      Class<? extends Annotation> scopeAnnotation, boolean skipFastClassGeneration,
      Annotation annotation) {
    int modifiers = method.getModifiers();
    /*if[AOP]*/
    if (!skipFastClassGeneration) {
      try {
        net.sf.cglib.reflect.FastClass fc = BytecodeGen.newFastClassForMember(method);
        if (fc != null) {
          return new FastClassProviderMethod<T>(key,
              fc,
              method,
              instance,
              dependencies,
              parameterProviders,
              scopeAnnotation,
              annotation);
        }
      } catch (net.sf.cglib.core.CodeGenerationException e) {/* fall-through */}
    }
    /*end[AOP]*/

    if (!Modifier.isPublic(modifiers) ||
        !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
      method.setAccessible(true);
    }

    return new ReflectionProviderMethod<T>(key,
        method,
        instance,
        dependencies,
        parameterProviders,
        scopeAnnotation,
        annotation);
  }

  protected final Object instance;
  protected final Method method;

  private final Key<T> key;
  private final Class<? extends Annotation> scopeAnnotation;
  private final ImmutableSet<Dependency<?>> dependencies;
  private final List<Provider<?>> parameterProviders;
  private final boolean exposed;
  private final Annotation annotation;

  /**
   * @param method the method to invoke. It's return type must be the same type as {@code key}.
   */
  private ProviderMethod(Key<T> key, Method method, Object instance,
      ImmutableSet<Dependency<?>> dependencies, List<Provider<?>> parameterProviders,
      Class<? extends Annotation> scopeAnnotation, Annotation annotation) {
    this.key = key;
    this.scopeAnnotation = scopeAnnotation;
    this.instance = instance;
    this.dependencies = dependencies;
    this.method = method;
    this.parameterProviders = parameterProviders;
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
  public T get() {
    Object[] parameters = new Object[parameterProviders.size()];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterProviders.get(i).get();
    }

    try {
      return doProvision(parameters);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InvocationTargetException e) {
      throw Exceptions.rethrowCause(e);
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
  public <B, V> V acceptExtensionVisitor(BindingTargetVisitor<B, V> visitor,
      ProviderInstanceBinding<? extends B> binding) {
    if (visitor instanceof ProvidesMethodTargetVisitor) {
      return ((ProvidesMethodTargetVisitor<T, V>)visitor).visit(this);
    }
    return visitor.visit(binding);
  }

  @Override public String toString() {
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
         && instance.equals(o.instance)
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
   * A {@link ProviderMethod} implementation that uses {@link net.sf.cglib.reflect.FastClass#invoke}
   * to invoke the provider method.
   */
  private static final class FastClassProviderMethod<T> extends ProviderMethod<T> {
    final net.sf.cglib.reflect.FastClass fastClass;
    final int methodIndex;

    FastClassProviderMethod(Key<T> key,
        net.sf.cglib.reflect.FastClass fc, 
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        List<Provider<?>> parameterProviders,
        Class<? extends Annotation> scopeAnnotation,
        Annotation annotation) {
      super(key,
          method,
          instance,
          dependencies,
          parameterProviders,
          scopeAnnotation,
          annotation);
      this.fastClass = fc;
      this.methodIndex = fc.getMethod(method).getIndex();
    }

    @SuppressWarnings("unchecked")
    @Override
    public T doProvision(Object[] parameters)
        throws IllegalAccessException, InvocationTargetException {
      return (T) fastClass.invoke(methodIndex, instance, parameters);
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
        Class<? extends Annotation> scopeAnnotation,
        Annotation annotation) {
      super(key,
          method,
          instance,
          dependencies,
          parameterProviders,
          scopeAnnotation,
          annotation);
    }

    @SuppressWarnings("unchecked")
    @Override
    T doProvision(Object[] parameters) throws IllegalAccessException, InvocationTargetException {
      return (T) method.invoke(instance, parameters);
    }
  }

  static <T> BindingImpl<T> createBinding(
      InjectorImpl injector,
      Key<T> key,
      ProviderMethod<T> providerMethod,
      Object source,
      Scoping scoping) {
    Factory<T> factory = new Factory<T>(source, providerMethod);
    InternalFactory<? extends T> scopedFactory =
        Scoping.scope(key, injector, factory, source, scoping);
    return new ProviderMethodProviderInstanceBindingImpl<T>(
        injector, key, source, scopedFactory, scoping, providerMethod, factory);
  }

  private static final class ProviderMethodProviderInstanceBindingImpl<T>
      extends ProviderInstanceBindingImpl<T> implements DelayedInitialize {
    final Factory<T> factory;

    ProviderMethodProviderInstanceBindingImpl(
        InjectorImpl injector,
        Key<T> key,
        Object source,
        InternalFactory<? extends T> internalFactory,
        Scoping scoping,
        // TODO(lukes): it is a little strange that we expose the ProviderMethod as the
        // userProvider.  Maybe we should expose BindingImpl.getProvider() and then we could drop
        // the ProviderLookups stored by ProviderMethod.  It probably isn't a big deal either way
        // but having 2 ways to invoke the method is kind of weird.
        ProviderMethod<T> providerInstance,
        Factory<T> factory) {
      super(
          injector,
          key,
          source,
          internalFactory,
          scoping,
          providerInstance,
          ImmutableSet.<InjectionPoint>of());
      this.factory = factory;
    }

    @Override
    public void initialize(InjectorImpl injector, Errors errors) throws ErrorsException {
      factory.parameterInjectors =
          injector.getParametersInjectors(factory.providerMethod.dependencies.asList(), errors);
      factory.provisionCallback = injector.provisionListenerStore.get(this);
    }
  }

  private static final class Factory<T> implements InternalFactory<T> {
    private final Object source;
    private final ProviderMethod<T> providerMethod;
    private ProvisionListenerStackCallback<T> provisionCallback;
    private SingleParameterInjector<?>[] parameterInjectors;

    Factory(Object source, ProviderMethod<T> providerMethod) {
      this.source = source;
      this.providerMethod = providerMethod;
    }

    @Override
    public T get(
        final Errors errors,
        final InternalContext context,
        final Dependency<?> dependency,
        boolean linked)
        throws ErrorsException {
      final ConstructionContext<T> constructionContext = context.getConstructionContext(this);
      // We have a circular reference between bindings. Return a proxy.
      if (constructionContext.isConstructing()) {
        Class<?> expectedType = dependency.getKey().getTypeLiteral().getRawType();
        // TODO: if we can't proxy this object, can we proxy the other object?
        @SuppressWarnings("unchecked")
        T proxyType =
            (T) constructionContext.createProxy(errors, context.getInjectorOptions(), expectedType);
        return proxyType;
      }
      // Optimization: Don't go through the callback stack if no one's listening.
      constructionContext.startConstruction();
      try {
        if (!provisionCallback.hasListeners()) {
          return provision(errors, dependency, context, constructionContext);
        } else {
          return provisionCallback.provision(
              errors,
              context,
              new ProvisionCallback<T>() {
                @Override
                public T call() throws ErrorsException {
                  return provision(errors, dependency, context, constructionContext);
                }
              });
        }
      } finally {
        constructionContext.removeCurrentReference();
        constructionContext.finishConstruction();
      }
    }

    T provision(
        Errors errors,
        Dependency<?> dependency,
        InternalContext context,
        ConstructionContext<T> constructionContext)
        throws ErrorsException {
      try {
        T t = providerMethod.doProvision(
            SingleParameterInjector.getAll(errors, context, parameterInjectors));
        errors.checkForNull(t, providerMethod.getMethod(), dependency);
        constructionContext.setProxyDelegates(t);
        return t;
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      } catch (InvocationTargetException userException) {
        Throwable cause =
            userException.getCause() != null ? userException.getCause() : userException;
        throw errors
            .withSource(source)
            .errorInProvider(cause)
            .toException();
      }
    }
    
    @Override
    public String toString() {
      return providerMethod.toString();
    }
  }
}
