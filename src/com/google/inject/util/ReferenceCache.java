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

package com.google.inject.util;

import static com.google.inject.util.ReferenceType.STRONG;

/**
 * Extends {@link ReferenceMap} to support lazy loading values by overriding
 * {@link #create(Object)}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class ReferenceCache<K, V>
    extends AbstractReferenceCache<K, V> {

  private static final long serialVersionUID = 0;

  public ReferenceCache(ReferenceType keyReferenceType,
      ReferenceType valueReferenceType) {
    super(keyReferenceType, valueReferenceType);
  }

  /**
   * Equivalent to {@code new ReferenceCache(STRONG, STRONG)}.
   */
  public ReferenceCache() {
    super(STRONG, STRONG);
  }

  /**
   * Override to lazy load values. Use as an alternative to {@link
   * #put(Object,Object)}. Invoked by getter if value isn't already cached.
   * Must not return {@code null}. This method will not be called again until
   * the garbage collector reclaims the returned value.
   */
  protected abstract V create(K key);

  V create(FutureValue<V> futureValue, K key) {
    return create(key);
  }

  /**
   * Returns a {@code ReferenceCache} delegating to the specified {@code
   * function}. The specified function must not return {@code null}.
   */
  public static <K, V> ReferenceCache<K, V> of(
      ReferenceType keyReferenceType,
      ReferenceType valueReferenceType,
      final Function<? super K, ? extends V> function) {
    ensureNotNull(function);
    return new ReferenceCache<K, V>(keyReferenceType, valueReferenceType) {
      protected V create(K key) {
        return function.apply(key);
      }
      private static final long serialVersionUID = 0;
    };
  }
}