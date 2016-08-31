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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ProvisionListenerStackCallback.ProvisionCallback;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.InjectionPoint;

/**
 * Injects members of instances of a given type.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class MembersInjectorImpl<T> implements MembersInjector<T> {
  private final TypeLiteral<T> typeLiteral;
  private final InjectorImpl injector;
  // a null list means empty. Since it is common for many of these lists to be empty we can save
  // some memory lookups by representing empty as null.
  /* @Nullable */ private final ImmutableList<SingleMemberInjector> memberInjectors;
  /* @Nullable */ private final ImmutableList<MembersInjector<? super T>> userMembersInjectors;
  /* @Nullable */ private final ImmutableList<InjectionListener<? super T>> injectionListeners;
  /*if[AOP]*/
  /* @Nullable */ private final ImmutableList<MethodAspect> addedAspects;
  /*end[AOP]*/

  MembersInjectorImpl(InjectorImpl injector, TypeLiteral<T> typeLiteral,
      EncounterImpl<T> encounter, ImmutableList<SingleMemberInjector> memberInjectors) {
    this.injector = injector;
    this.typeLiteral = typeLiteral;
    this.memberInjectors = memberInjectors.isEmpty() ? null : memberInjectors;
    this.userMembersInjectors = encounter.getMembersInjectors().isEmpty() 
        ? null 
        : encounter.getMembersInjectors().asList();
    this.injectionListeners = encounter.getInjectionListeners().isEmpty() 
        ? null 
        : encounter.getInjectionListeners().asList();
    /*if[AOP]*/
    this.addedAspects = encounter.getAspects().isEmpty() ? null : encounter.getAspects();
    /*end[AOP]*/
  }

  public ImmutableList<SingleMemberInjector> getMemberInjectors() {
    return memberInjectors == null ? ImmutableList.<SingleMemberInjector>of() : memberInjectors;
  }

  @Override
  public void injectMembers(T instance) {
    TypeLiteral<T> localTypeLiteral = typeLiteral;
    Errors errors = new Errors(localTypeLiteral);
    try {
      injectAndNotify(instance, errors, null, null, localTypeLiteral, false);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }

    errors.throwProvisionExceptionIfErrorsExist();
  }

  void injectAndNotify(final T instance,
      final Errors errors,
      final Key<T> key, // possibly null!
      final ProvisionListenerStackCallback<T> provisionCallback, // possibly null!
      final Object source,
      final boolean toolableOnly) throws ErrorsException {
    if (instance == null) {
      return;
    }

    injector.callInContext(new ContextualCallable<Void>() {
      @Override
      public Void call(final InternalContext context) throws ErrorsException {
        context.pushState(key, source);
        try {
          if (provisionCallback != null) {
            provisionCallback.provision(errors, context, new ProvisionCallback<T>() {
              @Override public T call() {
                injectMembers(instance, errors, context, toolableOnly);
                return instance;
              }
            });
          } else {
            injectMembers(instance, errors, context, toolableOnly);
          }
        } finally {
          context.popState();
        }
        return null;
      }
    });

    // TODO: We *could* notify listeners too here,
    // but it's not clear if we want to.  There's no way to know
    // if a MembersInjector from the usersMemberInjector list wants
    // toolable injections, so do we really want to notify
    // about injection?  (We could take a strategy of only notifying
    // if atleast one InjectionPoint was toolable, in which case
    // the above callInContext could return 'true' if it injected
    // anything.)
    if(!toolableOnly) {
      notifyListeners(instance, errors);
    }
  }

  void notifyListeners(T instance, Errors errors) throws ErrorsException {
    ImmutableList<InjectionListener<? super T>> localInjectionListeners = injectionListeners;
    if (localInjectionListeners == null) {
      // no listeners
      return;
    }
    int numErrorsBefore = errors.size();
    // optimization: use manual for/each to save allocating an iterator here
    for (int i = 0; i < localInjectionListeners.size(); i++) {
      InjectionListener<? super T> injectionListener = localInjectionListeners.get(i);
      try {
        injectionListener.afterInjection(instance);
      } catch (RuntimeException e) {
        errors.errorNotifyingInjectionListener(injectionListener, typeLiteral, e);
      }
    }
    errors.throwIfNewErrors(numErrorsBefore);
  }

  void injectMembers(T t, Errors errors, InternalContext context, boolean toolableOnly) {
    ImmutableList<SingleMemberInjector> localMembersInjectors = memberInjectors;
    if (localMembersInjectors != null) {
      // optimization: use manual for/each to save allocating an iterator here
      for (int i = 0, size = localMembersInjectors.size(); i < size; i++) {
        SingleMemberInjector injector = localMembersInjectors.get(i);
        if(!toolableOnly || injector.getInjectionPoint().isToolable()) {
          injector.inject(errors, context, t);
        }
      }
    }

    // TODO: There's no way to know if a user's MembersInjector wants toolable injections.
    if(!toolableOnly) {
      ImmutableList<MembersInjector<? super T>> localUsersMembersInjectors = userMembersInjectors;
      if (localUsersMembersInjectors != null) {
        // optimization: use manual for/each to save allocating an iterator here
        for (int i = 0; i < localUsersMembersInjectors.size(); i++) {
          MembersInjector<? super T> userMembersInjector = localUsersMembersInjectors.get(i);
          try {
            userMembersInjector.injectMembers(t);
          } catch (RuntimeException e) {
            errors.errorInUserInjector(userMembersInjector, typeLiteral, e);
          }
        }
      }
    }
  }

  @Override public String toString() {
    return "MembersInjector<" + typeLiteral + ">";
  }

  public ImmutableSet<InjectionPoint> getInjectionPoints() {
    ImmutableList<SingleMemberInjector> localMemberInjectors = memberInjectors;
    if (localMemberInjectors != null) {
      ImmutableSet.Builder<InjectionPoint> builder = ImmutableSet.builder();
      for (SingleMemberInjector memberInjector : localMemberInjectors) {
        builder.add(memberInjector.getInjectionPoint());
      }
      return builder.build();
    }
    return ImmutableSet.of();
  }

  /*if[AOP]*/
  public ImmutableList<MethodAspect> getAddedAspects() {
    return addedAspects == null ? ImmutableList.<MethodAspect>of() : addedAspects;
  }
  /*end[AOP]*/
}
