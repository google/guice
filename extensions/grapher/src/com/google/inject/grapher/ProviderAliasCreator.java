/**
 * Copyright (C) 2011 Google Inc.
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

package com.google.inject.grapher;

import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.spi.ProviderBinding;
import java.util.List;

/**
 * Alias creator that creates an alias for each {@link ProviderBinding}. These {@link Binding}s
 * arise from an {@link InjectionPoint} for the {@link Provider} interface. Since this isn't
 * very interesting information, we don't render this binding on the graph, and just alias the two
 * nodes.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 */
final class ProviderAliasCreator implements AliasCreator {
  @Override public Iterable<Alias> createAliases(Iterable<Binding<?>> bindings) {
    List<Alias> aliases = Lists.newArrayList();
    for (Binding<?> binding : bindings) {
      if (binding instanceof ProviderBinding) {
        aliases.add(new Alias(NodeId.newTypeId(binding.getKey()),
            NodeId.newTypeId(((ProviderBinding<?>) binding).getProvidedKey())));
      }
    }
    return aliases;
  }
}
