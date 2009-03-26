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

package com.google.inject;

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.FailableCache;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import static com.google.inject.internal.Iterables.concat;
import com.google.inject.internal.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectableType;
import com.google.inject.spi.InjectableTypeListenerBinding;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Constructor injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class ConstructorInjectorStore extends FailableCache<TypeLiteral<?>, ConstructorInjector<?>> {

  private final InjectorImpl injector;
  private final ImmutableList<InjectableTypeListenerBinding> injectableTypeListenerBindings;

  public ConstructorInjectorStore(InjectorImpl injector,
      List<InjectableTypeListenerBinding> injectableTypeListenerBindings) {
    this.injector = injector;
    this.injectableTypeListenerBindings = ImmutableList.copyOf(injectableTypeListenerBindings);
  }

  @SuppressWarnings("unchecked")
  protected ConstructorInjector<?> create(TypeLiteral<?> type, Errors errors)
      throws ErrorsException {
    try {
      return createConstructor(type, errors);
    } catch (ConfigurationException e) {
      throw errors.merge(e.getErrorMessages()).toException();
    }
  }

  private <T> ConstructorInjector<T> createConstructor(TypeLiteral<T> type, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();

    InjectionPoint injectionPoint;
    try {
      injectionPoint = InjectionPoint.forConstructorOf(type);
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
      throw errors.toException();
    }

    ImmutableList<SingleParameterInjector<?>> constructorParameterInjectors
        = injector.getParametersInjectors(injectionPoint.getDependencies(), errors);
    ImmutableList<SingleMemberInjector> memberInjectors = injector.injectors.get(type, errors);

    ImmutableSet.Builder<InjectionPoint> injectableMembersBuilder = ImmutableSet.builder();
    for (SingleMemberInjector memberInjector : memberInjectors) {
      injectableMembersBuilder.add(memberInjector.getInjectionPoint());
    }
    ImmutableSet<InjectionPoint> injectableMembers = injectableMembersBuilder.build();

    ProxyFactory<T> proxyFactory = new ProxyFactory<T>(injectionPoint, injector.methodAspects);
    EncounterImpl<T> encounter = new EncounterImpl<T>();
    InjectableType<T> injectableType = new InjectableTypeImpl<T>(
        injectionPoint, type, injectableMembers, proxyFactory.getInterceptors());

    for (InjectableTypeListenerBinding typeListener : injectableTypeListenerBindings) {
      if (typeListener.getTypeMatcher().matches(type)) {
        try {
          typeListener.getListener().hear(injectableType, encounter);
        } catch (RuntimeException e) {
          errors.errorNotifyingTypeListener(typeListener, injectableType, e);
        }
      }
    }

    // rebuild the proxy factory and injectable type if new interceptors were added
    if (encounter.hasAddedAspects()) {
      proxyFactory = new ProxyFactory<T>(
          injectionPoint, concat(injector.methodAspects, encounter.aspects));
      injectableType = new InjectableTypeImpl<T>(
          injectionPoint, type, injectableMembers, proxyFactory.getInterceptors());
    }

    errors.throwIfNewErrors(numErrorsBefore);

    return new ConstructorInjector<T>(proxyFactory.create(), constructorParameterInjectors,
        memberInjectors, encounter.getInjectionListeners(), injectableType);
  }

  private static class EncounterImpl<T> implements InjectableType.Encounter<T> {
    private List<InjectionListener<? super T>> injectionListeners; // lazy
    private List<MethodAspect> aspects; // lazy

    boolean hasAddedAspects() {
      return aspects != null;
    }

    ImmutableList<InjectionListener<? super T>> getInjectionListeners() {
      return injectionListeners == null
          ? ImmutableList.<InjectionListener<? super T>>of()
          : ImmutableList.copyOf(injectionListeners);
    }

    public void register(InjectionListener<? super T> injectionListener) {
      if (injectionListeners == null) {
        injectionListeners = Lists.newArrayList();
      }

      injectionListeners.add(injectionListener);
    }

    public void bindInterceptor(Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      // make sure the applicable aspects is mutable
      if (aspects == null) {
        aspects = Lists.newArrayList();
      }

      aspects.add(new MethodAspect(Matchers.any(), methodMatcher, interceptors));
    }

    public void addError(String message, Object... arguments) {
      throw new UnsupportedOperationException("TODO");
    }

    public void addError(Throwable t) {
      throw new UnsupportedOperationException("TODO");
    }

    public void addError(Message message) {
      throw new UnsupportedOperationException("TODO");
    }

    public <T> Provider<T> getProvider(Key<T> key) {
      throw new UnsupportedOperationException("TODO");
    }

    public <T> Provider<T> getProvider(Class<T> type) {
      throw new UnsupportedOperationException("TODO");
    }

    public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
      throw new UnsupportedOperationException("TODO");
    }

    public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
      throw new UnsupportedOperationException("TODO");
    }
  }

  static class InjectableTypeImpl<T> implements InjectableType<T> {
    private final InjectionPoint injectionPoint;
    private final TypeLiteral<T> type;
    private final Set<InjectionPoint> injectableMembers;
    private final Map<Method, List<MethodInterceptor>> methodInterceptors;

    InjectableTypeImpl(InjectionPoint injectionPoint, TypeLiteral<T> type,
        Set<InjectionPoint> injectableMembers,
        Map<Method, List<MethodInterceptor>> methodInterceptors) {
      this.injectionPoint = injectionPoint;
      this.type = type;
      this.injectableMembers = injectableMembers;
      this.methodInterceptors = methodInterceptors;
    }

    public TypeLiteral<T> getType() {
      return type;
    }

    public InjectionPoint getInjectableConstructor() {
      return injectionPoint;
    }

    public Set<InjectionPoint> getInjectableMembers() throws ConfigurationException {
      return injectableMembers;
    }

    /*if[AOP]*/
    public Map<Method, List<MethodInterceptor>> getMethodInterceptors() {
      return methodInterceptors;
    }
    /*end[AOP]*/

    @Override public String toString() {
      return type.toString();
    }
  }
}
