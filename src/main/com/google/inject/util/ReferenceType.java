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

/**
 * Reference type. Used to specify what type of reference to keep to a
 * referent.
 *
 * @see java.lang.ref.Reference
 * @author crazybob@google.com (Bob Lee)
 */
public enum ReferenceType {

  /**
   * Prevents referent from being reclaimed by the garbage collector.
   */
  STRONG,

  /**
   * Referent reclaimed in an LRU fashion when the VM runs low on memory and
   * no strong references exist.
   *
   * @see java.lang.ref.SoftReference
   */
  SOFT,

  /**
   * Referent reclaimed when no strong or soft references exist.
   *
   * @see java.lang.ref.WeakReference
   */
  WEAK,

  /**
   * Similar to weak references except the garbage collector doesn't actually
   * reclaim the referent. More flexible alternative to finalization.
   *
   * @see java.lang.ref.PhantomReference
   */
  PHANTOM;
}
