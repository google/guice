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

package com.google.inject.spi;


import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Immutable snapshot of a request for static injection.
 * 
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class RequestStaticInjection implements Element {
  private final Object source;
  private final List<Class> types;

  RequestStaticInjection(Object source, Class[] types) {
    this.source = checkNotNull(source, "source");
    this.types = ImmutableList.of(types);
  }

  public Object getSource() {
    return source;
  }

  public List<Class> getTypes() {
    return types;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitRequestStaticInjection(this);
  }
}
