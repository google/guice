/**
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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Lists;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.util.Types;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * <p>Builds a binding for a {@link CheckedProvider}.
 * 
 * <p>You can use a fluent API and custom providers:
 * <pre><code>ThrowingProviderBinder.create(binder())
 *    .bind(RemoteProvider.class, Customer.class)
 *    .to(RemoteCustomerProvider.class)
 *    .in(RequestScope.class);
 * </code></pre>
 * or, you can use throwing provider methods:
 * <pre><code>class MyModule extends AbstractModule {
 *   configure() {
 *     ThrowingProviderBinder.install(this, binder());
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
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ThrowingProviderBinder {

  private final Binder binder;

  private ThrowingProviderBinder(Binder binder) {
    this.binder = binder;
  }

  public static ThrowingProviderBinder create(Binder binder) {
    return new ThrowingProviderBinder(binder.skipSources(
        ThrowingProviderBinder.class,
        ThrowingProviderBinder.SecondaryBinder.class));
  }
  
  /**
   * Returns a module that installs {@literal @}{@link CheckedProvides} methods.
   * 
   * @since 3.0
   */
  public static Module forModule(Module module) {
    return CheckedProviderMethodsModule.forModule(module);
  }

  public <P extends CheckedProvider> SecondaryBinder<P> 
      bind(final Class<P> interfaceType, final Type valueType) {
    return new SecondaryBinder<P>(interfaceType, valueType);
  }

  public class SecondaryBinder<P extends CheckedProvider> {
    private final Class<P> interfaceType;
    private final Type valueType;
    private final List<Class<? extends Throwable>> exceptionTypes;
    private final boolean valid;

    private Class<? extends Annotation> annotationType;
    private Annotation annotation;
    private Key<P> interfaceKey;

    public SecondaryBinder(Class<P> interfaceType, Type valueType) {
      this.interfaceType = checkNotNull(interfaceType, "interfaceType");
      this.valueType = checkNotNull(valueType, "valueType");
      if(checkInterface()) {
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

    public SecondaryBinder<P> annotatedWith(Class<? extends Annotation> annotationType) {
      if (!(this.annotationType == null && this.annotation == null)) {
        throw new IllegalStateException();
      }
      this.annotationType = annotationType;
      return this;
    }

    public SecondaryBinder<P> annotatedWith(Annotation annotation) {
      if (!(this.annotationType == null && this.annotation == null)) {
        throw new IllegalStateException();
      }
      this.annotation = annotation;
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
    
    ScopedBindingBuilder toProviderMethod(CheckedProviderMethod<?> target) {
      Key<CheckedProviderMethod> targetKey =
        Key.get(CheckedProviderMethod.class, UniqueAnnotations.create());
      binder.bind(targetKey).toInstance(target);
      
      return toInternal(targetKey);
    }

    public ScopedBindingBuilder to(Key<? extends P> targetKey) {
      checkNotNull(targetKey, "targetKey");
      return toInternal(targetKey);
    }
    
    private ScopedBindingBuilder toInternal(final Key<? extends CheckedProvider> targetKey) {
      final Key<Result> resultKey = Key.get(Result.class, UniqueAnnotations.create());
      final Provider<Result> resultProvider = binder.getProvider(resultKey);
      final Provider<? extends CheckedProvider> targetProvider = binder.getProvider(targetKey);
      interfaceKey = createKey();

      // don't bother binding the proxy type if this is in an invalid state.
      if(valid) {
        binder.bind(interfaceKey).toProvider(new ProviderWithDependencies<P>() {
          private final P instance = interfaceType.cast(Proxy.newProxyInstance(
              interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
              new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                  return resultProvider.get().getOrThrow();
                }
              }));
            
            public P get() {
              return instance;
            }
            
            public Set<Dependency<?>> getDependencies() {
              return ImmutableSet.<Dependency<?>>of(Dependency.get(resultKey));
            }
          });
      }

      return binder.bind(resultKey).toProvider(new ProviderWithDependencies<Result>() {
        public Result get() {
          try {
            return Result.forValue(targetProvider.get().get());
          } catch (Exception e) {
            for(Class<? extends Throwable> exceptionType : exceptionTypes) {
              if (exceptionType.isInstance(e)) {
                return Result.forException(e);
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
        
        public Set<Dependency<?>> getDependencies() {
          return ImmutableSet.<Dependency<?>>of(Dependency.get(targetKey));
        }
      });
    }

    /**
     * Returns the exception type declared to be thrown by the get method of
     * {@code interfaceType}.
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
      if(!checkArgument(interfaceType.isInterface(),
         "%s must be an interface", interfaceType.getName())) {
        return false;
      }
      if(!checkArgument(interfaceType.getGenericInterfaces().length == 1,
          "%s must extend CheckedProvider (and only CheckedProvider)",
          interfaceType)) {
        return false;
      }
      
      boolean tpMode = interfaceType.getInterfaces()[0] == ThrowingProvider.class;      
      if(!tpMode) {
        if(!checkArgument(interfaceType.getInterfaces()[0] == CheckedProvider.class,
            "%s must extend CheckedProvider (and only CheckedProvider)",
            interfaceType)) {
          return false;
        }
      }

      // Ensure that T is parameterized and unconstrained.
      ParameterizedType genericThrowingProvider
          = (ParameterizedType) interfaceType.getGenericInterfaces()[0];
      if (interfaceType.getTypeParameters().length == 1) {
        String returnTypeName = interfaceType.getTypeParameters()[0].getName();
        Type returnType = genericThrowingProvider.getActualTypeArguments()[0];
        if(!checkArgument(returnType instanceof TypeVariable,
            "%s does not properly extend CheckedProvider, the first type parameter of CheckedProvider (%s) is not a generic type",
            interfaceType, returnType)) {
          return false;
        }
        if(!checkArgument(returnTypeName.equals(((TypeVariable) returnType).getName()),
            "The generic type (%s) of %s does not match the generic type of CheckedProvider (%s)",
            returnTypeName, interfaceType, ((TypeVariable)returnType).getName())) {
          return false;
        }
      } else {
        if(!checkArgument(interfaceType.getTypeParameters().length == 0,
            "%s has more than one generic type parameter: %s",
            interfaceType, Arrays.asList(interfaceType.getTypeParameters()))) {
          return false;
        }
        if(!checkArgument(genericThrowingProvider.getActualTypeArguments()[0].equals(valueType),
            "%s expects the value type to be %s, but it was %s",
            interfaceType, genericThrowingProvider.getActualTypeArguments()[0], valueType)) {
          return false;
        }
      }

      if(tpMode) { // only validate exception in ThrowingProvider mode.
        Type exceptionType = genericThrowingProvider.getActualTypeArguments()[1];
        if(!checkArgument(exceptionType instanceof Class,
            "%s has the wrong Exception generic type (%s) when extending CheckedProvider",
            interfaceType, exceptionType)) {
          return false;
        }
      }
      
      if (interfaceType.getDeclaredMethods().length == 1) {
        Method method = interfaceType.getDeclaredMethods()[0];
        if(!checkArgument(method.getName().equals("get"),
            "%s may not declare any new methods, but declared %s",
            interfaceType, method)) {
          return false;
        }
        if(!checkArgument(method.getParameterTypes().length == 0,
            "%s may not declare any new methods, but declared %s",
            interfaceType, method.toGenericString())) {
          return false;
        }
      } else {
        if(!checkArgument(interfaceType.getDeclaredMethods().length == 0,
            "%s may not declare any new methods, but declared %s",
            interfaceType, Arrays.asList(interfaceType.getDeclaredMethods()))) {
          return false;
        }
      }
      
      return true;
    }

    private boolean checkArgument(boolean condition,
        String messageFormat, Object... args) {
      if (!condition) {
        binder.addError(messageFormat, args);
        return false;
      } else {
        return true;
      }
    }

    @SuppressWarnings({"unchecked"})
    private Key<P> createKey() {
      TypeLiteral<P> typeLiteral;
      if (interfaceType.getTypeParameters().length == 1) {
        ParameterizedType type = Types.newParameterizedTypeWithOwner(
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
   * Represents the returned value from a call to {@link
   * CheckedProvider#get()}. This is the value that will be scoped by Guice.
   */
  static class Result implements Serializable {
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
    
    private static final long serialVersionUID = 0L;
  }
}
