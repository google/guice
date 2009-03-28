/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.spi;

import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ImmutableMap;
import com.google.inject.matcher.Matcher;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Represents an injectable type. A type is injectable if:
 *
 * <ol type="a">
 *   <li>Guice can inject its constructor, or</li>
 *   <li>Guice can inject the methods and fields of a pre-existing instance of the type</li>
 * </ol>
 *
 * <p>Note: Despite generic type erasure, Guice keeps track of full types, so it can and does treat
 * {@code Foo<String>} and {@code Foo<Integer>} as distinct injectable types.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class InjectableType<T> {
  private final InjectionPoint injectableConstructor;
  private final TypeLiteral<T> type;
  private final Set<InjectionPoint> injectableMembers;
  private final ImmutableMap<Method, List<MethodInterceptor>> methodInterceptors;

  public InjectableType(InjectionPoint injectableConstructor, TypeLiteral<T> type,
      Set<InjectionPoint> injectableMembers,
      Map<Method, List<MethodInterceptor>> methodInterceptors) {
    this.injectableConstructor = injectableConstructor;
    this.type = type;
    this.injectableMembers = injectableMembers;
    this.methodInterceptors = ImmutableMap.copyOf(methodInterceptors);
  }

  /**
   * Returns the type literal.
   */
  public TypeLiteral<T> getType() {
    return type;
  }

  /**
   * Returns the injection point representing the contructor or {@code null} if no injectable
   * constructor is present
   */
  public InjectionPoint getInjectableConstructor() {
    return injectableConstructor;
  }

  /**
   * Returns the instance methods and fields of {@code T} that can be injected.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order.
   *     All fields are returned and then all methods. Within the fields, supertype fields are
   *     returned before subtype fields. Similarly, supertype methods are returned before subtype
   *     methods.
   */
  public Set<InjectionPoint> getInjectableMembers() {
    return injectableMembers;
  }

  /*if[AOP]*/
  /**
   * Returns the interceptors applied to each method, in the order that they will be applied.
   * These only apply when Guice instantiates {@code I}.
   *
   * @return a possibly empty map
   */
  public Map<Method, List<MethodInterceptor>> getMethodInterceptors() {
    return methodInterceptors;
  }
  /*end[AOP]*/

  @Override public String toString() {
    return type.toString();
  }

  /**
   * Listens for Guice to encounter injectable types. If a given type has its constructor injected
   * in one situation but only its methods and fields injected in another, Guice will notify
   * this listener once.
   *
   * <p>Useful for extra type checking, {@linkplain Encounter#register(InjectionListener)
   * registering injection listeners}, and {@linkplain Encounter#bindInterceptor(
   * com.google.inject.matcher.Matcher, org.aopalliance.intercept.MethodInterceptor[])
   * binding method interceptors}.
   */
  public interface Listener {

    /**
     * Invoked when Guice encounters a new type eligible for constructor or members injection.
     * Called during injector creation (or afterwords if Guice encounters a type at run time and
     * creates a JIT binding).
     *
     * @param injectableType encountered by Guice
     * @param encounter context of this encounter, enables reporting errors, registering injection
     *  listeners and binding method interceptors for injectableType
     *
     * @param <I> the injectable type
     */
    <I> void hear(InjectableType<I> injectableType, Encounter<I> encounter);

  }

  /**
   * Context of the injectable type encounter. Enables reporting errors, registering injection
   * listeners and binding method interceptors for injectable type {@code I}.
   *
   * @param <I> the injectable type encountered
   */
  public interface Encounter<I> {

    /**
     * Records an error message for type {@code I} which will be presented to the user at a later
     * time. Unlike throwing an exception, this enable us to continue configuring the Injector and
     * discover more errors. Uses {@link String#format(String, Object[])} to insert the arguments
     * into the message.
     */
    void addError(String message, Object... arguments);

    /**
     * Records an exception for type {@code I}, the full details of which will be logged, and the
     * message of which will be presented to the user at a later time. If your
     * InjectableTypeListener calls something that you worry may fail, you should catch the
     * exception and pass it to this method.
     */
    void addError(Throwable t);

    /**
     * Records an error message to be presented to the user at a later time.
     */
    void addError(Message message);

    /**
     * Returns the provider used to obtain instances for the given injection key. The returned
     * provider will not be valid until the injector has been created. The provider will throw an
     * {@code IllegalStateException} if you try to use it beforehand.
     */
    <T> Provider<T> getProvider(Key<T> key);

    /**
     * Returns the provider used to obtain instances for the given injection type. The returned
     * provider will not be valid until the injetor has been created. The provider will throw an
     * {@code IllegalStateException} if you try to use it beforehand.
     */
    <T> Provider<T> getProvider(Class<T> type);

    /**
     * Returns the members injector used to inject dependencies into methods and fields on instances
     * of the given type {@code T}. The returned members injector will not be valid until the main
     * injector has been created. The members injector will throw an {@code IllegalStateException}
     * if you try to use it beforehand.
     *
     * @param typeLiteral type to get members injector for
     */
    <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral);

    /**
     * Returns the members injector used to inject dependencies into methods and fields on instances
     * of the given type {@code T}. The returned members injector will not be valid until the main
     * injector has been created. The members injector will throw an {@code IllegalStateException}
     * if you try to use it beforehand.
     *
     * @param type type to get members injector for
     */
    <T> MembersInjector<T> getMembersInjector(Class<T> type);

    /**
     * Registers an injection listener for type {@code I}. Guice will notify the listener after
     * injecting an instance of {@code I}. The order in which Guice will invoke listeners is
     * unspecified.
     *
     * @param listener for injections into instances of type {@code I}
     */
    void register(InjectionListener<? super I> listener);

    /*if[AOP]*/
    /**
     * Binds method interceptor[s] to methods matched in type {@code I} and its supertypes. A
     * method is eligible for interception if:
     *
     * <ul>
     *  <li>Guice created the instance the method is on</li>
     *  <li>Neither the enclosing type nor the method is final</li>
     *  <li>And the method is package-private or more accessible</li>
     * </ul>
     *
     * @param methodMatcher matches methods the interceptor should apply to. For
     *     example: {@code annotatedWith(Transactional.class)}.
     * @param interceptors to bind
     */
    void bindInterceptor(Matcher<? super Method> methodMatcher,
        org.aopalliance.intercept.MethodInterceptor... interceptors);
    /*end[AOP]*/
  }
}
