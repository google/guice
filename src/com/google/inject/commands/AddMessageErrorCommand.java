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

import static com.google.inject.internal.Objects.nonNull;

import java.util.Arrays;
import static java.util.Collections.unmodifiableList;
import java.util.List;

/**
 * Immutable snapshot of a request to add a string message.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class AddMessageErrorCommand implements Command {
  private final Object source;
  private final String message;
  private final List<Object> arguments;

  AddMessageErrorCommand(Object source, String message, Object[] arguments) {
    this.source = nonNull(source, "source");
    this.message = nonNull(message, "message");
    this.arguments = unmodifiableList(Arrays.asList(arguments.clone()));
  }

  public Object getSource() {
    return source;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitAddMessageError(this);
  }

  public String getMessage() {
    return message;
  }

  public List<Object> getArguments() {
    return arguments;
  }
}
