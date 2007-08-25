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

import static com.google.inject.ErrorMessages.ERROR_WHILE_LOCATING_FIELD;
import static com.google.inject.ErrorMessages.ERROR_WHILE_LOCATING_PARAMETER;
import static com.google.inject.ErrorMessages.ERROR_WHILE_LOCATING_VALUE;
import com.google.inject.internal.Objects;
import com.google.inject.internal.StackTraceElements;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Used to rethrow exceptions that occur while providing instances, to add
 * additional contextual details.
 */
public class ProvisionException extends RuntimeException {

  private static final String NEWLINE = String.format("%n");

  private final String errorMessage;
  private final List<ExternalContext> contexts =
      new ArrayList<ExternalContext>(4);

  ProvisionException(ExternalContext<?> externalContext,
      Throwable cause, String errorMessage) {
    super(errorMessage, cause);
    this.errorMessage = errorMessage;
    contexts.add(externalContext);
  }

  /**
   * Add more context to this exception, to be included in the exception
   * message. This allows nested contexts to be displayed more concisely than
   * with exception chaining.
   */
  void addContext(ExternalContext<?> externalContext) {
    // deduplicate contexts.
    if (!contexts.isEmpty()) {
      ExternalContext last = contexts.get(contexts.size() - 1);
      if (Objects.equal(last.getKey(), externalContext.getKey())
          && Objects.equal(last.getMember(), externalContext.getMember())) {
        return;
      }
    }
    
    contexts.add(externalContext);
  }

  @Override
  public String getMessage() {
    StringBuilder result = new StringBuilder();
    result.append(errorMessage)
        .append(NEWLINE);

    for (Iterator<ExternalContext> e = contexts.iterator(); e.hasNext(); ) {
      ExternalContext externalContext = e.next();
      result.append(contextToSnippet(externalContext));
      if (e.hasNext()) {
        result.append(NEWLINE);
      }
    }

    return result.toString();
  }

  /**
   * Returns a snippet to include in the stacktrace message that describes the
   * specified context.
   */
  private String contextToSnippet(ExternalContext externalContext) {
    Key<?> key = externalContext.getKey();
    Object keyDescription = ErrorMessages.convert(key);
    Member member = externalContext.getMember();

    if (member instanceof Field) {
      return String.format(ERROR_WHILE_LOCATING_FIELD,
          keyDescription, StackTraceElements.forMember(member));

    } else if (member instanceof Method || member instanceof Constructor) {
      return String.format(ERROR_WHILE_LOCATING_PARAMETER,
          keyDescription, externalContext.getParameterIndex(),
          StackTraceElements.forMember(member));

    } else {
      return String.format(ERROR_WHILE_LOCATING_VALUE, keyDescription);
    }
  }
}
