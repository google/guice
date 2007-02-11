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

import java.lang.ref.WeakReference;

/**
 * Weak reference with a {@link FinalizableReference#finalizeReferent()} method
 * which a background thread invokes after the garbage collector reclaims the
 * referent. This is a simpler alternative to using a
 * {@link java.lang.ref.ReferenceQueue}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class FinalizableWeakReference<T> extends WeakReference<T>
    implements FinalizableReference {

  protected FinalizableWeakReference(T referent) {
    super(referent, FinalizableReferenceQueue.getInstance());
  }
}
