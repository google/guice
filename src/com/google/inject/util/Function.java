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
 * A Function provides a transformation on an object and returns the resulting
 * object.  For example, a {@code StringToIntegerFunction} may implement
 * <code>Function&lt;String,Integer&gt;</code> and transform integers in String
 * format to Integer format.
 *
 * <p>The transformation on the from object does not necessarily result in
 * an object of a different type.  For example, a
 * {@code FarenheitToCelciusFunction} may implement
 * <code>Function&lt;Float,Float&gt;</code>.
 *
 * <p>Implementors of Function which may cause side effects upon evaluation are
 * strongly encouraged to state this fact clearly in their API documentation.
 */
public interface Function<F,T> {

  /**
   * Applies the function to an object of type {@code F}, resulting in an object
   * of type {@code T}.  Note that types {@code F} and {@code T} may or may not
   * be the same.
   *
   * @param from The from object.
   * @return The resulting object.
   */
  T apply(F from);
}
