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

import com.google.inject.*;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.Objects;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>Builds a binding for an {@link ThrowingProvider} using a fluent API:
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
      this.interfaceType = Objects.nonNull(interfaceType, "interfaceType");
      this.valueType = Objects.nonNull(valueType, "valueType");
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
      Key<P> targetKey = Key.get(interfaceType, uniqueAnnotation());
      binder.bind(targetKey).toInstance(target);
      return to(targetKey);
    }

    public ScopedBindingBuilder to(Class<? extends P> targetType) {
      return to(Key.get(targetType));
    }

    public ScopedBindingBuilder to(final Key<? extends P> targetKey) {
      Objects.nonNull(targetKey, "targetKey");
      final Key<Result> resultKey = Key.get(Result.class, uniqueAnnotation());
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
        typeLiteral = (TypeLiteral<P>) TypeLiteral.get(new ParameterizedType() {

          public Type[] getActualTypeArguments() {
            return new Type[]{valueType};
          }

          public Type getRawType() {
            return interfaceType;
          }

          public Type getOwnerType() {
            throw new UnsupportedOperationException();
          }
        });
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
   * Returns an annotation instance that is not equal to any other annotation
   * instances, for use in creating distinct {@link Key}s.
   */
  private static Annotation uniqueAnnotation() {
    final int value = nextUniqueValue.getAndIncrement();
    return new Internal() {
      public int value() {
        return value;
      }

      public Class<? extends Annotation> annotationType() {
        return Internal.class;
      }

      @Override public String toString() {
        return "@" + Internal.class.getName() + "(value=" + value + ")";
      }

      @Override public boolean equals(Object o) {
        return o instanceof Internal
            && ((Internal) o).value() == value();
      }

      @Override public int hashCode() {
        return 127 * "value".hashCode() ^ value;
      }
    };
  }
  @Retention(RUNTIME) @BindingAnnotation
  private @interface Internal {
    int value();
  }
  private static final AtomicInteger nextUniqueValue = new AtomicInteger(1);

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
