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
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.Preconditions.checkNotNull;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.util.Types;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * <p>Builds a binding for a {@link ThrowingProvider} using a fluent API:
 * <pre><code>ThrowingProviderBinder.create(binder())
 *    .bind(RemoteProvider.class, Customer.class)
 *    .to(RemoteCustomerProvider.class)
 *    .in(RequestScope.class);
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
    return new ThrowingProviderBinder(binder);
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
    private final Class<?> exceptionType;

    public SecondaryBinder(Class<P> interfaceType, Type valueType) {
      this.interfaceType = checkNotNull(interfaceType, "interfaceType");
      this.valueType = checkNotNull(valueType, "valueType");
      checkInterface();
      this.exceptionType = getExceptionType(interfaceType);
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

    public ScopedBindingBuilder to(final Key<? extends P> targetKey) {
      checkNotNull(targetKey, "targetKey");
      final Key<Result> resultKey = Key.get(Result.class, UniqueAnnotations.create());
      final Key<P> key = createKey();

      binder.bind(key).toProvider(new Provider<P>() {
        private P instance;

        @Inject void initialize(final Injector injector) {
          instance = interfaceType.cast(Proxy.newProxyInstance(
              interfaceType.getClassLoader(), new Class<?>[] { interfaceType },
              new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                  return injector.getInstance(resultKey).getOrThrow();
                }
              }));
          }

          public P get() {
            return instance;
          }
        });

      return binder.bind(resultKey).toProvider(new Provider<Result>() {
        private Injector injector;

        @Inject void initialize(Injector injector) {
          this.injector = injector;
        }

        public Result get() {
          try {
            return Result.forValue(injector.getInstance(targetKey).get());
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
      });
    }

    /**
     * Returns the exception type declared to be thrown by the get method of
     * {@code interfaceType}.
     */
    @SuppressWarnings({"unchecked"})
    private <P extends ThrowingProvider> Class<?> getExceptionType(Class<P> interfaceType) {
      ParameterizedType genericUnreliableProvider
          = (ParameterizedType) interfaceType.getGenericInterfaces()[0];
      return (Class<? extends Exception>) genericUnreliableProvider.getActualTypeArguments()[1];
    }

    private void checkInterface() {
      String errorMessage = "%s is not a compliant interface "
          + "- see the Javadoc for ThrowingProvider";

      checkArgument(interfaceType.isInterface(), errorMessage, interfaceType.getName());
      checkArgument(interfaceType.getGenericInterfaces().length == 1, errorMessage,
          interfaceType.getName());
      checkArgument(interfaceType.getInterfaces()[0] == ThrowingProvider.class,
          errorMessage, interfaceType.getName());

      // Ensure that T is parameterized and unconstrained.
      ParameterizedType genericThrowingProvider
          = (ParameterizedType) interfaceType.getGenericInterfaces()[0];
      if (interfaceType.getTypeParameters().length == 1) {
        checkArgument(interfaceType.getTypeParameters().length == 1, errorMessage,
            interfaceType.getName());
        String returnTypeName = interfaceType.getTypeParameters()[0].getName();
        Type returnType = genericThrowingProvider.getActualTypeArguments()[0];
        checkArgument(returnType instanceof TypeVariable, errorMessage, interfaceType.getName());
        checkArgument(returnTypeName.equals(((TypeVariable) returnType).getName()),
            errorMessage, interfaceType.getName());
      } else {
        checkArgument(interfaceType.getTypeParameters().length == 0,
            errorMessage, interfaceType.getName());
        checkArgument(genericThrowingProvider.getActualTypeArguments()[0].equals(valueType),
            errorMessage, interfaceType.getName());
      }

      Type exceptionType = genericThrowingProvider.getActualTypeArguments()[1];
      checkArgument(exceptionType instanceof Class, errorMessage, interfaceType.getName());
      
      if (interfaceType.getDeclaredMethods().length == 1) {
        Method method = interfaceType.getDeclaredMethods()[0];
        checkArgument(method.getName().equals("get"), errorMessage, interfaceType.getName());
        checkArgument(method.getParameterTypes().length == 0,
            errorMessage, interfaceType.getName());
      } else {
        checkArgument(interfaceType.getDeclaredMethods().length == 0,
            errorMessage, interfaceType.getName());
      }
    }

    private void checkArgument(boolean condition,
        String messageFormat, Object... args) {
      if (!condition) {
        throw new IllegalArgumentException(String.format(messageFormat, args));
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
