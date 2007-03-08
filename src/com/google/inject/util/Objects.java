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
 * Object utilities.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Objects {

  /**
   * Detects null values.
   *
   * @param t value
   * @param message to display in the event of a null
   * @return t
   */
  public static <T> T nonNull(T t, String message) {
    if (t == null) {
      throw new NullPointerException(message);
    }
    return t;
  }

  /**
   * {@code null}-aware equals.
   */
  public static boolean equal(Object a, Object b) {
    if (a == b) {
      return true;
    }

    if (a == null || b == null) {
      return false;
    }

    return a.equals(b);
  }

  /**
   * We use this as a sanity check immediately before injecting into a method
   * or constructor, to make sure we aren't supplying a null.  This should never
   * happen because we should have caught the problem earlier.  Perhaps this 
   * should be used with Java asserts...
   */
  public static void assertNoNulls(Object[] objects) {
    // TODO(kevinb): gee, ya think we might want to remove this?
    if (("I'm a bad hack".equals(
        System.getProperty("guice.allow.nulls.bad.bad.bad")))) {
      return;
    }
    if (objects != null) { // hmm. weird.
      for (Object object : objects) {
        if (object == null) {
          throw new AssertionError();
        }
      }
    }
  }
}
