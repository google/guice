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
import java.util.List;
import java.util.Set;

/**
 * A private environment for configuration information.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class PrivateEnvironment implements Element {

  private final Object source;
  List<Element> elementsMutable = Lists.newArrayList();

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
   * Returns the configuration information in this private environment, including the {@link
   * Exposure} elements that make configuration available to the enclosing environment.
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
      for (Element element : getElements()) {
        if (element instanceof Exposure) {
          exposedKeysMutable.add(((Exposure) element).getKey());
        }
      }
      exposedKeys = ImmutableSet.copyOf(exposedKeysMutable);
    }

    return exposedKeys;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visitPrivateElements(this);
  }
}
