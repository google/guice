package com.google.inject.throwingproviders;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.inject.internal.Errors;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.List;

/** Helper methods to verify the correctness of CheckedProvider interfaces. */
final class ProviderChecker {

  private ProviderChecker() {}

  static <P extends CheckedProvider<?>> void checkInterface(
      Class<P> interfaceType, Optional<? extends Type> valueType) {
    checkArgument(interfaceType.isInterface(), "%s must be an interface", interfaceType.getName());
    checkArgument(
        interfaceType.getGenericInterfaces().length == 1,
        "%s must extend CheckedProvider (and only CheckedProvider)",
        interfaceType);

    boolean tpMode = interfaceType.getInterfaces()[0] == ThrowingProvider.class;
    if (!tpMode) {
      checkArgument(
          interfaceType.getInterfaces()[0] == CheckedProvider.class,
          "%s must extend CheckedProvider (and only CheckedProvider)",
          interfaceType);
    }

    // Ensure that T is parameterized and unconstrained.
    ParameterizedType genericThrowingProvider =
        (ParameterizedType) interfaceType.getGenericInterfaces()[0];
    if (interfaceType.getTypeParameters().length == 1) {
      String returnTypeName = interfaceType.getTypeParameters()[0].getName();
      Type returnType = genericThrowingProvider.getActualTypeArguments()[0];
      checkArgument(
          returnType instanceof TypeVariable,
          "%s does not properly extend CheckedProvider, the first type parameter of CheckedProvider"
              + " (%s) is not a generic type",
          interfaceType,
          returnType);
      checkArgument(
          returnTypeName.equals(((TypeVariable) returnType).getName()),
          "The generic type (%s) of %s does not match the generic type of CheckedProvider (%s)",
          returnTypeName,
          interfaceType,
          ((TypeVariable) returnType).getName());
    } else {
      checkArgument(
          interfaceType.getTypeParameters().length == 0,
          "%s has more than one generic type parameter: %s",
          interfaceType,
          Arrays.asList(interfaceType.getTypeParameters()));
      if (valueType.isPresent()) {
        checkArgument(
            genericThrowingProvider.getActualTypeArguments()[0].equals(valueType.get()),
            "%s expects the value type to be %s, but it was %s",
            interfaceType,
            genericThrowingProvider.getActualTypeArguments()[0],
            valueType.get());
      }
    }

    if (tpMode) { // only validate exception in ThrowingProvider mode.
      Type exceptionType = genericThrowingProvider.getActualTypeArguments()[1];
      checkArgument(
          exceptionType instanceof Class,
          "%s has the wrong Exception generic type (%s) when extending CheckedProvider",
          interfaceType,
          exceptionType);
    }

    // Skip synthetic/bridge methods because java8 generates
    // a default method on the interface w/ the superinterface type that
    // just delegates directly to the overridden method.
    List<Method> declaredMethods =
        Arrays.stream(interfaceType.getDeclaredMethods())
            .filter(NotSyntheticOrBridgePredicate.INSTANCE)
            .collect(toImmutableList());
    if (declaredMethods.size() == 1) {
      Method method = declaredMethods.get(0);
      checkArgument(
          method.getName().equals("get"),
          "%s may not declare any new methods, but declared %s",
          interfaceType,
          method);
      checkArgument(
          method.getParameterTypes().length == 0,
          "%s may not declare any new methods, but declared %s",
          interfaceType,
          method.toGenericString());
    } else {
      checkArgument(
          declaredMethods.isEmpty(),
          "%s may not declare any new methods, but declared %s",
          interfaceType,
          Arrays.asList(interfaceType.getDeclaredMethods()));
    }
  }

  private static void checkArgument(boolean condition, String messageFormat, Object... args) {
    if (!condition) {
      throw new IllegalArgumentException(Errors.format(messageFormat, args));
    }
  }

  private static class NotSyntheticOrBridgePredicate implements Predicate<Method> {
    static final NotSyntheticOrBridgePredicate INSTANCE = new NotSyntheticOrBridgePredicate();

    @Override
    public boolean apply(Method input) {
      return !input.isBridge() && !input.isSynthetic();
    }
  }
}
