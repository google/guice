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

package com.google.inject;

/**
 * Implemented by classes which can remember their source.
 *
 * @author crazybob@google.com (Bob Lee)
 */
interface SourceAware<R> {

  /**
   * Sets the source object. Useful for debugging. Contents may include the
   * name of the file and the line number this binding came from, a code
   * snippet, etc.
   */
  R from(Object source);
}
