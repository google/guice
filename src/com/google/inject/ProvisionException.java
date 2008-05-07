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

import com.google.inject.internal.ErrorMessages;
import static com.google.inject.internal.ErrorMessages.*;
import com.google.inject.internal.StackTraceElements;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Indicates a failure to provide an instance.
 */
public class ProvisionException extends RuntimeException {

  private final String errorMessage;
  private final List<InjectionPoint<?>> contexts
      = new ArrayList<InjectionPoint<?>>(5);

  public ProvisionException(Throwable cause, String errorMessage) {
    super(errorMessage, cause);
    this.errorMessage = errorMessage;
  }

  /**
   * Add an injection point that was being resolved when this exception
   * occurred.
   */
  void addContext(InjectionPoint<?> injectionPoint) {
    this.contexts.add(injectionPoint);
  }

  @Override
  public String getMessage() {
    StringBuilder result = new StringBuilder();
    result.append(errorMessage);

    for (int i = contexts.size() - 1; i >= 0; i--) {
      InjectionPoint injectionPoint = contexts.get(i);
      result.append(String.format("%n"));
      result.append(contextToSnippet(injectionPoint));
    }

    return result.toString();
  }

  /**
   * Returns a snippet to include in the stacktrace message that describes the
   * specified context.
   */
  private String contextToSnippet(InjectionPoint injectionPoint) {
    Key<?> key = injectionPoint.getKey();
    Object keyDescription = ErrorMessages.convert(key);
    Member member = injectionPoint.getMember();

    if (member instanceof Field) {
      return String.format(ERROR_WHILE_LOCATING_FIELD,
          keyDescription, StackTraceElements.forMember(member));

    } else if (member instanceof Method || member instanceof Constructor) {
      return String.format(ERROR_WHILE_LOCATING_PARAMETER,
          keyDescription, injectionPoint.getParameterIndex(),
          StackTraceElements.forMember(member));

    } else {
      return String.format(ERROR_WHILE_LOCATING_VALUE, keyDescription);
    }
  }
}
