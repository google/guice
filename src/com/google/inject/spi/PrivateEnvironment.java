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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.inject.internal.ModuleBinding.ExposureBuilder;
import java.util.List;
import java.util.Set;

/**
 * A private environment whose configuration information is hidden from the enclosing environment
 * by default. See {@link com.google.inject.PrivateModule PrivateModule} for details.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class PrivateEnvironment implements Element {

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

  PrivateEnvironment(Object source) {
    this.source = checkNotNull(source, "source");
  }

  public Object getSource() {
    return source;
  }

  /**
   * Returns the configuration information in this private environment.
   */
  public List<Element> getElements() {
    if (elements == null) {
      elements = ImmutableList.copyOf(elementsMutable);
      elementsMutable = null;
    }

    return elements;
  }

  /**
   * Returns the unique exposed keys for these private elements.
   */
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
    return visitor.visitPrivateEnvironment(this);
  }

  List<Element> getElementsMutable() {
    return elementsMutable;
  }

  void addExposureBuilder(ExposureBuilder<?> exposureBuilder) {
    exposureBuilders.add(exposureBuilder);
  }
}
