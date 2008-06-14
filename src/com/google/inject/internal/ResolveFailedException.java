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

/**
 * Indicates that resolving a binding failed. This is thrown when resolving a
 * new binding, either at injector-creation time or when resolving a
 * just-in-time binding.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ResolveFailedException extends Exception {

  public ResolveFailedException(ErrorMessage errorMessage) {
    super(errorMessage.toString());
  }

  public Message getMessage(Object source) {
    return new Message(source, getMessage());
  }
}
