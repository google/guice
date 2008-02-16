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

package com.google.inject.visitable;

import java.util.Arrays;
import static java.util.Collections.unmodifiableList;
import java.util.List;

/**
 * Immutable snapshot of a request for static injection.
 * 
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class RequestStaticInjectionCommand implements Command {
  private final List<Class> types;

  RequestStaticInjectionCommand(Class[] types) {
    this.types = unmodifiableList(Arrays.asList(types.clone()));
  }

  public List<Class> getTypes() {
    return types;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitRequestStaticInjection(this);
  }
}
