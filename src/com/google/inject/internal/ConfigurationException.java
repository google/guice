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

package com.google.inject.internal;

import com.google.inject.spi.Message;
import java.util.Collection;

/**
 * Indicates that the injector or injection points are improperly configured.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ConfigurationException extends RuntimeException {

  /** non-null for Guice-created ProvisionExceptions */
  private final Errors errors;

  /**
   * Constructs a new exception for the given errors.
   */
  public ConfigurationException(Errors errors) {
    errors.makeImmutable();
    this.errors = errors;

    // find a cause
    for (Message message : errors.getMessages()) {
      if (message.getCause() != null) {
        initCause(message.getCause());
        break;
      }
    }
  }

  /**
   * Gets the error messages which resulted in this exception.
   */
  public Collection<Message> getErrorMessages() {
    return errors.getMessages();
  }

  @Override public String getMessage() {
    return Errors.format("Guice configuration errors", errors.getMessages());
  }

  /**
   * Throws a new provision exception if {@code errors} contains any error
   * messages.
   */
  public static void throwNewIfNonEmpty(Errors errors) {
    if (errors.hasErrors()) {
      throw new ConfigurationException(errors.makeImmutable());
    }
  }
}
