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

import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.InternalMethodHandles.BIFUNCTION_APPLY_HANDLE;
import static com.google.inject.internal.InternalMethodHandles.castReturnTo;
import static com.google.inject.internal.InternalMethodHandles.castReturnToObject;
import static java.lang.invoke.MethodType.methodType;

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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
public abstract class ProviderMethod<T> extends InternalProviderInstanceBindingImpl.Factory<T>
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
    if (InternalFlags.getUseMethodHandlesOption()) {
      // `unreflect` fails if the method is not public and there is either a security manager
      // blocking access (very rare) or application has set up modules that are not open.
      // In that case we fall back to fast class generation.
      // TODO(lukes): In theory we could use a similar approach to the 'HiddenClassDefiner' and
      // use Unsafe to access the trusted MethodHandles.Lookup object which allows us to access all
      // methods.  However, this is a dangerous and long term unstable approach. The better approach
      // is to add a new API that allows users to pass us an appropriate MethodHandles.Lookup
      // object.  These objects act like `capabilities` which users can use to pass private access
      // to us.  e.g. `Binder.grantAccess(MethodHandles.Lookup lookup)` could allow callers to pass
      // us a lookup object that allows us to access all their methods. Then they could mark their
      // methods as private and still hit this case.
      MethodHandle target = InternalMethodHandles.unreflect(method);
      if (target != null) {
        return new MethodHandleProviderMethod<T>(
            key, method, instance, dependencies, scopeAnnotation, annotation, target);
      }
      // fall through to fast class generation.
    }
    if (InternalFlags.isBytecodeGenEnabled() && !skipFastClassGeneration) {
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
  private int circularFactoryId;

  /**
   * Set by {@link #initialize(InjectorImpl, Errors)} so it is always available prior to injection.
   */
  private SingleParameterInjector<?>[] parameterInjectors;

  /**
   * @param method the method to invoke. Its return type must be the same type as {@code key}.
   */
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
    circularFactoryId = injector.circularFactoryIdFactory.next();
  }

  @Override
  public final T get(final InternalContext context, final Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    @SuppressWarnings("unchecked")
    T result = (T) context.tryStartConstruction(circularFactoryId, dependency);
    if (result != null) {
      // We have a circular reference between bindings. Return a proxy.
      return result;
    }
    return super.get(context, dependency, linked);
  }

  @Override
  protected T doProvision(InternalContext context, Dependency<?> dependency)
      throws InternalProvisionException {
    T t = null;
    try {
      t = doProvision(SingleParameterInjector.getAll(context, parameterInjectors));
      if (t == null && !dependency.isNullable()) {
        InternalProvisionException.onNullInjectedIntoNonNullableDependency(getMethod(), dependency);
      }
      return t;
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    } catch (InternalProvisionException e) {
      throw e.addSource(getSource());
    } catch (InvocationTargetException userException) {
      Throwable cause = userException.getCause() != null ? userException.getCause() : userException;
      throw InternalProvisionException.errorInProvider(cause).addSource(getSource());
    } catch (Throwable unexpected) {
      throw InternalProvisionException.errorInProvider(unexpected).addSource(getSource());
    } finally {
      context.finishConstruction(circularFactoryId, t);
    }
  }

  /** Extension point for our subclasses to implement the provisioning strategy. */
  abstract T doProvision(Object[] parameters)
      throws IllegalAccessException, InvocationTargetException;

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    MethodHandleResult result = super.makeHandle(context, linked);
    checkState(result.cachability == MethodHandleResult.Cachability.ALWAYS);
    // Handle circular proxies.
    return makeCachable(
        InternalMethodHandles.tryStartConstruction(result.methodHandle, circularFactoryId));
  }

  /** Creates a method handle that constructs the object to be injected. */
  @Override
  protected final MethodHandle doGetHandle(LinkageContext context) {
    MethodHandle handle =
        doProvisionHandle(SingleParameterInjector.getAllHandles(context, parameterInjectors));
    InternalMethodHandles.checkHasElementFactoryType(handle);
    // add a dependency parameter so `nullCheckResult` can use it.
    handle = MethodHandles.dropArguments(handle, 1, Dependency.class);
    handle = InternalMethodHandles.nullCheckResult(handle, getMethod());
    // catch everything else and rethrow as an error in provider.
    handle =
        InternalMethodHandles.catchThrowableInProviderAndRethrowWithSource(handle, getSource());
    handle = InternalMethodHandles.finishConstruction(handle, circularFactoryId);

    return handle;
  }

  /**
   * Extension point for our subclasses to implement the provisioning strategy.
   *
   * <p>Should return a handle with the signature {@code (InternalContext) -> Object}
   */
  abstract MethodHandle doProvisionHandle(MethodHandle[] parameters);

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

    @Override
    MethodHandle doProvisionHandle(MethodHandle[] parameters) {
      // We can hit this case if the method is package private and we are using bytecode gen but
      // a security manager is installed that blocks access to `setAccessible`.
      // (Object[]) -> Object
      var apply =
          MethodHandles.insertArguments(BIFUNCTION_APPLY_HANDLE.bindTo(fastMethod), 0, instance)
              // Cast the parameter type to be an Object array
              .asType(methodType(Object.class, Object[].class));

      // (InternalContext) -> Object
      apply =
          MethodHandles.filterArguments(
              apply, 0, InternalMethodHandles.buildObjectArrayFactory(parameters));
      return apply;
    }
  }

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

    @Override
    MethodHandle doProvisionHandle(MethodHandle[] parameters) {
      // We can hit this case if
      // 1. the method/class/parameters are non-public and we are using bytecode gen but a security
      // manager or modules configuration are installed that blocks access to our fastclass
      // generation and the setAccessible method.
      // 2. The method has too many parameters to be supported by method handles (and fastclass also
      // fails)
      // So we fall back to reflection.
      // You might be wondering... why is it that MethodHandles cannot handle methods with the
      // maximum number of parameters but java reflection can? And yes it is true that java
      // reflection is based on MethodHandles, but it supports a fallback for exactly this case that
      // goes through a JVM native method... le sigh...
      // bind to the `Method` object
      // (Object, Object[]) -> Object
      var handle = InternalMethodHandles.invokeHandle(method);
      // insert the instance
      // (Object[]) -> Object
      handle = MethodHandles.insertArguments(handle, 0, instance);
      // Pass the parameters
      // (InternalContext)->Object
      handle =
          MethodHandles.filterArguments(
              handle, 0, InternalMethodHandles.buildObjectArrayFactory(parameters));
      return handle;
    }
  }

  /**
   * A {@link ProviderMethod} implementation that uses bytecode generation to invoke the provider
   * method.
   */
  private static final class MethodHandleProviderMethod<T> extends ProviderMethod<T> {
    private final MethodHandle providerMethod;

    MethodHandleProviderMethod(
        Key<T> key,
        Method method,
        Object instance,
        ImmutableSet<Dependency<?>> dependencies,
        Class<? extends Annotation> scopeAnnotation,
        Annotation annotation,
        MethodHandle providerMethod) {
      super(key, method, instance, dependencies, scopeAnnotation, annotation);
      if (!Modifier.isStatic(method.getModifiers())) {
        providerMethod = providerMethod.bindTo(instance);
      }
      this.providerMethod = providerMethod;
    }

    @Override
    MethodHandle doProvisionHandle(MethodHandle[] parameters) {
      // Cast the parameters to the correct concrete type.
      // Generally the parameters will already have the correct type, but there can be a mismatch
      // if the target method has generic parameters, then the concrete type is Object (or perhaps
      // some other type bound), but the parameters will be cast to the actual instantiated generic.
      var methodType = providerMethod.type();
      for (int i = 0; i < parameters.length; i++) {
        parameters[i] = castReturnTo(parameters[i], methodType.parameterType(i));
      }
      // Pass the parameters to the provider method.
      // The signature is now (...InternalContext)->T, one InternalContext per parameter.
      var handle = MethodHandles.filterArguments(providerMethod, 0, parameters);
      handle = castReturnToObject(handle);
      // Merge all the internalcontext parameters into a single object factory returning the type of
      // the method.
      handle =
          MethodHandles.permuteArguments(
              handle, InternalMethodHandles.ELEMENT_FACTORY_TYPE, new int[parameters.length]);
      return handle;
    }

    @SuppressWarnings("unchecked")
    @Override
    T doProvision(Object[] parameters) throws InvocationTargetException {
      // TODO: b/366058184: once all factories have been migrated to MethodHandles this should be
      // unreachable.
      try {
        return (T) providerMethod.invokeWithArguments(parameters);
      } catch (Throwable e) {
        throw new InvocationTargetException(e); // match JDK reflection behaviour
      }
    }
  }
}
