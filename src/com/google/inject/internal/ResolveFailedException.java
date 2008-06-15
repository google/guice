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
import java.util.ArrayList;
import java.util.List;

/**
 * Indicates that resolving a binding failed. This is thrown when resolving a new binding, either at
 * injector-creation time or when resolving a just-in-time binding. It can fail due to a problem
 * executing user-supplied code (such as a user's UnsupportedOperationException), or due to a
 * configuration issue. 
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ResolveFailedException extends Exception {

  private final Errors errors;

  private final List<String> contexts = new ArrayList<String>(5);

  public ResolveFailedException(Errors errors) {
    this.errors = errors;
  }

  public Message getMessage(Object source) {
    return new Message(source, getMessage());
  }

  public List<String> getContexts() {
    return contexts;
  }

  @Override public String getMessage() {
    StringBuilder result = new StringBuilder();
    result.append(super.getMessage());

    for (String context : contexts) {
      result.append(String.format("%n"));
      result.append(context);
    }

    return result.toString();
  }

  public Errors getErrors() {
    return errors;
  }
}
