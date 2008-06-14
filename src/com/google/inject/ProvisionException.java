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

import static com.google.inject.internal.ErrorMessage.whileLocatingField;
import static com.google.inject.internal.ErrorMessage.whileLocatingParameter;
import static com.google.inject.internal.ErrorMessage.whileLocatingValue;
import com.google.inject.internal.StackTraceElements;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicates a failure to provide an instance.
 *
 * @author kevinb@google.com (Kevin Bourrillion)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ProvisionException extends RuntimeException {

  private final List<String> contexts = new ArrayList<String>(5);

  public ProvisionException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override public String getMessage() {
    StringBuilder result = new StringBuilder();
    result.append(super.getMessage());

    for (int i = contexts.size() - 1; i >= 0; i--) {
      result.append(String.format("%n"));
      result.append(contexts.get(i));
    }

    return result.toString();
  }

  /**
   * Add an injection point that was being resolved when this exception
   * occurred.
   */
  void addContext(InjectionPoint<?> injectionPoint) {
    this.contexts.add(contextToSnippet(injectionPoint));
  }

  /**
   * Returns a snippet to include in the stacktrace message that describes the
   * specified context.
   */
  private String contextToSnippet(InjectionPoint injectionPoint) {
    Key<?> key = injectionPoint.getKey();
    Member member = injectionPoint.getMember();

    if (member instanceof Field) {
      return whileLocatingField(
          key, StackTraceElements.forMember(member)).toString();

    } else if (member instanceof Method || member instanceof Constructor) {
      return whileLocatingParameter(
          key, injectionPoint.getParameterIndex(),
          StackTraceElements.forMember(member)).toString();

    } else {
      return whileLocatingValue(key).toString();
    }
  }
}
