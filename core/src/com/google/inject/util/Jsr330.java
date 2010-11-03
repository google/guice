/**
 * Copyright (C) 2009 Google Inc.
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

import static com.google.inject.internal.util.Preconditions.checkNotNull;
import javax.inject.Provider;

/**
 * Utility methods for use with <a href="http://code.google.com/p/atinject/">JSR
 * 330</a>.
 *
 * @author jessewilson@google.com (Jesse Wilson) 
 * @since 3.0
 */
public final class Jsr330 {

  private Jsr330() {}

  /**
   * Returns a Guice-friendly {@code com.google.inject.Provider} for the given
   * JSR-330 {@code javax.inject.Provider}. The converse method is unnecessary,
   * since Guice providers directly implement the JSR-330 interface.
   */
  public static <T> com.google.inject.Provider<T> guicify(Provider<T> provider) {
    if (provider instanceof com.google.inject.Provider) {
      return (com.google.inject.Provider<T>) provider;
    }

    final Provider<T> delegate = checkNotNull(provider, "provider");
    return new com.google.inject.Provider<T>() {
      public T get() {
        return delegate.get();
      }

      @Override public String toString() {
        return "guicified(" + delegate + ")";
      }
    };
  }
}
