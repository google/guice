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
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.util.Types;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Set;

/**
 * <p>Builds a binding for a {@link ThrowingProvider}.
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
 *   {@literal @}ThrowingProvides(RemoteProvider.class)
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
   * Installs {@literal @}{@link ThrowingProvides} methods.
   * 
   * @since 3.0
   */
  public static void install(Module module, Binder binder) {
    binder.install(ThrowingProviderMethodsModule.forModule(module));
  }

  public <P extends ThrowingProvider> SecondaryBinder<P> 
      bind(final Class<P> interfaceType, final Type valueType) {
    return new SecondaryBinder<P>(interfaceType, valueType);
  }

  public class SecondaryBinder<P extends ThrowingProvider> {
    private final Class<P> interfaceType;
    private final Type valueType;
    private Class<? extends Annotation> annotationType;
    private Annotation annotation;
    private final Class<? extends Exception> exceptionType;
    private final boolean valid;

    public SecondaryBinder(Class<P> interfaceType, Type valueType) {
      this.interfaceType = checkNotNull(interfaceType, "interfaceType");
      this.valueType = checkNotNull(valueType, "valueType");
      if(checkInterface()) {
        this.exceptionType = getExceptionType(interfaceType);
        valid = true;
      } else {
        valid = false;
        this.exceptionType = null;
      }
    }
    
    Class<? extends Exception> getExceptionType() {
      return exceptionType;
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
    
    ScopedBindingBuilder toProviderMethod(ThrowingProviderMethod<?> target) {
      Key<ThrowingProviderMethod> targetKey =
        Key.get(ThrowingProviderMethod.class, UniqueAnnotations.create());
      binder.bind(targetKey).toInstance(target);
      
      return toInternal(targetKey);
    }

    public ScopedBindingBuilder to(Key<? extends P> targetKey) {
      checkNotNull(targetKey, "targetKey");
      return toInternal(targetKey);
    }
    
    private ScopedBindingBuilder toInternal(final Key<? extends ThrowingProvider> targetKey) {
      final Key<Result> resultKey = Key.get(Result.class, UniqueAnnotations.create());
      final Key<P> key = createKey();      
      final Provider<Result> resultProvider = binder.getProvider(resultKey);
      final Provider<? extends ThrowingProvider> targetProvider = binder.getProvider(targetKey);

      // don't bother binding the proxy type if this is in an invalid state.
      if(valid) {
        binder.bind(key).toProvider(new ProviderWithDependencies<P>() {
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
            if (exceptionType.isInstance(e)) {
              return Result.forException(e);
            } else if (e instanceof RuntimeException) {
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
    @SuppressWarnings({"unchecked"})
    private Class<? extends Exception> getExceptionType(Class<P> interfaceType) {
      ParameterizedType genericUnreliableProvider
          = (ParameterizedType) interfaceType.getGenericInterfaces()[0];
      return (Class<? extends Exception>) genericUnreliableProvider.getActualTypeArguments()[1];
    }

    private boolean checkInterface() {
      if(!checkArgument(interfaceType.isInterface(),
         "%s must be an interface", interfaceType.getName())) {
        return false;
      }
      if(!checkArgument(interfaceType.getGenericInterfaces().length == 1,
          "%s must extend ThrowingProvider (and only ThrowingProvider)",
          interfaceType)) {
        return false;
      }
      if(!checkArgument(interfaceType.getInterfaces()[0] == ThrowingProvider.class,
          "%s must extend ThrowingProvider (and only ThrowingProvider)",
          interfaceType)) {
        return false;
      }

      // Ensure that T is parameterized and unconstrained.
      ParameterizedType genericThrowingProvider
          = (ParameterizedType) interfaceType.getGenericInterfaces()[0];
      if (interfaceType.getTypeParameters().length == 1) {
        String returnTypeName = interfaceType.getTypeParameters()[0].getName();
        Type returnType = genericThrowingProvider.getActualTypeArguments()[0];
        if(!checkArgument(returnType instanceof TypeVariable,
            "%s does not properly extend ThrowingProvider, the first type parameter of ThrowingProvider (%s) is not a generic type",
            interfaceType, returnType)) {
          return false;
        }
        if(!checkArgument(returnTypeName.equals(((TypeVariable) returnType).getName()),
            "The generic type (%s) of %s does not match the generic type of ThrowingProvider (%s)",
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

      Type exceptionType = genericThrowingProvider.getActualTypeArguments()[1];
      if(!checkArgument(exceptionType instanceof Class,
          "%s has the wrong Exception generic type (%s) when extending ThrowingProvider",
          interfaceType, exceptionType)) {
        return false;
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
   * ThrowingProvider#get()}. This is the value that will be scoped by Guice.
   */
  private static class Result {
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
}
