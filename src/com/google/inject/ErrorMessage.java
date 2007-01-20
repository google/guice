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

import static com.google.inject.util.Objects.nonNull;

/**
 * A configuration error.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorMessage {

  final Object source;
  final String message;

  public ErrorMessage(Object source, String message) {
    this.source = nonNull(source, "source");
    this.message = nonNull(message, "message");
  }

  /**
   * Gets the source of the configuration which resulted in this error message.
   */
  public Object getSource() {
    return source;
  }

  /**
   * Gets the error message text.
   */
  public String getMessage() {
    return message;
  }

  public String toString() {
    return source + " " + message;
  }

  public int hashCode() {
    return source.hashCode() * 31 + message.hashCode();
  }

  public boolean equals(Object o) {
    if (!(o instanceof ErrorMessage)) {
      return false;
    }
    ErrorMessage e = (ErrorMessage) o;
    return source.equals(e.source) && message.equals(e.message);
  }
}
