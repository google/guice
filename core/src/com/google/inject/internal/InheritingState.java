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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.ModuleAnnotatedMethodScannerBinding;
import com.google.inject.spi.ProvisionListenerBinding;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class InheritingState implements State {

  private final State parent;

  // Must be a linked hashmap in order to preserve order of bindings in Modules.
  private final Map<Key<?>, Binding<?>> explicitBindingsMutable = Maps.newLinkedHashMap();
  private final Map<Key<?>, Binding<?>> explicitBindings
      = Collections.unmodifiableMap(explicitBindingsMutable);
  private final Map<Class<? extends Annotation>, ScopeBinding> scopes = Maps.newHashMap();
  private final List<TypeConverterBinding> converters = Lists.newArrayList();
  /*if[AOP]*/
  private final List<MethodAspect> methodAspects = Lists.newArrayList();
  /*end[AOP]*/
  private final List<TypeListenerBinding> typeListenerBindings = Lists.newArrayList();
  private final List<ProvisionListenerBinding> provisionListenerBindings = Lists.newArrayList();
  private final List<ModuleAnnotatedMethodScannerBinding> scannerBindings = Lists.newArrayList();
  private final WeakKeySet blacklistedKeys;
  private final Object lock;

  InheritingState(State parent) {
    this.parent = checkNotNull(parent, "parent");
    this.lock = (parent == State.NONE) ? this : parent.lock();
    this.blacklistedKeys = new WeakKeySet(lock);
  }

  public State parent() {
    return parent;
  }

  @SuppressWarnings("unchecked") // we only put in BindingImpls that match their key types
  public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
    Binding<?> binding = explicitBindings.get(key);
    return binding != null ? (BindingImpl<T>) binding : parent.getExplicitBinding(key);
  }

  public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
    return explicitBindings;
  }

  public void putBinding(Key<?> key, BindingImpl<?> binding) {
    explicitBindingsMutable.put(key, binding);
  }

  public ScopeBinding getScopeBinding(Class<? extends Annotation> annotationType) {
    ScopeBinding scopeBinding = scopes.get(annotationType);
    return scopeBinding != null ? scopeBinding : parent.getScopeBinding(annotationType);
  }

  public void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope) {
    scopes.put(annotationType, scope);
  }

  public Iterable<TypeConverterBinding> getConvertersThisLevel() {
    return converters;
  }

  public void addConverter(TypeConverterBinding typeConverterBinding) {
    converters.add(typeConverterBinding);
  }

  public TypeConverterBinding getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source) {
    TypeConverterBinding matchingConverter = null;
    for (State s = this; s != State.NONE; s = s.parent()) {
      for (TypeConverterBinding converter : s.getConvertersThisLevel()) {
        if (converter.getTypeMatcher().matches(type)) {
          if (matchingConverter != null) {
            errors.ambiguousTypeConversion(stringValue, source, type, matchingConverter, converter);
          }
          matchingConverter = converter;
        }
      }
    }
    return matchingConverter;
  }

  /*if[AOP]*/
  public void addMethodAspect(MethodAspect methodAspect) {
    methodAspects.add(methodAspect);
  }

  public ImmutableList<MethodAspect> getMethodAspects() {
    return new ImmutableList.Builder<MethodAspect>()
        .addAll(parent.getMethodAspects())
        .addAll(methodAspects)
        .build();
  }
  /*end[AOP]*/

  public void addTypeListener(TypeListenerBinding listenerBinding) {
    typeListenerBindings.add(listenerBinding);
  }

  public List<TypeListenerBinding> getTypeListenerBindings() {
    List<TypeListenerBinding> parentBindings = parent.getTypeListenerBindings();
    List<TypeListenerBinding> result =
        Lists.newArrayListWithCapacity(parentBindings.size() + typeListenerBindings.size());
    result.addAll(parentBindings);
    result.addAll(typeListenerBindings);
    return result;
  }
  
  public void addProvisionListener(ProvisionListenerBinding listenerBinding) {
    provisionListenerBindings.add(listenerBinding);
  }

  public List<ProvisionListenerBinding> getProvisionListenerBindings() {
    List<ProvisionListenerBinding> parentBindings = parent.getProvisionListenerBindings();
    List<ProvisionListenerBinding> result =
        Lists.newArrayListWithCapacity(parentBindings.size() + provisionListenerBindings.size());
    result.addAll(parentBindings);
    result.addAll(provisionListenerBindings);
    return result;
  }

  public void addScanner(ModuleAnnotatedMethodScannerBinding scanner) {
    scannerBindings.add(scanner);
  }

  public List<ModuleAnnotatedMethodScannerBinding> getScannerBindings() {
    List<ModuleAnnotatedMethodScannerBinding> parentBindings = parent.getScannerBindings();
    List<ModuleAnnotatedMethodScannerBinding> result =
        Lists.newArrayListWithCapacity(parentBindings.size() + scannerBindings.size());
    result.addAll(parentBindings);
    result.addAll(scannerBindings);
    return result;
  }

  public void blacklist(Key<?> key, State state, Object source) {
    parent.blacklist(key, state, source);
    blacklistedKeys.add(key, state, source);
  }

  public boolean isBlacklisted(Key<?> key) {
    return blacklistedKeys.contains(key);
  }
  
  public Set<Object> getSourcesForBlacklistedKey(Key<?> key) {
    return blacklistedKeys.getSources(key);
  }

  public Object lock() {
    return lock;
  }

  public Map<Class<? extends Annotation>, Scope> getScopes() {
    ImmutableMap.Builder<Class<? extends Annotation>, Scope> builder = ImmutableMap.builder();
    for (Map.Entry<Class<? extends Annotation>, ScopeBinding> entry : scopes.entrySet()) {
      builder.put(entry.getKey(), entry.getValue().getScope());
    }
    return builder.build();
  }
}
