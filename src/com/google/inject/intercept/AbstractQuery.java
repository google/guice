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

package com.google.inject.intercept;

/**
 * Implements {@code and()} and {@code or()}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class AbstractQuery<T> implements Query<T> {

  public Query<T> and(final Query<? super T> other) {
    return new AndQuery<T>(this, other);
  }

  public Query<T> or(Query<? super T> other) {
    return new OrQuery<T>(this, other);
  }

  static class AndQuery<T> extends AbstractQuery<T> {

    final Query<? super T> a, b;

    public AndQuery(Query<? super T> a, Query<? super T> b) {
      this.a = a;
      this.b = b;
    }

    public boolean matches(T t) {
      return a.matches(t) && b.matches(t);
    }
  }

  static class OrQuery<T> extends AbstractQuery<T> {

    final Query<? super T> a, b;

    public OrQuery(Query<? super T> a, Query<? super T> b) {
      this.a = a;
      this.b = b;
    }

    public boolean matches(T t) {
      return a.matches(t) || b.matches(t);
    }
  }
}
