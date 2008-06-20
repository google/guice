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

package com.google.inject.commands;

import com.google.common.collect.ImmutableList;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;

/**
 * Immutable snapshot of a request to add a string message.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class AddMessageCommand implements Command {
  private final Message message;

  AddMessageCommand(Message message) {
    this.message = message;
  }

  AddMessageCommand(Object source, String message, Object[] arguments) {
    this.message = new Message(source, String.format(message, arguments));
  }

  AddMessageCommand(Object source, Throwable throwable) {
    this.message = new Message(source,
        "An exception was caught and reported. Message: " + throwable.getMessage(), 
        ImmutableList.<InjectionPoint>of(), throwable);
  }

  public Object getSource() {
    return message.getSource();
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitAddMessage(this);
  }

  public Message getMessage() {
    return message;
  }
}
