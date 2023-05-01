/*
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

package com.google.inject.matcher;

/**
 * Implements {@code and()} and {@code or()}.
 *
 * @author crazybob@google.com (Bob Lee)
 * @deprecated This class used to be useful to avoid implementing {@code and()} and {@code or()}
 *     yourself, but is no longer necessary now that {@link Matcher} implements these methods.
 */
@Deprecated
public abstract class AbstractMatcher<T> implements Matcher<T> {
  // FYI: AbstractMatcher explicitly implements `and` and `or` in order
  // to reduce binary compatibility issues, despite their impls directly
  // delegating to the Matcher impl.

  @Override
  public Matcher<T> and(final Matcher<? super T> other) {
    return Matcher.super.and(other);
  }

  @Override
  public Matcher<T> or(Matcher<? super T> other) {
    return Matcher.super.or(other);
  }
}
