// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * Returns {@code true} or {@code false} for a given input.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public interface Query<T> {

  /**
   * Returns {@code true} if this query matches {@code t}, {@code false}
   * otherwise.
   */
  boolean matches(T t);

  /**
   * Returns a query which returns {@code true} if both this and the given
   * query return {@code true}.
   */
  Query<T> and(Query<? super T> other);

  /**
   * Returns a query which returns {@code true} if either this or the given
   * query return {@code true}.
   */
  Query<T> or(Query<? super T> other);
}
