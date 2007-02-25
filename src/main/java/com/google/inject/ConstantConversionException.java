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

import java.lang.reflect.Member;

/**
 * Thrown when a constant type conversion error occurs.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ConstantConversionException extends Exception {

  ConstantConversionException(Member member, Key<?> key, String value,
      String reason) {
    super(createMessage(value, key, member, reason));
  }

  ConstantConversionException(Member member, Key<?> key, String value,
      Throwable reason) {
    this(member, key, value, reason.toString());
  }

  static String createMessage(String value, Key<?> key, Member member,
      String reason) {
    String annotationMessage = key.hasAnnotationType()
        ? " annotated with " + key.getAnnotationName()
        : "";
    
    return member == null
        ? "Error converting '" + value + "' to "
            + key.getRawType().getSimpleName()
            + " while getting binding value" + annotationMessage
            + ". Reason: " + reason
        : "Error converting '" + value + "' to "
            + key.getRawType().getSimpleName() + " while injecting "
            + member.getName() + " with binding value" + annotationMessage
            + " required by " + member.getDeclaringClass().getSimpleName()
            + ". Reason: " + reason;
  }
}
