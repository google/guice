/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.internal.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Static utility methods pertaining to {@link List} instances. Also see this
 * class's counterparts {@link Sets} and {@link Maps}.
 *
 * @author Kevin Bourrillion
 * @author Mike Bostock
 */
public final class Lists {
  private Lists() {}

  // ArrayList

  /**
   * Creates an empty {@code ArrayList} instance.
   *
   * <p><b>Note:</b> if you need an immutable empty list, use {@link
   * Collections#emptyList} instead.
   *
   * @return a new, empty {@code ArrayList}
   */
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<E>();
  }

  /**
   * Creates an {@code ArrayList} instance containing the given elements.
   *
   * <p><b>Note:</b> if you need an immutable List, use {@link ImmutableList}
   * instead.
   *
   * <p><b>Note:</b> due to a bug in javac 1.5.0_06, we cannot support the
   * following:
   *
   * <p>{@code List<Base> list = Lists.newArrayList(sub1, sub2);}
   *
   * <p>where {@code sub1} and {@code sub2} are references to subtypes of {@code
   * Base}, not of {@code Base} itself. To get around this, you must use:
   *
   * <p>{@code List<Base> list = Lists.<Base>newArrayList(sub1, sub2);}
   *
   * @param elements the elements that the list should contain, in order
   * @return a new {@code ArrayList} containing those elements
   */
  public static <E> ArrayList<E> newArrayList(E... elements) {
    // Avoid integer overflow when a large array is passed in
    int capacity = computeArrayListCapacity(elements.length);
    ArrayList<E> list = new ArrayList<E>(capacity);
    Collections.addAll(list, elements);
    return list;
  }

  static int computeArrayListCapacity(int arraySize) {
    Preconditions.checkArgument(arraySize >= 0);

    // TODO: Figure out the right behavior, and document it
    return (int) Math.min(5L + arraySize + (arraySize / 10), Integer.MAX_VALUE);
  }

  /**
   * Creates an {@code ArrayList} instance containing the given elements.
   *
   * @param elements the elements that the list should contain, in order
   * @return a new {@code ArrayList} containing those elements
   */
  public static <E> ArrayList<E> newArrayList(Iterable<? extends E> elements) {
    // Let ArrayList's sizing logic work, if possible
    if (elements instanceof Collection) {
      @SuppressWarnings("unchecked")
      Collection<? extends E> collection = (Collection<? extends E>) elements;
      return new ArrayList<E>(collection);
    } else {
      return newArrayList(elements.iterator());
    }
  }

  /**
   * Creates an {@code ArrayList} instance containing the given elements.
   *
   * @param elements the elements that the list should contain, in order
   * @return a new {@code ArrayList} containing those elements
   */
  public static <E> ArrayList<E> newArrayList(Iterator<? extends E> elements) {
    ArrayList<E> list = newArrayList();
    while (elements.hasNext()) {
      list.add(elements.next());
    }
    return list;
  }


  /**
   * Returns an unmodifiable list containing the specified first element and
   * the additional elements.
   */
  public static <E> ArrayList<E> newArrayList(@Nullable E first, E[] rest) {
    ArrayList<E> result = new ArrayList<E>(rest.length + 1);
    result.add(first);
    for (E element : rest) {
      result.add(element);
    }
    return result;
  }
}