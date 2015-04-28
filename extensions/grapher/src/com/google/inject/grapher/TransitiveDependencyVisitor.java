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

package com.google.inject.grapher;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;

import java.util.Collection;
import java.util.Set;

/**
 * {@link com.google.inject.spi.BindingTargetVisitor} that returns a
 * {@link Collection} of the {@link Key}s of each {@link Binding}'s
 * dependencies. Used by {@link InjectorGrapher} to walk the dependency graph
 * from a starting set of {@link Binding}s.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class TransitiveDependencyVisitor
    extends DefaultBindingTargetVisitor<Object, Collection<Key<?>>> {

  private Collection<Key<?>> visitHasDependencies(HasDependencies hasDependencies) {
    Set<Key<?>> dependencies = Sets.newHashSet();
    
    for (Dependency<?> dependency : hasDependencies.getDependencies()) {
      dependencies.add(dependency.getKey());
    }

    return dependencies;
  }
  
  @Override public Collection<Key<?>> visit(ConstructorBinding<?> binding) {
    return visitHasDependencies(binding);
  }

  @Override public Collection<Key<?>> visit(ConvertedConstantBinding<?> binding) {
    return visitHasDependencies(binding);
  }

  @Override public Collection<Key<?>> visit(InstanceBinding<?> binding) {
    return visitHasDependencies(binding);
  }

  @Override public Collection<Key<?>> visit(LinkedKeyBinding<?> binding) {
    return ImmutableSet.<Key<?>>of(binding.getLinkedKey());
  }

  @Override public Collection<Key<?>> visit(ProviderBinding<?> binding) {
    return ImmutableSet.<Key<?>>of(binding.getProvidedKey());
  }

  @Override public Collection<Key<?>> visit(ProviderInstanceBinding<?> binding) {
    return visitHasDependencies(binding);
  }

  @Override public Collection<Key<?>> visit(ProviderKeyBinding<?> binding) {
    return ImmutableSet.<Key<?>>of(binding.getProviderKey());
  }

  /** @since 4.0 */
  @Override public Collection<Key<?>> visitOther(Binding<?> binding) {
    return ImmutableSet.of();
  }
}
