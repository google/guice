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

package com.google.inject.throwingproviders;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.util.Types;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * Builds a binding for a {@link CheckedProvider}.
 *
 * <p>You can use a fluent API and custom providers:
 *
 * <pre><code>ThrowingProviderBinder.create(binder())
 *    .bind(RemoteProvider.class, Customer.class)
 *    .to(RemoteCustomerProvider.class)
 *    .in(RequestScope.class);
 * </code></pre>
 *
 * or, you can use throwing provider methods:
 *
 * <pre><code>class MyModule extends AbstractModule {
 *   configure() {
 *     install(ThrowingProviderBinder.forModule(this));
 *   }
 *
 *   {@literal @}CheckedProvides(RemoteProvider.class)
 *   {@literal @}RequestScope
 *   Customer provideCustomer(FlakyCustomerCreator creator) throws RemoteException {
 *     return creator.getCustomerOrThrow();
 *   }
 * }
 * </code></pre>
 *
 * You also can declare that a CheckedProvider construct a particular class whose constructor throws
 * an exception:
 *
 * <pre><code>ThrowingProviderBinder.create(binder())
 *    .bind(RemoteProvider.class, Customer.class)
 *    .providing(CustomerImpl.class)
 *    .in(RequestScope.class);
 * </code></pre>
 *
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author sameb@google.com (Sam Berlin)
 */
public class ThrowingProviderBinder {

  private static final TypeLiteral<CheckedProvider<?>> CHECKED_PROVIDER_TYPE =
      new TypeLiteral<CheckedProvider<?>>() {};

  private static final TypeLiteral<CheckedProviderMethod<?>> CHECKED_PROVIDER_METHOD_TYPE =
      new TypeLiteral<CheckedProviderMethod<?>>() {};

  private final Binder binder;

  private ThrowingProviderBinder(Binder binder) {
    this.binder = binder;
  }

  public static ThrowingProviderBinder create(Binder binder) {
    return new ThrowingProviderBinder(
        binder.skipSources(
            ThrowingProviderBinder.class, ThrowingProviderBinder.SecondaryBinder.class));
  }

  /**
   * Returns a module that installs {@literal @}{@link CheckedProvides} methods.
   *
   * @since 3.0
   */
  public static Module forModule(Module module) {
    return CheckedProviderMethodsModule.forModule(module);
  }

  /** @deprecated Use {@link #bind(Class, Class)} or {@link #bind(Class, TypeLiteral)} instead. */
  @Deprecated
  public <P extends CheckedProvider> SecondaryBinder<P, ?> bind(
      Class<P> interfaceType, Type clazz) {
    return new SecondaryBinder<P, Object>(interfaceType, clazz);
  }

  /** @since 4.0 */
  public <P extends CheckedProvider, T> SecondaryBinder<P, T> bind(
      Class<P> interfaceType, Class<T> clazz) {
    return new SecondaryBinder<P, T>(interfaceType, clazz);
  }

  /** @since 4.0 */
  public <P extends CheckedProvider, T> SecondaryBinder<P, T> bind(
      Class<P> interfaceType, TypeLiteral<T> typeLiteral) {
    return new SecondaryBinder<P, T>(interfaceType, typeLiteral.getType());
  }

  public class SecondaryBinder<P extends CheckedProvider, T> {
    private final Class<P> interfaceType;
    private final Type valueType;
    private final List<Class<? extends Throwable>> exceptionTypes;
    private final boolean valid;

    private Class<? extends Annotation> annotationType;
    private Annotation annotation;
    private Key<P> interfaceKey;
    private boolean scopeExceptions = true;

    public SecondaryBinder(Class<P> interfaceType, Type valueType) {
      this.interfaceType = checkNotNull(interfaceType, "interfaceType");
      this.valueType = checkNotNull(valueType, "valueType");
      if (checkInterface()) {
        this.exceptionTypes = getExceptionType(interfaceType);
        valid = true;
      } else {
        valid = false;
        this.exceptionTypes = ImmutableList.of();
      }
    }

    List<Class<? extends Throwable>> getExceptionTypes() {
      return exceptionTypes;
    }

    Key<P> getKey() {
      return interfaceKey;
    }

    public SecondaryBinder<P, T> annotatedWith(Class<? extends Annotation> annotationType) {
      if (!(this.annotationType == null && this.annotation == null)) {
        throw new IllegalStateException("Cannot set annotation twice");
      }
      this.annotationType = annotationType;
      return this;
    }

    public SecondaryBinder<P, T> annotatedWith(Annotation annotation) {
      if (!(this.annotationType == null && this.annotation == null)) {
        throw new IllegalStateException("Cannot set annotation twice");
      }
      this.annotation = annotation;
      return this;
    }

    /**
     * Determines if exceptions should be scoped. By default exceptions are scoped.
     *
     * @param scopeExceptions whether exceptions should be scoped.
     * @since 4.0
     */
    public SecondaryBinder<P, T> scopeExceptions(boolean scopeExceptions) {
      this.scopeExceptions = scopeExceptions;
      return this;
    }

    public ScopedBindingBuilder to(P target) {
      Key<P> targetKey = Key.get(interfaceType, UniqueAnnotations.create());
      binder.bind(targetKey).toInstance(target);
      return to(targetKey);
    }

    public ScopedBindingBuilder to(Class<? extends P> targetType) {
      return to(Key.get(targetType));
    }

    /** @since 4.0 */
    public ScopedBindingBuilder providing(Class<? extends T> cxtorClass) {
      return providing(TypeLiteral.get(cxtorClass));
    }

    /** @since 4.0 */
    @SuppressWarnings("unchecked") // safe because this is the cxtor of the literal
    public ScopedBindingBuilder providing(TypeLiteral<? extends T> cxtorLiteral) {
      // Find a constructor that has @ThrowingInject.
      Constructor<? extends T> cxtor =
          CheckedProvideUtils.findThrowingConstructor(cxtorLiteral, binder);

      final Provider<T> typeProvider;
      final Key<? extends T> typeKey;
      // If we found an injection point, then bind the cxtor to a unique key
      if (cxtor != null) {
        // Validate the exceptions are consistent with the CheckedProvider interface.
        CheckedProvideUtils.validateExceptions(
            binder, cxtorLiteral.getExceptionTypes(cxtor), exceptionTypes, interfaceType);

        typeKey = Key.get(cxtorLiteral, UniqueAnnotations.create());
        binder.bind(typeKey).toConstructor((Constructor) cxtor).in(Scopes.NO_SCOPE);
        typeProvider = binder.getProvider((Key<T>) typeKey);
      } else {
        // never used, but need it assigned.
        typeProvider = null;
        typeKey = null;
      }

      // Create a CheckedProvider that calls our cxtor
      CheckedProvider<T> checkedProvider =
          new CheckedProviderWithDependencies<T>() {
            @Override
            public T get() throws Exception {
              try {
                return typeProvider.get();
              } catch (ProvisionException pe) {
                // Rethrow the provision cause as the actual exception
                if (pe.getCause() instanceof Exception) {
                  throw (Exception) pe.getCause();
                } else if (pe.getCause() instanceof Error) {
                  throw (Error) pe.getCause();
                } else {
                  // If this failed because of multiple reasons (ie, more than
                  // one dependency failed due to scoping errors), then
                  // the ProvisionException won't have a cause, so we need
                  // to rethrow it as-is.
                  throw pe;
                }
              }
            }

            @Override
            public Set<Dependency<?>> getDependencies() {
              return ImmutableSet.<Dependency<?>>of(Dependency.get(typeKey));
            }
          };

      Key<CheckedProvider<?>> targetKey =
          Key.get(CHECKED_PROVIDER_TYPE, UniqueAnnotations.create());
      binder.bind(targetKey).toInstance(checkedProvider);
      return toInternal(targetKey);
    }

    ScopedBindingBuilder toProviderMethod(CheckedProviderMethod<?> target) {
      Key<CheckedProviderMethod<?>> targetKey =
          Key.get(CHECKED_PROVIDER_METHOD_TYPE, UniqueAnnotations.create());
      binder.bind(targetKey).toInstance(target);

      return toInternal(targetKey);
    }

    @SuppressWarnings("unchecked") // P only extends the raw type of CheckedProvider
    public ScopedBindingBuilder to(Key<? extends P> targetKey) {
      checkNotNull(targetKey, "targetKey");
      return toInternal((Key<? extends CheckedProvider<?>>) targetKey);
    }

    private ScopedBindingBuilder toInternal(final Key<? extends CheckedProvider<?>> targetKey) {
      final Key<Result> resultKey = Key.get(Result.class, UniqueAnnotations.create());
      // Note that this provider will behave like the final provider Guice creates.
      // It will especially do scoping if the user adds that.
      final Provider<Result> resultProvider = binder.getProvider(resultKey);
      final Provider<? extends CheckedProvider<?>> targetProvider = binder.getProvider(targetKey);
      interfaceKey = createKey();

      // don't bother binding the proxy type if this is in an invalid state.
      if (valid) {
        binder
            .bind(interfaceKey)
            .toProvider(
                new ProviderWithDependencies<P>() {
                  private final P instance =
                      interfaceType.cast(
                          Proxy.newProxyInstance(
                              interfaceType.getClassLoader(),
                              new Class<?>[] {interfaceType},
                              new InvocationHandler() {
                                @Override
                                public Object invoke(Object proxy, Method method, Object[] args)
                                    throws Throwable {
                                  // Allow methods like .equals(..), .hashcode(..), .toString(..) to work.
                                  if (method.getDeclaringClass() == Object.class) {
                                    return method.invoke(this, args);
                                  }

                                  if (scopeExceptions) {
                                    return resultProvider.get().getOrThrow();
                                  } else {
                                    Result result;
                                    try {
                                      result = resultProvider.get();
                                    } catch (ProvisionException pe) {
                                      Throwable cause = pe.getCause();
                                      if (cause instanceof ResultException) {
                                        throw ((ResultException) cause).getCause();
                                      } else {
                                        throw pe;
                                      }
                                    }
                                    return result.getOrThrow();
                                  }
                                }
                              }));

                  @Override
                  public P get() {
                    return instance;
                  }

                  @Override
                  public Set<Dependency<?>> getDependencies() {
                    return ImmutableSet.<Dependency<?>>of(Dependency.get(resultKey));
                  }
                });
      }

      // The provider is unscoped, but the user may apply a scope to it through the
      // ScopedBindingBuilder this returns.
      return binder.bind(resultKey).toProvider(createResultProvider(targetKey, targetProvider));
    }

    private ProviderWithDependencies<Result> createResultProvider(
        final Key<? extends CheckedProvider<?>> targetKey,
        final Provider<? extends CheckedProvider<?>> targetProvider) {
      return new ProviderWithDependencies<Result>() {
        @Override
        public Result get() {
          try {
            return Result.forValue(targetProvider.get().get());
          } catch (Exception e) {
            for (Class<? extends Throwable> exceptionType : exceptionTypes) {
              if (exceptionType.isInstance(e)) {
                if (scopeExceptions) {
                  return Result.forException(e);
                } else {
                  throw new ResultException(e);
                }
              }
            }

            if (e instanceof RuntimeException) {
              throw (RuntimeException) e;
            } else {
              // this should never happen
              throw new RuntimeException(e);
            }
          }
        }

        @Override
        public Set<Dependency<?>> getDependencies() {
          return ImmutableSet.<Dependency<?>>of(Dependency.get(targetKey));
        }
      };
    }

    /**
     * Returns the exception type declared to be thrown by the get method of {@code interfaceType}.
     */
    private List<Class<? extends Throwable>> getExceptionType(Class<P> interfaceType) {
      try {
        Method getMethod = interfaceType.getMethod("get");
        List<TypeLiteral<?>> exceptionLiterals =
            TypeLiteral.get(interfaceType).getExceptionTypes(getMethod);
        List<Class<? extends Throwable>> results = Lists.newArrayList();
        for (TypeLiteral<?> exLiteral : exceptionLiterals) {
          results.add(exLiteral.getRawType().asSubclass(Throwable.class));
        }
        return results;
      } catch (SecurityException e) {
        throw new IllegalStateException("Not allowed to inspect exception types", e);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException("No 'get'method available", e);
      }
    }

    private boolean checkInterface() {
      try {
        ProviderChecker.checkInterface(interfaceType, Optional.of(valueType));
        return true;
      } catch (IllegalArgumentException e) {
        binder.addError(e.getMessage());
        return false;
      }
    }

    @SuppressWarnings({"unchecked"})
    private Key<P> createKey() {
      TypeLiteral<P> typeLiteral;
      if (interfaceType.getTypeParameters().length == 1) {
        ParameterizedType type =
            Types.newParameterizedTypeWithOwner(
                interfaceType.getEnclosingClass(), interfaceType, valueType);
        typeLiteral = (TypeLiteral<P>) TypeLiteral.get(type);
      } else {
        typeLiteral = TypeLiteral.get(interfaceType);
      }

      if (annotation != null) {
        return Key.get(typeLiteral, annotation);

      } else if (annotationType != null) {
        return Key.get(typeLiteral, annotationType);

      } else {
        return Key.get(typeLiteral);
      }
    }
  }

  /**
   * Represents the returned value from a call to {@link CheckedProvider#get()}. This is the value
   * that will be scoped by Guice.
   */
  static class Result implements Serializable {
    private static final long serialVersionUID = 0L;

    private final Object value;
    private final Exception exception;

    private Result(Object value, Exception exception) {
      this.value = value;
      this.exception = exception;
    }

    public static Result forValue(Object value) {
      return new Result(value, null);
    }

    public static Result forException(Exception e) {
      return new Result(null, e);
    }

    public Object getOrThrow() throws Exception {
      if (exception != null) {
        throw exception;
      } else {
        return value;
      }
    }
  }

  /**
   * RuntimeException class to wrap exceptions from the checked provider. The regular guice provider
   * can throw it and the checked provider proxy extracts the underlying exception and rethrows it.
   */
  private static class ResultException extends RuntimeException {
    ResultException(Exception cause) {
      super(cause);
    }
  }
}
