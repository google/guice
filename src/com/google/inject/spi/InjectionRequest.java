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
 * A request to inject the instance fields and methods of an instance. Requests are created
 * explicitly in a module using {@link com.google.inject.Binder#requestInjection(Object[])
 * requestInjection()} statements:
 * <pre>
 *     requestInjection(serviceInstance);</pre>
 *
 * @author mikeward@google.com (Mike Ward)
 */
public final class InjectionRequest implements Element {
  private Object source;
  private List<Object> instances;

  public InjectionRequest(Object source, Object[] instances) {
    this.source = checkNotNull(source, "source");
    this.instances = ImmutableList.of(instances);
  }

  public Object getSource() {
    return source;
  }

  public List<Object> getInstances() {
    return instances;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitInjectionRequest(this);
  }
}
