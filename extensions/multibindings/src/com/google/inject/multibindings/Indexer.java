/**
 * Copyright (C) 2014 Google Inc.
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

package com.google.inject.multibindings;

import com.google.common.base.Objects;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.UntargettedBinding;

import java.lang.annotation.Annotation;

/**
 * Visits bindings to return a {@code IndexedBinding} that can be used to emulate the binding
 * deduplication that Guice internally performs.
 */
class Indexer extends DefaultBindingTargetVisitor<Object, Indexer.IndexedBinding>
    implements BindingScopingVisitor<Object> {
  enum BindingType {
    INSTANCE,
    PROVIDER_INSTANCE,
    PROVIDER_KEY,
    LINKED_KEY,
    UNTARGETTED,
    CONSTRUCTOR,
    CONSTANT,
    EXPOSED,
    PROVIDED_BY,
  }

  static class IndexedBinding {
    final String annotationName;
    final Element.Type annotationType;
    final TypeLiteral<?> typeLiteral;
    final Object scope;
    final BindingType type;
    final Object extraEquality;

    IndexedBinding(Binding<?> binding, BindingType type, Object scope, Object extraEquality) {
      this.scope = scope;
      this.type = type;
      this.extraEquality = extraEquality;
      this.typeLiteral = binding.getKey().getTypeLiteral();
      Element annotation = (Element) binding.getKey().getAnnotation();
      this.annotationName = annotation.setName();
      this.annotationType = annotation.type();
    }

    @Override public boolean equals(Object obj) {
      if (!(obj instanceof IndexedBinding)) {
        return false;
      }
      IndexedBinding o = (IndexedBinding) obj;
      return type == o.type
          && Objects.equal(scope, o.scope)
          && typeLiteral.equals(o.typeLiteral)
          && annotationType == o.annotationType
          && annotationName.equals(o.annotationName)
          && Objects.equal(extraEquality, o.extraEquality);
    }

    @Override public int hashCode() {
      return Objects.hashCode(type, scope, typeLiteral, annotationType, annotationName,
          extraEquality);
    }
  }

  final Injector injector;

  Indexer(Injector injector) {
    this.injector = injector;
  }

  boolean isIndexable(Binding<?> binding) {
    return binding.getKey().getAnnotation() instanceof Element;
  }

  private Object scope(Binding<?> binding) {
    return binding.acceptScopingVisitor(this);
  }

  @Override public Indexer.IndexedBinding visit(ConstructorBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.CONSTRUCTOR, scope(binding),
        binding.getConstructor());
  }

  @Override public Indexer.IndexedBinding visit(
      ConvertedConstantBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.CONSTANT, scope(binding),
        binding.getValue());
  }

  @Override public Indexer.IndexedBinding visit(ExposedBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.EXPOSED, scope(binding), binding);
  }

  @Override public Indexer.IndexedBinding visit(InstanceBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.INSTANCE, scope(binding),
        binding.getInstance());
  }

  @Override public Indexer.IndexedBinding visit(LinkedKeyBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.LINKED_KEY, scope(binding),
        binding.getLinkedKey());
  }

  @Override public Indexer.IndexedBinding visit(ProviderBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.PROVIDED_BY, scope(binding),
        injector.getBinding(binding.getProvidedKey()));
  }

  @Override public Indexer.IndexedBinding visit(ProviderInstanceBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.PROVIDER_INSTANCE, scope(binding),
        binding.getUserSuppliedProvider());
  }

  @Override public Indexer.IndexedBinding visit(ProviderKeyBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.PROVIDER_KEY, scope(binding),
        binding.getProviderKey());
  }

  @Override public Indexer.IndexedBinding visit(UntargettedBinding<? extends Object> binding) {
    return new Indexer.IndexedBinding(binding, BindingType.UNTARGETTED, scope(binding), null);
  }
  
  private static final Object EAGER_SINGLETON = new Object();
  
  @Override public Object visitEagerSingleton() {
    return EAGER_SINGLETON;
  }

  @Override public Object visitNoScoping() {
    return Scopes.NO_SCOPE;
  }

  @Override public Object visitScope(Scope scope) {
    return scope;
  }

  @Override public Object visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
    return scopeAnnotation;
  }
}
