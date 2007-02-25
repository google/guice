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

import com.google.inject.util.StackTraceElements;
import java.lang.reflect.Member;

/**
 * Used to rethrow exceptions that occur while providing instances, to add
 * additional contextual details.
 */
class ProvisionException extends RuntimeException {
  public ProvisionException(ExternalContext<?> externalContext,
      Throwable cause) {
    super(createMessage(externalContext), cause);
  }

  private static String createMessage(ExternalContext<?> externalContext) {
    Key<?> key = externalContext.getKey();
    Member member = externalContext.getMember();
    return String.format(ErrorMessages.EXCEPTION_WHILE_CREATING,
        ErrorMessages.convert(key),
        StackTraceElements.forMember(member));
  }
}
