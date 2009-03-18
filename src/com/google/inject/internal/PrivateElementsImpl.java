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

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Binder;
import com.google.inject.internal.BindingBuilder.ExposureBuilder;
import static com.google.inject.internal.Preconditions.checkNotNull;
import static com.google.inject.internal.Preconditions.checkState;
import com.google.inject.spi.Element;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.PrivateElements;
import java.util.List;
import java.util.Set;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class PrivateElementsImpl implements PrivateElements {

  /*
   * This class acts as both a value object and as a builder. When getElements() is called, an
   * immutable collection of elements is constructed and the original mutable list is nulled out.
   * Similarly, the exposed keys are made immutable on access.
   */

  private final Object source;

  private List<Element> elementsMutable = Lists.newArrayList();
  private List<ExposureBuilder<?>> exposureBuilders = Lists.newArrayList();

  /** lazily instantiated */
  private ImmutableList<Element> elements;

  /** lazily instantiated */
  private ImmutableSet<Key<?>> exposedKeys;
  private Injector injector;

  public PrivateElementsImpl(Object source) {
    this.source = checkNotNull(source, "source");
  }

  public Object getSource() {
    return source;
  }

  public List<Element> getElements() {
    if (elements == null) {
      elements = ImmutableList.copyOf(elementsMutable);
      elementsMutable = null;
    }

    return elements;
  }

  public Injector getInjector() {
    return injector;
  }

  public void initInjector(Injector injector) {
    checkState(this.injector == null, "injector already initialized");
    this.injector = checkNotNull(injector, "injector");
  }

  public Set<Key<?>> getExposedKeys() {
    if (exposedKeys == null) {
      Set<Key<?>> exposedKeysMutable = Sets.newLinkedHashSet();
      for (ExposureBuilder<?> exposureBuilder : exposureBuilders) {
        exposedKeysMutable.add(exposureBuilder.getKey());
      }
      exposedKeys = ImmutableSet.copyOf(exposedKeysMutable);
      exposureBuilders = null;
    }

    return exposedKeys;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public List<Element> getElementsMutable() {
    return elementsMutable;
  }

  public void addExposureBuilder(ExposureBuilder<?> exposureBuilder) {
    exposureBuilders.add(exposureBuilder);
  }

  public void applyTo(Binder binder) {
    throw new UnsupportedOperationException("TODO");
  }
}
