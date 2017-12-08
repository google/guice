/*
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

import static com.google.common.base.Preconditions.checkState;

import com.google.inject.internal.Messages;
import com.google.inject.spi.Message;
import java.util.Collection;

/**
 * Thrown when a programming error such as a misplaced annotation, illegal binding, or unsupported
 * scope is found. Clients should catch this exception, log it, and stop execution.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class ConfigurationException extends RuntimeException {

  private final com.google.common.collect.ImmutableSet<Message> messages;
  private Object partialValue = null;

  /** Creates a ConfigurationException containing {@code messages}. */
  public ConfigurationException(Iterable<Message> messages) {
    this.messages = com.google.common.collect.ImmutableSet.copyOf(messages);
    initCause(Messages.getOnlyCause(this.messages));
  }

  /** Returns a copy of this configuration exception with the specified partial value. */
  public ConfigurationException withPartialValue(Object partialValue) {
    checkState(this.partialValue == null,
        "Can't clobber existing partial value %s with %s", this.partialValue, partialValue);
    ConfigurationException result = new ConfigurationException(messages);
    result.partialValue = partialValue;
    return result;
  }

  /** Returns messages for the errors that caused this exception. */
  public Collection<Message> getErrorMessages() {
    return messages;
  }

  /**
   * Returns a value that was only partially computed due to this exception. The caller can use this
   * while collecting additional configuration problems.
   *
   * @return the partial value, or {@code null} if none was set. The type of the partial value is
   *     specified by the throwing method.
   */
  @SuppressWarnings({
    "unchecked",
    "TypeParameterUnusedInFormals"
  }) // this is *extremely* unsafe. We trust the caller here.
  public <E> E getPartialValue() {
    return (E) partialValue;
  }

  @Override public String getMessage() {
    return Messages.formatMessages("Guice configuration errors", messages);
  }

  private static final long serialVersionUID = 0;
}
