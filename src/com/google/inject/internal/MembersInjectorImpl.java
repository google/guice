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

import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableSet;
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
  private final ImmutableList<SingleMemberInjector> memberInjectors;
  private final ImmutableList<MembersInjector<? super T>> userMembersInjectors;
  private final ImmutableList<InjectionListener<? super T>> injectionListeners;
  /*if[AOP]*/
  private final ImmutableList<MethodAspect> addedAspects;
  /*end[AOP]*/

  MembersInjectorImpl(InjectorImpl injector, TypeLiteral<T> typeLiteral,
      EncounterImpl<T> encounter, ImmutableList<SingleMemberInjector> memberInjectors) {
    this.injector = injector;
    this.typeLiteral = typeLiteral;
    this.memberInjectors = memberInjectors;
    this.userMembersInjectors = encounter.getMembersInjectors();
    this.injectionListeners = encounter.getInjectionListeners();
    /*if[AOP]*/
    this.addedAspects = encounter.getAspects();
    /*end[AOP]*/
  }

  public ImmutableList<SingleMemberInjector> getMemberInjectors() {
    return memberInjectors;
  }

  public void injectMembers(T instance) {
    Errors errors = new Errors(typeLiteral);
    try {
      injectAndNotify(instance, errors, false);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }

    errors.throwProvisionExceptionIfErrorsExist();
  }

  void injectAndNotify(final T instance, final Errors errors, final boolean toolableOnly) throws ErrorsException {
    if (instance == null) {
      return;
    }

    injector.callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) throws ErrorsException {
        injectMembers(instance, errors, context, toolableOnly);
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
    int numErrorsBefore = errors.size();
    for (InjectionListener<? super T> injectionListener : injectionListeners) {
      try {
        injectionListener.afterInjection(instance);
      } catch (RuntimeException e) {
        errors.errorNotifyingInjectionListener(injectionListener, typeLiteral, e);
      }
    }
    errors.throwIfNewErrors(numErrorsBefore);
  }

  void injectMembers(T t, Errors errors, InternalContext context, boolean toolableOnly) {
    // optimization: use manual for/each to save allocating an iterator here
    for (int i = 0, size = memberInjectors.size(); i < size; i++) {
      SingleMemberInjector injector = memberInjectors.get(i);
      if(!toolableOnly || injector.getInjectionPoint().isToolable()) {
        injector.inject(errors, context, t);
      }
    }

    // TODO: There's no way to know if a user's MembersInjector wants toolable injections.
    if(!toolableOnly) {
    // optimization: use manual for/each to save allocating an iterator here
      for (int i = 0, size = userMembersInjectors.size(); i < size; i++) {
        MembersInjector<? super T> userMembersInjector = userMembersInjectors.get(i);
        try {
          userMembersInjector.injectMembers(t);
        } catch (RuntimeException e) {
          errors.errorInUserInjector(userMembersInjector, typeLiteral, e);
        }
      }
    }
  }

  @Override public String toString() {
    return "MembersInjector<" + typeLiteral + ">";
  }

  public ImmutableSet<InjectionPoint> getInjectionPoints() {
    ImmutableSet.Builder<InjectionPoint> builder = ImmutableSet.builder();
    for (SingleMemberInjector memberInjector : memberInjectors) {
      builder.add(memberInjector.getInjectionPoint());
    }
    return builder.build();
  }

  /*if[AOP]*/
  public ImmutableList<MethodAspect> getAddedAspects() {
    return addedAspects;
  }
  /*end[AOP]*/
}
