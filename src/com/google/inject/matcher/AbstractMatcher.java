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

package com.google.inject.matcher;

/**
 * Implements {@code and()} and {@code or()}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class AbstractMatcher<T> implements Matcher<T> {

  public Matcher<T> and(final Matcher<? super T> other) {
    return new AndMatcher<T>(this, other);
  }

  public Matcher<T> or(Matcher<? super T> other) {
    return new OrMatcher<T>(this, other);
  }

  static class AndMatcher<T> extends AbstractMatcher<T> {

    final Matcher<? super T> a, b;

    public AndMatcher(Matcher<? super T> a, Matcher<? super T> b) {
      this.a = a;
      this.b = b;
    }

    public boolean matches(T t) {
      return a.matches(t) && b.matches(t);
    }
  }

  static class OrMatcher<T> extends AbstractMatcher<T> {

    final Matcher<? super T> a, b;

    public OrMatcher(Matcher<? super T> a, Matcher<? super T> b) {
      this.a = a;
      this.b = b;
    }

    public boolean matches(T t) {
      return a.matches(t) || b.matches(t);
    }
  }
}
