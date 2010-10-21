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

import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import static com.google.inject.internal.util.Preconditions.checkState;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.Message;
import com.google.inject.spi.TypeEncounter;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class EncounterImpl<T> implements TypeEncounter<T> {

  private final Errors errors;
  private final Lookups lookups;
  private List<MembersInjector<? super T>> membersInjectors; // lazy
  private List<InjectionListener<? super T>> injectionListeners; // lazy
  /*if[AOP]*/
  private List<MethodAspect> aspects; // lazy
  /*end[AOP]*/
  private boolean valid = true;

  EncounterImpl(Errors errors, Lookups lookups) {
    this.errors = errors;
    this.lookups = lookups;
  }

  void invalidate() {
    valid = false;
  }

  /*if[AOP]*/
  ImmutableList<MethodAspect> getAspects() {
    return aspects == null
        ? ImmutableList.<MethodAspect>of()
        : ImmutableList.copyOf(aspects);
  }

  public void bindInterceptor(Matcher<? super Method> methodMatcher,
      org.aopalliance.intercept.MethodInterceptor... interceptors) {
    checkState(valid, "Encounters may not be used after hear() returns.");

    // make sure the applicable aspects is mutable
    if (aspects == null) {
      aspects = Lists.newArrayList();
    }

    aspects.add(new MethodAspect(Matchers.any(), methodMatcher, interceptors));
  }
  /*end[AOP]*/

  ImmutableList<MembersInjector<? super T>> getMembersInjectors() {
    return membersInjectors == null
        ? ImmutableList.<MembersInjector<? super T>>of()
        : ImmutableList.copyOf(membersInjectors);
  }

  ImmutableList<InjectionListener<? super T>> getInjectionListeners() {
    return injectionListeners == null
        ? ImmutableList.<InjectionListener<? super T>>of()
        : ImmutableList.copyOf(injectionListeners);
  }

  public void register(MembersInjector<? super T> membersInjector) {
    checkState(valid, "Encounters may not be used after hear() returns.");

    if (membersInjectors == null) {
      membersInjectors = Lists.newArrayList();
    }

    membersInjectors.add(membersInjector);
  }

  public void register(InjectionListener<? super T> injectionListener) {
    checkState(valid, "Encounters may not be used after hear() returns.");

    if (injectionListeners == null) {
      injectionListeners = Lists.newArrayList();
    }

    injectionListeners.add(injectionListener);
  }

  public void addError(String message, Object... arguments) {
    checkState(valid, "Encounters may not be used after hear() returns.");
    errors.addMessage(message, arguments);
  }

  public void addError(Throwable t) {
    checkState(valid, "Encounters may not be used after hear() returns.");
    errors.errorInUserCode(t, "An exception was caught and reported. Message: %s", t.getMessage());
  }

  public void addError(Message message) {
    checkState(valid, "Encounters may not be used after hear() returns.");
    errors.addMessage(message);
  }

  public <T> Provider<T> getProvider(Key<T> key) {
    checkState(valid, "Encounters may not be used after hear() returns.");
    return lookups.getProvider(key);
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
    checkState(valid, "Encounters may not be used after hear() returns.");
    return lookups.getMembersInjector(typeLiteral);
  }

  public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
    return getMembersInjector(TypeLiteral.get(type));
  }
}