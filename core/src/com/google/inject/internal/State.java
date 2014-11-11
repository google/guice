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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.ProvisionListenerBinding;
import com.google.inject.spi.ScopeBinding;
import com.google.inject.spi.TypeConverterBinding;
import com.google.inject.spi.TypeListenerBinding;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The inheritable data within an injector. This class is intended to allow parent and local
 * injector data to be accessed as a unit.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
interface State {

  static final State NONE = new State() {
    public State parent() {
      throw new UnsupportedOperationException();
    }

    public <T> BindingImpl<T> getExplicitBinding(Key<T> key) {
      return null;
    }

    public Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel() {
      throw new UnsupportedOperationException();
    }

    public void putBinding(Key<?> key, BindingImpl<?> binding) {
      throw new UnsupportedOperationException();
    }

    public ScopeBinding getScopeBinding(Class<? extends Annotation> scopingAnnotation) {
      return null;
    }

    public void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope) {
      throw new UnsupportedOperationException();
    }

    public void addConverter(TypeConverterBinding typeConverterBinding) {
      throw new UnsupportedOperationException();
    }

    public TypeConverterBinding getConverter(String stringValue, TypeLiteral<?> type, Errors errors,
        Object source) {
      throw new UnsupportedOperationException();
    }

    public Iterable<TypeConverterBinding> getConvertersThisLevel() {
      return ImmutableSet.of();
    }

    /*if[AOP]*/
    public void addMethodAspect(MethodAspect methodAspect) {
      throw new UnsupportedOperationException();
    }

    public ImmutableList<MethodAspect> getMethodAspects() {
      return ImmutableList.of();
    }
    /*end[AOP]*/

    public void addTypeListener(TypeListenerBinding typeListenerBinding) {
      throw new UnsupportedOperationException();
    }

    public List<TypeListenerBinding> getTypeListenerBindings() {
      return ImmutableList.of();
    }
    
    public void addProvisionListener(ProvisionListenerBinding provisionListenerBinding) {
      throw new UnsupportedOperationException();
    }
    
    public List<ProvisionListenerBinding> getProvisionListenerBindings() {
      return ImmutableList.of();
    }

    public void blacklist(Key<?> key, State state, Object source) {
    }

    public boolean isBlacklisted(Key<?> key) {
      return true;
    }
    
    public Set<Object> getSourcesForBlacklistedKey(Key<?> key) {
      throw new UnsupportedOperationException();
    }

    public Object lock() {
      throw new UnsupportedOperationException();
    }

    public Object singletonCreationLock() {
      throw new UnsupportedOperationException();
    }

    public Map<Class<? extends Annotation>, Scope> getScopes() {
      return ImmutableMap.of();
    }
  };

  State parent();

  /** Gets a binding which was specified explicitly in a module, or null. */
  <T> BindingImpl<T> getExplicitBinding(Key<T> key);

  /** Returns the explicit bindings at this level only. */
  Map<Key<?>, Binding<?>> getExplicitBindingsThisLevel();

  void putBinding(Key<?> key, BindingImpl<?> binding);
 
  ScopeBinding getScopeBinding(Class<? extends Annotation> scopingAnnotation);

  void putScopeBinding(Class<? extends Annotation> annotationType, ScopeBinding scope);

  void addConverter(TypeConverterBinding typeConverterBinding);

  /** Returns the matching converter for {@code type}, or null if none match. */
  TypeConverterBinding getConverter(
      String stringValue, TypeLiteral<?> type, Errors errors, Object source);

  /** Returns all converters at this level only. */
  Iterable<TypeConverterBinding> getConvertersThisLevel();

  /*if[AOP]*/
  void addMethodAspect(MethodAspect methodAspect);

  ImmutableList<MethodAspect> getMethodAspects();
  /*end[AOP]*/

  void addTypeListener(TypeListenerBinding typeListenerBinding);
  
  List<TypeListenerBinding> getTypeListenerBindings();
  
  void addProvisionListener(ProvisionListenerBinding provisionListenerBinding);
  
  List<ProvisionListenerBinding> getProvisionListenerBindings();

  /**
   * Forbids the corresponding injector from creating a binding to {@code key}. Child injectors
   * blacklist their bound keys on their parent injectors to prevent just-in-time bindings on the
   * parent injector that would conflict and pass along their state to control the lifetimes.
   */
  void blacklist(Key<?> key, State state, Object source);

  /**
   * Returns true if {@code key} is forbidden from being bound in this injector. This indicates that
   * one of this injector's descendent's has bound the key.
   */
  boolean isBlacklisted(Key<?> key);
  
  /** Returns the source of a blacklisted key. */
  Set<Object> getSourcesForBlacklistedKey(Key<?> key);

  /**
   * Returns the shared lock for all injector data. This is a low-granularity, high-contention lock
   * to be used when reading mutable data (ie. just-in-time bindings, and binding blacklists).
   */
  Object lock();

  /**
   * Returns the shared lock for all injector's singletons. This is a low-granularity lock
   * to guarantee singleton creation semantics.
   */
  Object singletonCreationLock();

  /**
   * Returns all the scope bindings at this level and parent levels.
   */
  Map<Class<? extends Annotation>, Scope> getScopes();
}
