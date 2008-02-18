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

package com.google.inject.assistedinject;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides a mechanism to combine user-specified paramters with
 * {@link Injector}-specified paramters when creating new objects.
 *
 * <p>To use a {@link FactoryProvider}:
 *
 * <p>Annotate your implementation class' constructor with the
 * {@literal @}{@link AssistedInject} and the user-specified parameters with
 * {@literal @}{@link Assisted}:
 * <pre><code>public class RealPayment implements Payment {
 *    {@literal @}AssistedInject
 *    public RealPayment(CreditService creditService, AuthService authService,
 *      {@literal @}Assisted Date startDate, {@literal @}Assisted Money amount) {
 *     ...
 *  }
 * }</code></pre>
 *
 * <p>Write an interface with a <i>create</i> method that accepts the user-specified
 * parameters in the same order as they appear in the implementation class' constructor:
 * <pre><code>public interface PaymentFactory {
 *    Payment create(Date startDate, Money amount);
 * }</code></pre>
 *
 * <p>You can name your create methods whatever you like, such as <i>create</i>,
 * or <i>createPayment</i> or <i>newPayment</i>. The concrete class must
 * be assignable to the return type of your create method. You can also provide
 * multiple factory methods, but there must be exactly one
 * {@literal @}{@link AssistedInject} constructor on the implementation class for each.
 *
 * <p>In your Guice {@link com.google.inject.Module module}, bind your factory
 * interface to an instance of {@link FactoryProvider} that was created with
 * the same factory interface and implementation type:
 * <pre><code>  bind(PaymentFactory.class).toProvider(
 *     FactoryProvider.newFactory(PaymentFactory.class, RealPayment.class));</code></pre>
 *
 * <p>Now you can {@literal @}{@code Inject} your factory interface into your
 * Guice-injected classes. When you invoke the create method on that factory, the
 * {@link FactoryProvider} will instantiate the implementation class using
 * parameters from the injector and the factory method.
 *
 * <pre><code>public class PaymentAction {
 *    {@literal @}Inject private PaymentFactory paymentFactory;
 *
 *    public void doPayment(Money amount) {
 *       Payment payment = paymentFactory.create(new Date(), amount);
 *       payment.apply();
 *    }
 * }</code></pre>
 * 
 * @param <F> The factory interface
 * @param <R> The concrete class to be created.
 * 
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class FactoryProvider<F, R> implements Provider<F> {

  private Injector injector;

  private final Class<F> factoryType;
  private final Class<R> implementationType;
  private final Map<Method, AssistedConstructor<?>> factoryMethodToConstructor;

  public static <X,Y> FactoryProvider<X,Y> newFactory(
      Class<X> factoryType, Class<Y> implementationType){
    return new FactoryProvider<X, Y>(factoryType,implementationType);
  }
  
  private FactoryProvider(Class<F> factoryType, Class<R> implementationType) {
    this.factoryType = factoryType;
    this.implementationType = implementationType;
    this.factoryMethodToConstructor = createMethodMapping();
    checkDeclaredExceptionsMatch();
  }
  
  @Inject
  @SuppressWarnings({"unchecked", "unused"})
  private void setInjectorAndCheckUnboundParametersAreInjectable(
      Injector injector) {
    this.injector = injector;
    for (AssistedConstructor<?> c : factoryMethodToConstructor.values()) {
      for (Parameter p : c.getAllParameters()) {
        if(!p.isProvidedByFactory() && !paramCanBeInjected(p, injector)) {
          // this is lame - we're not using the proper mechanism to add an
          // error to the injector. Throughout this class we throw exceptions
          // to add errors, which isn't really the best way in Guice
          throw new IllegalStateException(String.format(
              "Parameter of type '%s' is not injectable or annotated "
                + "with @Assisted for Constructor '%s'", p, c));
        }
      }
    }
  }
  
  private void checkDeclaredExceptionsMatch() {
    for (Map.Entry<Method, AssistedConstructor<?>> entry : factoryMethodToConstructor.entrySet()) {
      for (Class<?> constructorException : entry.getValue().getDeclaredExceptions()) {
        if (!isConstructorExceptionCompatibleWithFactoryExeception(
            constructorException, entry.getKey().getExceptionTypes())) {
          throw new IllegalStateException(String.format(
              "Constructor %s declares an exception, but no compatible exception is thrown " 
                  + "by the factory method %s", entry.getValue(), entry.getKey()));
        }
      }
    }
  }
  
  private boolean isConstructorExceptionCompatibleWithFactoryExeception(
      Class<?> constructorException, Class<?>[] factoryExceptions) {
    for (Class<?> factoryException : factoryExceptions) {
      if (factoryException.isAssignableFrom(constructorException)) {
        return true;
      }
    }
    return false;
  }
  
  @SuppressWarnings("unchecked")
  private boolean paramCanBeInjected(Parameter parameter, Injector injector) {
    return parameter.isBound(injector);
  }

  @SuppressWarnings({"unchecked"})
  private Map<Method, AssistedConstructor<?>> createMethodMapping() {
    
    List<AssistedConstructor<?>> constructors = new ArrayList<AssistedConstructor<?>>();
    
    for (Constructor c : implementationType.getDeclaredConstructors()) {
      if (c.getAnnotation(AssistedInject.class) != null) {
        constructors.add(new AssistedConstructor(c));
      }
    }
    
    if (constructors.size() != factoryType.getMethods().length) {
      throw new IllegalArgumentException(
          String.format(
              "Constructor mismatch: %s has %s @AssistedInject " +
              "constructors, factory %s has %s creation methods",
              implementationType.getSimpleName(),
              constructors.size(),
              factoryType.getSimpleName(),
              factoryType.getMethods().length));
    }
    
    Map<ParameterListKey, AssistedConstructor> paramsToConstructor
        = new HashMap<ParameterListKey, AssistedConstructor>();
    
    for (AssistedConstructor c : constructors) {
      if (paramsToConstructor.containsKey(c.getAssistedParameters())) {
        throw new RuntimeException("Duplicate constructor, " + c);
      }
      paramsToConstructor.put(c.getAssistedParameters(), c);
    }
    
    Map<Method, AssistedConstructor<?>> result = new HashMap<Method, AssistedConstructor<?>>();
    for (Method method : factoryType.getMethods()) {
      if (!method.getReturnType().isAssignableFrom(implementationType)) {
        throw new RuntimeException(String.format("Return type of method \"%s\""
            + " is not assignable from class \"%s\"", method,
            implementationType.getName()));
      }
      ParameterListKey methodParams = new ParameterListKey(method.getGenericParameterTypes());
      
      if (!paramsToConstructor.containsKey(methodParams)) {
        throw new IllegalArgumentException(String.format("%s has no " +
            "@AssistInject constructor that takes the @Assisted parameters %s " +
            "in that order. @AssistInject constructors are %s",
            implementationType, methodParams, paramsToConstructor.values()));
      }
      AssistedConstructor matchingConstructor = paramsToConstructor.remove(methodParams);
      
      result.put(method, matchingConstructor);
    }
    return result;
  }

  @SuppressWarnings({"unchecked"})
  public F get() {
    InvocationHandler invocationHandler = new InvocationHandler() {

      public Object invoke(Object proxy, Method method, Object[] creationArgs) throws Throwable {
        AssistedConstructor<?> constructor = factoryMethodToConstructor.get(method);

        Object[] constructorArgs = gatherArgsForConstructor(
            constructor, creationArgs);
        Object objectToReturn = constructor.newInstance(constructorArgs);
        injector.injectMembers(objectToReturn);
        return objectToReturn;
      }

      public Object[] gatherArgsForConstructor(
          AssistedConstructor<?> constructor,
          Object[] factoryArgs) {
        int numParams = constructor.getAllParameters().size();
        int argPosition = 0;
        Object[] result = new Object[numParams];
        
        for (int i = 0; i < numParams; i++) {
          Parameter parameter = constructor.getAllParameters().get(i);
          if (parameter.isProvidedByFactory()) {
            result[i] = factoryArgs[argPosition];
            argPosition++;
          } else {
            result[i] = parameter.getValue(injector);
          }
        }
        return result;
      }
    };

    return (F) Proxy.newProxyInstance(factoryType.getClassLoader(),
        new Class[] {factoryType}, invocationHandler);
  }
}
