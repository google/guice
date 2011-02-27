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

import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
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
  private final Map<Class<? extends Annotation>, Scope> scopes = Maps.newHashMap();
  private final List<TypeConverterBinding> converters = Lists.newArrayList();
  /*if[AOP]*/
  private final List<MethodAspect> methodAspects = Lists.newArrayList();
  /*end[AOP]*/
  private final List<TypeListenerBinding> listenerBindings = Lists.newArrayList();
  private final WeakKeySet blacklistedKeys = new WeakKeySet();
  private final Object lock;

  InheritingState(State parent) {
    this.parent = checkNotNull(parent, "parent");
    this.lock = (parent == State.NONE) ? this : parent.lock();
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

  public Scope getScope(Class<? extends Annotation> annotationType) {
    Scope scope = scopes.get(annotationType);
    return scope != null ? scope : parent.getScope(annotationType);
  }

  public void putAnnotation(Class<? extends Annotation> annotationType, Scope scope) {
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
    listenerBindings.add(listenerBinding);
  }

  public List<TypeListenerBinding> getTypeListenerBindings() {
    List<TypeListenerBinding> parentBindings = parent.getTypeListenerBindings();
    List<TypeListenerBinding> result
        = new ArrayList<TypeListenerBinding>(parentBindings.size() + 1);
    result.addAll(parentBindings);
    result.addAll(listenerBindings);
    return result;
  }

  public void blacklist(Key<?> key, Object source) {
    parent.blacklist(key, source);
    blacklistedKeys.add(key, source);
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
    return scopes;
  }
}
