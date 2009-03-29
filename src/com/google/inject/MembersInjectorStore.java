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
import com.google.inject.internal.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.InjectableType;
import com.google.inject.spi.InjectableTypeListenerBinding;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Members injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class MembersInjectorStore {
  private final InjectorImpl injector;
  private final ImmutableList<InjectableTypeListenerBinding> injectableTypeListenerBindings;

  private final FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>> cache
      = new FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>>() {
    @Override protected MembersInjectorImpl<?> create(TypeLiteral<?> type, Errors errors)
        throws ErrorsException {
      return createWithListeners(type, errors);
    }
  };

  MembersInjectorStore(InjectorImpl injector,
      List<InjectableTypeListenerBinding> injectableTypeListenerBindings) {
    this.injector = injector;
    this.injectableTypeListenerBindings = ImmutableList.copyOf(injectableTypeListenerBindings);
  }

  /**
   * Returns a new complete members injector with injection listeners registered.
   */
  @SuppressWarnings("unchecked") // the MembersInjector type always agrees with the passed type
  public <T> MembersInjectorImpl<T> get(TypeLiteral<T> key, Errors errors) throws ErrorsException {
    return (MembersInjectorImpl<T>) cache.get(key, errors);
  }

  /**
   * Creates a new members injector without attaching injection listeners.
   */
  public <T> MembersInjectorImpl<T> createWithoutListeners(TypeLiteral<T> type, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();
    Set<InjectionPoint> injectionPoints;
    try {
      injectionPoints = InjectionPoint.forInstanceMethodsAndFields(type);
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
      injectionPoints = e.getPartialValue();
    }
    ImmutableList<SingleMemberInjector> injectors = getInjectors(injectionPoints, errors);
    errors.throwIfNewErrors(numErrorsBefore);
    return new MembersInjectorImpl<T>(injector, type, injectors);
  }

  /**
   * Creates a new members injector and attaches injection listeners.
   */
  private <T> MembersInjectorImpl<T> createWithListeners(TypeLiteral<T> type, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();

    MembersInjectorImpl<T> membersInjector = createWithoutListeners(type, errors);

    InjectableType<T> injectableType = new InjectableType<T>(null, type,
        membersInjector.getInjectionPoints());

    EncounterImpl<T> encounter = new EncounterImpl<T>() {
      @Override public void bindInterceptor(Matcher<? super Method> methodMatcher,
          MethodInterceptor... interceptors) {
        throw new UnsupportedOperationException("TODO");
        // TODO: add an error here
      }
    };

    for (InjectableTypeListenerBinding typeListener : injectableTypeListenerBindings) {
      if (typeListener.getTypeMatcher().matches(type)) {
        try {
          typeListener.getListener().hear(injectableType, encounter);
        } catch (RuntimeException e) {
          errors.errorNotifyingTypeListener(typeListener, injectableType, e);
        }
      }
    }

    errors.throwIfNewErrors(numErrorsBefore);
    return membersInjector.withListeners(encounter.getInjectionListeners());
  }

  /**
   * Returns the injectors for the specified injection points.
   */
  ImmutableList<SingleMemberInjector> getInjectors(
      Set<InjectionPoint> injectionPoints, Errors errors) {
    List<SingleMemberInjector> injectors = Lists.newArrayList();
    for (InjectionPoint injectionPoint : injectionPoints) {
      try {
        Errors errorsForMember = injectionPoint.isOptional()
            ? new Errors(injectionPoint)
            : errors.withSource(injectionPoint);
        SingleMemberInjector injector = injectionPoint.getMember() instanceof Field
            ? new SingleFieldInjector(this.injector, injectionPoint, errorsForMember)
            : new SingleMethodInjector(this.injector, injectionPoint, errorsForMember);
        injectors.add(injector);
      } catch (ErrorsException ignoredForNow) {
        // ignored for now
      }
    }
    return ImmutableList.copyOf(injectors);
  }
}
