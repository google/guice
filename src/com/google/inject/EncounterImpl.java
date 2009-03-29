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

import com.google.inject.spi.InjectableType;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.Message;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.Lists;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import java.util.List;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
class EncounterImpl<T> implements InjectableType.Encounter<T> {
  private List<InjectionListener<? super T>> injectionListeners; // lazy
  private List<MethodAspect> aspects; // lazy

  boolean hasAddedAspects() {
    return aspects != null;
  }

  public boolean hasAddedListeners() {
    return injectionListeners != null;
  }

  public List<MethodAspect> getAspects() {
    return aspects;
  }

  ImmutableList<InjectionListener<? super T>> getInjectionListeners() {
    return injectionListeners == null
        ? ImmutableList.<InjectionListener<? super T>>of()
        : ImmutableList.copyOf(injectionListeners);
  }

  @SuppressWarnings("unchecked") // an InjectionListener<? super T> is an InjectionListener<T>
  public void register(InjectionListener<? super T> injectionListener) {
    if (injectionListeners == null) {
      injectionListeners = Lists.newArrayList();
    }

    injectionListeners.add((InjectionListener<T>) injectionListener);
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
