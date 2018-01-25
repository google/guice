/*
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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.ConfigurationException;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeListener;
import com.google.inject.spi.TypeListenerBinding;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/**
 * Members injectors by type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class MembersInjectorStore {
  private final InjectorImpl injector;
  private final ImmutableList<TypeListenerBinding> typeListenerBindings;

  private final FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>> cache =
      new FailableCache<TypeLiteral<?>, MembersInjectorImpl<?>>() {
        @Override
        protected MembersInjectorImpl<?> create(TypeLiteral<?> type, Errors errors)
            throws ErrorsException {
          return createWithListeners(type, errors);
        }
      };

  MembersInjectorStore(InjectorImpl injector, List<TypeListenerBinding> typeListenerBindings) {
    this.injector = injector;
    this.typeListenerBindings = ImmutableList.copyOf(typeListenerBindings);
  }

  /**
   * Returns true if any type listeners are installed. Other code may take shortcuts when there
   * aren't any type listeners.
   */
  public boolean hasTypeListeners() {
    return !typeListenerBindings.isEmpty();
  }

  /** Returns a new complete members injector with injection listeners registered. */
  @SuppressWarnings("unchecked") // the MembersInjector type always agrees with the passed type
  public <T> MembersInjectorImpl<T> get(TypeLiteral<T> key, Errors errors) throws ErrorsException {
    return (MembersInjectorImpl<T>) cache.get(key, errors);
  }

  /**
   * Purges a type literal from the cache. Use this only if the type is not actually valid for
   * binding and needs to be purged. (See issue 319 and
   * ImplicitBindingTest#testCircularJitBindingsLeaveNoResidue and
   * #testInstancesRequestingProvidersForThemselvesWithChildInjectors for examples of when this is
   * necessary.)
   *
   * <p>Returns true if the type was stored in the cache, false otherwise.
   */
  boolean remove(TypeLiteral<?> type) {
    return cache.remove(type);
  }

  /** Creates a new members injector and attaches both injection listeners and method aspects. */
  private <T> MembersInjectorImpl<T> createWithListeners(TypeLiteral<T> type, Errors errors)
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

    EncounterImpl<T> encounter = new EncounterImpl<>(errors, injector.lookups);
    Set<TypeListener> alreadySeenListeners = Sets.newHashSet();
    for (TypeListenerBinding binding : typeListenerBindings) {
      TypeListener typeListener = binding.getListener();
      if (!alreadySeenListeners.contains(typeListener) && binding.getTypeMatcher().matches(type)) {
        alreadySeenListeners.add(typeListener);
        try {
          typeListener.hear(type, encounter);
        } catch (RuntimeException e) {
          errors.errorNotifyingTypeListener(binding, type, e);
        }
      }
    }
    encounter.invalidate();
    errors.throwIfNewErrors(numErrorsBefore);

    return new MembersInjectorImpl<T>(injector, type, encounter, injectors);
  }

  /** Returns the injectors for the specified injection points. */
  ImmutableList<SingleMemberInjector> getInjectors(
      Set<InjectionPoint> injectionPoints, Errors errors) {
    List<SingleMemberInjector> injectors = Lists.newArrayList();
    for (InjectionPoint injectionPoint : injectionPoints) {
      try {
        Errors errorsForMember =
            injectionPoint.isOptional()
                ? new Errors(injectionPoint)
                : errors.withSource(injectionPoint);
        SingleMemberInjector injector =
            injectionPoint.getMember() instanceof Field
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
