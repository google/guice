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

package com.google.inject.multibindings;

import com.google.common.base.Objects;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.spi.InjectionPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A mutable annotation implementation with non-conforming {@link #equals} and {@link #hashCode}
 * methods.
 * 
 * <p>Annotation equality is defined based on the binding it is used in, letting Guice spot and
 * remove duplicate bindings without false conflicts. However, as the binding is mutable, so is
 * the annotation.
 * 
 * @author chrispurcell@google.com (Chris Purcell)
 */
class RealElement implements Element {
  private static final AtomicInteger nextUniqueId = new AtomicInteger(1);
  
  /**
   * Adds a new binding to a multibindings collection, returning its {@link BindingBuilder}.
   * 
   * @param binder the current Guice binder
   * @param type whether the collection is a set or a map
   * @param elementType the type of element stored in the collection
   * @param setName the string used internally to identify the collection
   */
  static <T> BindingBuilder<T> addBinding(
      Binder binder, Element.Type type, TypeLiteral<T> elementType, String setName) {
    RealElement annotation = new RealElement(setName, type, null);
    LinkedBindingBuilder<T> delegate = binder
        .skipSources(RealElement.class)
        .bind(Key.get(elementType, annotation));
    return new BindingBuilder<T>(annotation, delegate);
  }
  
  /**
   * Adds a new map value binding to a multibindings collection, returning its
   * {@link BindingBuilder}.
   * 
   * @param binder the current Guice binder
   * @param mapKey the key used to fetch the element from the map
   * @param elementType the type of element stored in the collection
   * @param setName the string used internally to identify the collection
   */
  static <T> BindingBuilder<T> addMapBinding(
      Binder binder, Object mapKey, TypeLiteral<T> elementType, String setName) {
    RealElement annotation = new RealElement(setName, Element.Type.MAPBINDER, mapKey);
    LinkedBindingBuilder<T> delegate = binder
        .skipSources(RealElement.class)
        .bind(Key.get(elementType, annotation));
    return new BindingBuilder<T>(annotation, delegate);
  }
  
  private final int uniqueId;
  private final String setName;
  private final Element.Type type;
  private final Object mapKey;
  private TargetType targetType = TargetType.UNTARGETTED;
  private Object target = null;
  private Object scope = Scopes.NO_SCOPE;

  private RealElement(String setName, Element.Type type, Object mapKey) {
    this.uniqueId = nextUniqueId.incrementAndGet();
    this.setName = setName;
    this.type = type;
    this.mapKey = mapKey;
  }
  
  public String setName() {
    return setName;
  }
  
  public int uniqueId() {
    return uniqueId;
  }
  
  public Element.Type type() {
    return type;
  }

  public Class<? extends Annotation> annotationType() {
    return Element.class;
  }
  
  @Override public String toString() {
    return String.format("@%s(setName=%s, uniqueId=%d, type=%s)",
        annotationType().getName(), setName, uniqueId, type);
  }

  /**
   * Returns true if the given object is a {@link RealElement} associated with a binding that
   * is considered a duplicate of the one associated with this object. Specifically, that means all
   * the following must hold:
   * <ul>
   * <li>the bindings are from the same collection (as determined by {@link #setName} and
   *     {@link #type})
   * <li>the bindings are in the same scope
   * <li>the target types ("instance", "linked key", etc.) match
   * <li>the targets themselves (the instance, the linked key, etc.) are equal
   * </ul>
   * 
   * <p>Note that this means the definition of equality can change if a bound instance changes.
   * 
   * <p>This <b>does not</b> match the definition of {@link Annotation#equals}. However, as
   * these annotations will only ever be used within Guice, and {@link Element} itself is package
   * private and will never be used as an annotation, this should not cause problems.
   */
  @Override public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RealElement other = (RealElement) obj;
    return (setName.equals(other.setName)
        && type.equals(other.type)
        && Objects.equal(mapKey, other.mapKey)
        && scope.equals(other.scope)
        && targetType.equals(other.targetType)
        && Objects.equal(target, other.target));
  }
  
  /**
   * Returns the hash code of this annotation. This depends on the binding the annotation is in.
   * 
   * <p>This <b>does not</b> match the definition of {@link Annotation#hashCode}. However, as
   * these annotations will only ever be used within Guice, and {@link Element} itself is package
   * private and will never be used as an annotation, this should not cause problems.
   */
  @Override public int hashCode() {
    return Objects.hashCode(setName, type, mapKey, scope, targetType, target);
  }

  private enum TargetType {
    INSTANCE, PROVIDER_INSTANCE, PROVIDER_KEY, LINKED_KEY, UNTARGETTED, CONSTRUCTOR
  }
  
  private static final Object EAGER_SINGLETON = new Object();

  static class BindingBuilder<T> implements LinkedBindingBuilder<T> {
    private final RealElement annotation;
    private final LinkedBindingBuilder<T> delegate;

    BindingBuilder(RealElement annotation, LinkedBindingBuilder<T> delegate) {
      this.annotation = annotation;
      this.delegate = delegate;
    }
    
    RealElement getAnnotation() {
      return annotation;
    }

    public void in(Class<? extends Annotation> scopeAnnotation) {
      delegate.in(scopeAnnotation);
      annotation.scope = scopeAnnotation;
    }

    public void in(Scope scope) {
      delegate.in(scope);
      annotation.scope = scope;
    }

    public void asEagerSingleton() {
      delegate.asEagerSingleton();
      annotation.scope = EAGER_SINGLETON;
    }

    public ScopedBindingBuilder to(Class<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(TypeLiteral<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(Key<? extends T> targetKey) {
      delegate.to(targetKey);
      annotation.targetType = TargetType.LINKED_KEY;
      annotation.target = targetKey;
      return this;
    }

    public void toInstance(T instance) {
      delegate.toInstance(instance);
      annotation.scope = EAGER_SINGLETON;
      annotation.targetType = TargetType.INSTANCE;
      annotation.target = instance;
    }

    public ScopedBindingBuilder toProvider(Provider<? extends T> provider) {
      return toProvider((javax.inject.Provider<T>) provider);
    }

    public ScopedBindingBuilder toProvider(javax.inject.Provider<? extends T> provider) {
      delegate.toProvider(provider);
      annotation.targetType = TargetType.PROVIDER_INSTANCE;
      annotation.target = provider;
      return this;
    }

    public ScopedBindingBuilder toProvider(
        Class<? extends javax.inject.Provider<? extends T>> providerType) {
      return toProvider(Key.get(providerType));
    }

    public ScopedBindingBuilder toProvider(
        TypeLiteral<? extends javax.inject.Provider<? extends T>> providerType) {
      return toProvider(Key.get(providerType));
    }

    public ScopedBindingBuilder toProvider(
        Key<? extends javax.inject.Provider<? extends T>> providerKey) {
      delegate.toProvider(providerKey);
      annotation.targetType = TargetType.PROVIDER_KEY;
      annotation.target = providerKey;
      return this;
    }

    public <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor) {
      return toConstructor(constructor, TypeLiteral.get(constructor.getDeclaringClass()));
    }

    public <S extends T> ScopedBindingBuilder toConstructor(
        Constructor<S> constructor, TypeLiteral<? extends S> type) {
      delegate.toConstructor(constructor, type);
      annotation.targetType = TargetType.CONSTRUCTOR;
      annotation.target = InjectionPoint.forConstructor(constructor, type);
      return this;
    }
  }
}
