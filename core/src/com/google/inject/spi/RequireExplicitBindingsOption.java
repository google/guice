/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.spi;

import com.google.inject.Binder;
import static com.google.inject.internal.util.Preconditions.checkNotNull;

/**
 * A request to require explicit bindings.
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 3.0
 */
public final class RequireExplicitBindingsOption implements Element {
  private final Object source;

  RequireExplicitBindingsOption(Object source) {
    this.source = checkNotNull(source, "source");
  }

  public Object getSource() {
    return source;
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).requireExplicitBindings();
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }
}
