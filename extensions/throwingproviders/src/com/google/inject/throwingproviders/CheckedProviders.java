package com.google.inject.throwingproviders;

import static com.google.inject.throwingproviders.ProviderChecker.checkInterface;

import com.google.common.base.Optional;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Static utility methods for creating and working with instances of {@link CheckedProvider}.
 *
 * @author eatnumber1@google.com (Russ Harmon)
 * @since 4.2
 */
public final class CheckedProviders {
  private abstract static class CheckedProviderInvocationHandler<T> implements InvocationHandler {
    private boolean isGetMethod(Method method) {
      // Since Java does not allow multiple methods with the same name and number & type of
      // arguments, this is all we need to check to see if it is an overriding method of
      // CheckedProvider#get().
      return method.getName().equals("get") && method.getParameterTypes().length == 0;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.getDeclaringClass() == Object.class) {
        return method.invoke(this, args);
      } else if (isGetMethod(method)) {
        return invokeGet(proxy, method);
      }
      throw new UnsupportedOperationException(
          String.format(
              "Unsupported method <%s> with args <%s> invoked on <%s>",
              method, Arrays.toString(args), proxy));
    }

    protected abstract T invokeGet(Object proxy, Method method) throws Throwable;

    @Override
    public abstract String toString();
  }

  private static final class ReturningHandler<T> extends CheckedProviderInvocationHandler<T> {
    private final T returned;

    ReturningHandler(T returned) {
      this.returned = returned;
    }

    @Override
    protected T invokeGet(Object proxy, Method method) throws Throwable {
      return returned;
    }

    @Override
    public String toString() {
      return String.format("generated CheckedProvider returning <%s>", returned);
    }
  }

  private static final class ThrowingHandler extends CheckedProviderInvocationHandler<Object> {
    private final Constructor<? extends Throwable> throwableCtor;
    private final String typeName;

    private ThrowingHandler(Constructor<? extends Throwable> throwableCtor, String typeName) {
      this.throwableCtor = throwableCtor;
      this.typeName = typeName;

      this.throwableCtor.setAccessible(true);
    }

    static ThrowingHandler forClass(Class<? extends Throwable> throwable) {
      try {
        return new ThrowingHandler(throwable.getDeclaredConstructor(), throwable.getName());
      } catch (NoSuchMethodException e) {
        // This should have been caught by checkThrowable
        throw new AssertionError(
            String.format(
                "Throwable <%s> does not have a no-argument constructor", throwable.getName()));
      }
    }

    @Override
    protected Object invokeGet(Object proxy, Method method) throws Throwable {
      throw this.throwableCtor.newInstance();
    }

    @Override
    public String toString() {
      return String.format("generated CheckedProvider throwing <%s>", this.typeName);
    }
  }

  private CheckedProviders() {}

  private static <T, P extends CheckedProvider<? super T>> P generateProvider(
      Class<P> providerType, Optional<T> value, InvocationHandler handler) {
    checkInterface(providerType, getClassOptional(value));
    Object proxy =
        Proxy.newProxyInstance(
            providerType.getClassLoader(), new Class<?>[] {providerType}, handler);
    @SuppressWarnings("unchecked") // guaranteed by the newProxyInstance API
    P proxyP = (P) proxy;
    return proxyP;
  }

  private static <T, P extends CheckedProvider<? super T>> P generateProvider(
      TypeLiteral<P> providerType, Optional<T> value, InvocationHandler handler) {
    // TODO(user): Understand why TypeLiteral#getRawType returns a Class<? super T> rather
    // than a Class<T> and remove this unsafe cast.
    Class<P> providerRaw = (Class) providerType.getRawType();
    return generateProvider(providerRaw, value, handler);
  }

  private static Optional<Class<?>> getClassOptional(Optional<?> value) {
    if (!value.isPresent()) {
      return Optional.absent();
    }
    return Optional.<Class<?>>of(value.get().getClass());
  }

  /**
   * Returns a {@link CheckedProvider} which always provides {@code instance}.
   *
   * <p>The provider type passed as {@code providerType} must be an interface. Calls to methods
   * other than {@link CheckedProvider#get} will throw {@link UnsupportedOperationException}.
   *
   * @param providerType the type of the {@link CheckedProvider} to return
   * @param instance the instance that should always be provided
   */
  public static <T, P extends CheckedProvider<? super T>> P of(
      TypeLiteral<P> providerType, @Nullable T instance) {
    return generateProvider(
        providerType, Optional.fromNullable(instance), new ReturningHandler<T>(instance));
  }

  /**
   * Returns a {@link CheckedProvider} which always provides {@code instance}.
   *
   * @param providerType the type of the {@link CheckedProvider} to return
   * @param instance the instance that should always be provided
   * @see #of(TypeLiteral, T)
   */
  public static <T, P extends CheckedProvider<? super T>> P of(
      Class<P> providerType, @Nullable T instance) {
    return of(TypeLiteral.get(providerType), instance);
  }

  /**
   * Returns a {@link CheckedProvider} which always throws exceptions.
   *
   * <p>This method uses the nullary (no argument) constructor of {@code throwable} to create a new
   * instance of the given {@link Throwable} on each method invocation which is then thrown
   * immediately.
   *
   * <p>See {@link #of(TypeLiteral, T)} for more information.
   *
   * @param providerType the type of the {@link CheckedProvider} to return
   * @param throwable the type of the {@link Throwable} to throw
   * @see #of(TypeLiteral, T)
   */
  public static <T, P extends CheckedProvider<? super T>> P throwing(
      TypeLiteral<P> providerType, Class<? extends Throwable> throwable) {
    // TODO(user): Understand why TypeLiteral#getRawType returns a Class<? super T> rather
    // than a Class<T> and remove this unsafe cast.
    Class<P> providerRaw = (Class) providerType.getRawType();
    checkThrowable(providerRaw, throwable);
    return generateProvider(
        providerType, Optional.<T>absent(), ThrowingHandler.forClass(throwable));
  }

  /**
   * Returns a {@link CheckedProvider} which always throws exceptions.
   *
   * @param providerType the type of the {@link CheckedProvider} to return
   * @param throwable the type of the {@link Throwable} to throw
   * @see #throwing(TypeLiteral, Class)
   */
  public static <T, P extends CheckedProvider<? super T>> P throwing(
      Class<P> providerType, Class<? extends Throwable> throwable) {
    return throwing(TypeLiteral.get(providerType), throwable);
  }

  private static boolean isCheckedException(Class<? extends Throwable> thrownType) {
    return Exception.class.isAssignableFrom(thrownType)
        && !RuntimeException.class.isAssignableFrom(thrownType);
  }

  private static void checkThrowable(
      Class<? extends CheckedProvider<?>> providerType, Class<? extends Throwable> thrownType) {
    try {
      thrownType.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format(
              "Thrown exception <%s> must have a no-argument constructor", thrownType.getName()),
          e);
    }

    if (!isCheckedException(thrownType)) {
      return;
    }

    Method getMethod;
    try {
      getMethod = providerType.getMethod("get");
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException(
          String.format("Provider class <%s> must have a get() method", providerType.getName()), e);
    }

    @SuppressWarnings("unchecked") // guaranteed by getExceptionTypes
    List<Class<? extends Throwable>> exceptionTypes =
        Arrays.asList((Class<? extends Throwable>[]) getMethod.getExceptionTypes());

    if (exceptionTypes.size() == 0) {
      return;
    }

    // Check if the thrown exception is declared to be thrown in the method signature.
    for (Class<? extends Throwable> exceptionType : exceptionTypes) {
      if (exceptionType.isAssignableFrom(thrownType)) {
        return;
      }
    }

    throw new IllegalArgumentException(
        String.format(
            "Thrown exception <%s> is not declared to be thrown by <%s>",
            thrownType.getName(), getMethod));
  }
}
