/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.internal;

import com.google.inject.Key;
import java.util.Set;

/**
 * Minimal set that doesn't hold strong references to the contained keys.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class WeakKeySet {

  /**
   * We store strings rather than keys so we don't hold strong references.
   *
   * <p>One potential problem with this approach is that parent and child injectors cannot define
   * keys whose class names are equal but class loaders are different. This shouldn't be an issue
   * in practice.
   */
  private Set<String> backingSet;

  public boolean add(Key<?> key) {
    if (backingSet == null) {
      backingSet = Sets.newHashSet();
    }
    return backingSet.add(key.toString());
  }

  public boolean contains(Object o) {
    // avoid calling key.toString() if the backing set is empty. toString is expensive in aggregate,
    // and most WeakKeySets are empty in practice (because they're used by top-level injectors)
    return backingSet != null && o instanceof Key && backingSet.contains(o.toString());
  }
}
