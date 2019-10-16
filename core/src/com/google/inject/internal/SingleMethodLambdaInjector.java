package com.google.inject.internal;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Consumer;

import com.google.common.base.Preconditions;
import com.google.inject.spi.InjectionPoint;

/**
 * Invokes an injectable method with a lambda.
 */
final class SingleMethodLambdaInjector implements SingleMemberInjector {
  private final InjectionPoint injectionPoint;
  private final SingleParameterInjector<?>[] parameterInjectors;
  private final Consumer<Object[]> lambda;

  private SingleMethodLambdaInjector(
      InjectionPoint injectionPoint,
      Consumer<Object[]> lambda,
      SingleParameterInjector<?>[] parameterInjectors) {

    this.injectionPoint = injectionPoint;
    this.lambda = lambda;
    this.parameterInjectors = parameterInjectors;
  }

  @Override
  public void inject(InternalContext context, Object instance)
      throws InternalProvisionException {

    Object[] parameters = SingleParameterInjector.getAll(context, parameterInjectors);
    lambda.accept(parameters);
  }

  @Override
  public InjectionPoint getInjectionPoint() {
    return injectionPoint;
  }

  private static Consumer<Object[]> createLambda(InjectionPoint injectionPoint) throws Throwable {
    return createLambdaForMethod((Method) injectionPoint.getMember());
  }

  private static Consumer<Object[]> createLambdaForMethod(Method method) throws Throwable {
    MethodHandles.Lookup lookup = createLookupForMethod(method);
    MethodHandle handle = resolveMethodHandle(method, lookup);
    return createLambdaForMethodHandle(handle, lookup);
  }

  @SuppressWarnings("unchecked")
  private static Consumer<Object[]> createLambdaForMethodHandle(
      MethodHandle handle, MethodHandles.Lookup lookup) throws Throwable {

    MethodType lambdaType = MethodType.methodType(Consumer.class);
    MethodType singleAbstractMethodType = MethodType.methodType(void.class, Object[].class);
    MethodType methodType = handle.type().unwrap();
    CallSite callSite = LambdaMetafactory.metafactory(
        /* lookup */ lookup,
        /* invokedName */ "call",
        /* invokedType */ lambdaType,
        /* samType */ singleAbstractMethodType,
        /* implMethod */ handle,
        /* instantiatedMethodType*/ methodType
    );
    return (Consumer<Object[]>) callSite.getTarget().invoke();
  }


  private static MethodHandles.Lookup createLookupForMethod(Method method) {
    return isPubliclyAccessible(method)
        ? MethodHandles.publicLookup()
        : MethodHandles.lookup();
  }

  private static MethodHandle resolveMethodHandle(
      Method method, MethodHandles.Lookup lookup) {

    try {
      return lookup.unreflect(method);
    } catch (IllegalAccessException illegalAccess) {
      throw new AssertionError(illegalAccess);
    }
  }

  private static boolean isPubliclyAccessible(Method method) {
    return Modifier.isPublic(method.getModifiers())
        && Modifier.isPublic(method.getDeclaringClass().getModifiers());
  }

  static SingleMethodLambdaInjector forInjectionPoint(
      InjectorImpl injector, Errors errors, InjectionPoint injectionPoint)
      throws InternalProvisionException, ErrorsException {

    Preconditions.checkNotNull(injectionPoint);
    try {
      Consumer<Object[]> lambda = createLambda(injectionPoint);
      SingleParameterInjector<?>[] parameterInjectors = injector.getParametersInjectors(
          injectionPoint.getDependencies(), errors);

      return new SingleMethodLambdaInjector(injectionPoint, lambda, parameterInjectors);
    } catch (ErrorsException errorsException) {
      throw errorsException;
    } catch (Throwable lambdaCreationFailure) {
      throw InternalProvisionException
          .cannotCreateLambda(lambdaCreationFailure)
          .addSource(injectionPoint);
    }
  }
}
