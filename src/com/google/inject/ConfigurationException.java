/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.inject.internal.Errors;
import com.google.inject.spi.Message;
import java.util.Collection;

/**
 * Thrown when a programming error such as a misplaced annotation, illegal binding, or unsupported
 * scope is found. Clients should catch this exception, log it, and stop execution.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class ConfigurationException extends RuntimeException {

  private final ImmutableSet<Message> messages;

  /** Creates a ConfigurationException containing {@code messages}. */
  public ConfigurationException(Iterable<Message> messages) {
    this.messages = ImmutableSet.copyOf(messages);
    initCause(Errors.getOnlyCause(this.messages));
  }

  /** Returns messages for the errors that caused this exception. */
  public Collection<Message> getErrorMessages() {
    return messages;
  }

  @Override public String getMessage() {
    return Errors.format("Guice configuration errors", messages);
  }

  private static final long serialVersionUID = 0;
}