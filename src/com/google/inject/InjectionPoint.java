/**
 * Copyright (C) 2006 Google Inc.
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

import com.google.inject.internal.ErrorMessages;
import com.google.inject.internal.Objects;
import com.google.inject.internal.ToStringBuilder;
import com.google.inject.spi.Dependency;

import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * An immutable snapshot of where the value is to be injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InjectionPoint<T> implements Dependency<T> {

  final Member member;
  final Key<T> key;
  final InjectorImpl injector;
  final int parameterIndex;
  final Nullability nullability;

  private InjectionPoint(Member member, int paramterIndex,
      Nullability nullability, Key<T> key, InjectorImpl injector) {
    this.member = member;
    this.parameterIndex = paramterIndex;
    this.nullability = Objects.nonNull(nullability, "nullability");
    this.key = key;
    this.injector = injector;
  }

  public Key<T> getKey() {
    return this.key;
  }

  public Binding<T> getBinding() {
    return injector.getBinding(key);
  }

  public boolean allowsNull() {
    return getNullability() == Nullability.NULLABLE;
  }

  public Injector getInjector() {
    return injector;
  }

  public Member getMember() {
    return member;
  }

  public int getParameterIndex() {
    return parameterIndex;
  }

  public Nullability getNullability() {
    return nullability;
  }

  public String toString() {
    return new ToStringBuilder("Context")
        .add("member", member)
        .add("key", key)
        .add("injector", injector)
        .toString();
  }

  <T> T checkForNull(T value, Object source) {
    if (value != null
        || getNullability() == Nullability.NULLABLE
        || allowNullsBadBadBad()) {
      return value;
    }

    String message = getMember() != null
        ? String.format(ErrorMessages.CANNOT_INJECT_NULL_INTO_MEMBER, source,
            getMember())
        : String.format(ErrorMessages.CANNOT_INJECT_NULL, source);

    throw new ProvisionException(new NullPointerException(message),
        String.format(ErrorMessages.CANNOT_INJECT_NULL, source));
  }

  // TODO(kevinb): gee, ya think we might want to remove this?
  private static boolean allowNullsBadBadBad() {
    return "I'm a bad hack".equals(
          System.getProperty("guice.allow.nulls.bad.bad.bad"));
  }

  static <T> InjectionPoint<T> newInstance(Field field,
      Nullability nullability, Key<T> key, InjectorImpl injector) {
    return new InjectionPoint<T>(field, -1, nullability, key, injector);
  }

  static <T> InjectionPoint<T> newInstance(Key<T> key, InjectorImpl injector) {
    return new InjectionPoint<T>(null, -1, Nullability.NULLABLE, key, injector);
  }

  static <T> InjectionPoint<T> newInstance(Member member, int parameterIndex,
      Nullability nullability, Key<T> key, InjectorImpl injector) {
    return new InjectionPoint<T>(member, parameterIndex, nullability, key,
        injector);
  }
}
