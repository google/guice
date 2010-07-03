/**
 * Copyright (C) 2009 Google Inc.
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
package com.google.inject.assistedinject;

import com.google.inject.ConfigurationException;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Maps;
import com.google.inject.spi.Message;
import java.util.Collections;
import java.util.Map;

/**
 * Utility class for collecting factory bindings. Used for configuring {@link FactoryProvider2}.
 *
 * @author schmitt@google.com (Peter Schmitt)
 */
class BindingCollector {

  private final Map<Key<?>, TypeLiteral<?>> bindings = Maps.newHashMap();

  public BindingCollector addBinding(Key<?> key, TypeLiteral<?> target) {
    if (bindings.containsKey(key)) {
      throw new ConfigurationException(ImmutableSet.of(
          new Message("Only one implementation can be specified for " + key)));
    }

    bindings.put(key, target);

    return this;
  }

  public Map<Key<?>, TypeLiteral<?>> getBindings() {
    return Collections.unmodifiableMap(bindings);
  }
}
