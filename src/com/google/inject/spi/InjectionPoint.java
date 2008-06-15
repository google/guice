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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Key;
import com.google.inject.internal.Errors;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.ResolveFailedException;
import com.google.inject.internal.ToStringBuilder;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

/**
 * An immutable snapshot of where the value is to be injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class InjectionPoint<T> implements Serializable {

  private final Member member;
  private final Key<T> key;
  private final int parameterIndex;
  private final boolean allowsNull;

  private InjectionPoint(Member member, int paramterIndex,
      boolean allowsNull, Key<T> key) {
    this.member = member == null ? null : MoreTypes.canonicalize(member);
    this.parameterIndex = paramterIndex;
    this.allowsNull = allowsNull;
    this.key = checkNotNull(key, "key");
  }

  public Key<T> getKey() {
    return this.key;
  }

  public Member getMember() {
    return member;
  }

  public int getParameterIndex() {
    return parameterIndex;
  }

  public boolean allowsNull() {
    return allowsNull;
  }

  public String toString() {
    return new ToStringBuilder(InjectionPoint.class)
        .add("member", member)
        .add("key", key)
        .toString();
  }

  public <T> T checkForNull(Errors errors, T value, Object source) throws ResolveFailedException {
    if (value != null
        || allowsNull()
        || allowNullsBadBadBad()) {
      return value;
    }

    throw errors.cannotInjectNull(source, getMember(), parameterIndex).toException();
  }

  // TODO(kevinb): gee, ya think we might want to remove this?
  private static boolean allowNullsBadBadBad() {
    return "I'm a bad hack".equals(
          System.getProperty("guice.allow.nulls.bad.bad.bad"));
  }

  public static <T> InjectionPoint<T> newInstance(
      Field field, boolean allowsNull, Key<T> key) {
    return new InjectionPoint<T>(field, -1, allowsNull, key);
  }

  public static <T> InjectionPoint<T> newInstance(Key<T> key) {
    return new InjectionPoint<T>(null, -1, true, key);
  }

  public static <T> InjectionPoint<T> newInstance(Member member, int parameterIndex,
      boolean allowsNull, Key<T> key) {
    return new InjectionPoint<T>(member, parameterIndex, allowsNull, key);
  }
}
