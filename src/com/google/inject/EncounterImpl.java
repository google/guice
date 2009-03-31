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
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.InjectableType;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.Message;
import java.lang.reflect.Method;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class EncounterImpl<T> implements InjectableType.Encounter<T> {

  private final Errors errors;
  private final Lookups lookups;
  private List<InjectionListener<? super T>> injectionListeners; // lazy
  private List<MethodAspect> aspects; // lazy

  public EncounterImpl(Errors errors, Lookups lookups) {
    this.errors = errors;
    this.lookups = lookups;
  }

  public boolean hasAddedAspects() {
    return aspects != null;
  }

  public boolean hasAddedListeners() {
    return injectionListeners != null;
  }

  public List<MethodAspect> getAspects() {
    return aspects;
  }

  public ImmutableList<InjectionListener<? super T>> getInjectionListeners() {
    return injectionListeners == null
        ? ImmutableList.<InjectionListener<? super T>>of()
        : ImmutableList.copyOf(injectionListeners);
  }

  @SuppressWarnings("unchecked") // an InjectionListener<? super T> is an InjectionListener<T>
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
    errors.addMessage(message, arguments);
  }

  public void addError(Throwable t) {
    errors.errorInUserCode(t, "An exception was caught and reported. Message: %s", t.getMessage());
  }

  public void addError(Message message) {
    errors.addMessage(message);
  }

  public <T> Provider<T> getProvider(Key<T> key) {
    return lookups.getProvider(key);
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  public <T> MembersInjector<T> getMembersInjector(TypeLiteral<T> typeLiteral) {
    return lookups.getMembersInjector(typeLiteral);
  }

  public <T> MembersInjector<T> getMembersInjector(Class<T> type) {
    return getMembersInjector(TypeLiteral.get(type));
  }
}