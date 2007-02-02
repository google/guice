// Copyright 2006 Google Inc. All Rights Reserved.

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
