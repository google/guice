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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/**
 * This class contains static utility methods that operate on or return objects
 * of type {@code Iterator}. Also see the parallel implementations in {@link
 * Iterables}.
 *
 * @author Kevin Bourrillion
 * @author Scott Bonneau
 */
public final class Iterators {
  private Iterators() {}

  static final Iterator<Object> EMPTY_ITERATOR
      = new UnmodifiableIterator<Object>() {
        public boolean hasNext() {
          return false;
        }
        public Object next() {
          throw new NoSuchElementException();
        }
      };


  /** Returns the empty {@code Iterator}. */
  // Casting to any type is safe since there are no actual elements.
  @SuppressWarnings("unchecked")
  public static <T> UnmodifiableIterator<T> emptyIterator() {
    return (UnmodifiableIterator<T>) EMPTY_ITERATOR;
  }

  private static final ListIterator<Object> EMPTY_LIST_ITERATOR =
      new ListIterator<Object>() {
        public boolean hasNext() {
          return false;
        }
        public boolean hasPrevious() {
          return false;
        }
        public int nextIndex() {
          return 0;
        }
        public int previousIndex() {
          return -1;
        }
        public Object next() {
          throw new NoSuchElementException();
        }
        public Object previous() {
          throw new NoSuchElementException();
        }
        public void set(Object o) {
          throw new UnsupportedOperationException();
        }
        public void add(Object o) {
          throw new UnsupportedOperationException();
        }
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };

  /** Returns the empty {@code ListIterator}. */
  // Casting to any type is safe since there are no actual elements.
  @SuppressWarnings("unchecked")
  public static <T> ListIterator<T> emptyListIterator() {
    return (ListIterator<T>) EMPTY_LIST_ITERATOR;
  }

  /** Returns an unmodifiable view of {@code iterator}. */
  public static <T> UnmodifiableIterator<T> unmodifiableIterator(
      final Iterator<T> iterator) {
    Preconditions.checkNotNull(iterator);
    return new UnmodifiableIterator<T>() {
      public boolean hasNext() {
        return iterator.hasNext();
      }
      public T next() {
        return iterator.next();
      }
    };
  }


  /**
   * Returns a string representation of {@code iterator}, with the format
   * {@code [e1, e2, ..., en]}. The iterator will be left exhausted: its
   * {@code hasNext()} method will return {@code false}.
   */
  public static String toString(Iterator<?> iterator) {
    if (!iterator.hasNext()) {
      return "[]";
    }
    StringBuilder builder = new StringBuilder();
    builder.append('[').append(iterator.next());
    while (iterator.hasNext()) {
      builder.append(", ").append(iterator.next());
    }
    return builder.append(']').toString();
  }

  /**
   * Returns the single element contained in {@code iterator}.
   *
   * @throws NoSuchElementException if the iterator is empty
   * @throws IllegalArgumentException if the iterator contains multiple
   *     elements.  The state of the iterator is unspecified.
   */
  public static <T> T getOnlyElement(Iterator<T> iterator) {
    T first = iterator.next();
    if (!iterator.hasNext()) {
      return first;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("expected one element but was: <" + first);
    for (int i = 0; i < 4 && iterator.hasNext(); i++) {
      sb.append(", " + iterator.next());
    }
    if (iterator.hasNext()) {
      sb.append(", ...");
    }
    sb.append(">");

    throw new IllegalArgumentException(sb.toString());
  }

  /**
   * Combines multiple iterators into a single iterator. The returned iterator
   * iterates across the elements of each iterator in {@code inputs}. The input
   * iterators are not polled until necessary.
   *
   * <p>The returned iterator supports {@code remove()} when the corresponding
   * input iterator supports it. The methods of the returned iterator may throw
   * {@code NullPointerException} if any of the input iterators are null.
   */
  public static <T> Iterator<T> concat(
      final Iterator<? extends Iterator<? extends T>> inputs) {
    Preconditions.checkNotNull(inputs);
    return new Iterator<T>() {
      Iterator<? extends T> current = emptyIterator();
      Iterator<? extends T> removeFrom;

      public boolean hasNext() {
        while (!current.hasNext() && inputs.hasNext()) {
          current = inputs.next();
        }
        return current.hasNext();
      }
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        removeFrom = current;
        return current.next();
      }
      public void remove() {
        Preconditions.checkState(removeFrom != null,
            "no calls to next() since last call to remove()");
        removeFrom.remove();
        removeFrom = null;
      }
    };
  }


  /**
   * Returns an iterator that applies {@code function} to each element of {@code
   * fromIterator}.
   *
   * <p>The returned iterator supports {@code remove()} if the provided iterator
   * does. After a successful {@code remove()} call, {@code fromIterator} no
   * longer contains the corresponding element.
   */
  public static <F, T> Iterator<T> transform(final Iterator<F> fromIterator,
      final Function<? super F, ? extends T> function) {
    Preconditions.checkNotNull(fromIterator);
    Preconditions.checkNotNull(function);
    return new Iterator<T>() {
      public boolean hasNext() {
        return fromIterator.hasNext();
      }
      public T next() {
        F from = fromIterator.next();
        return function.apply(from);
      }
      public void remove() {
        fromIterator.remove();
      }
    };
  }

  // Methods only in Iterators, not in Iterables

  /**
   * Returns an iterator containing the elements of {@code array} in order. The
   * returned iterator is a view of the array; subsequent changes to the array
   * will be reflected in the iterator.
   *
   * <p><b>Note:</b> It is often preferable to represent your data using a
   * collection type, for example using {@link Arrays#asList(Object[])}, making
   * this method unnecessary.
   */
  public static <T> UnmodifiableIterator<T> forArray(final T... array) {
    // optimized. benchmarks at nearly 2x of the straightforward impl
    return new UnmodifiableIterator<T>() {
      final int length = array.length;
      int i = 0;
      public boolean hasNext() {
        return i < length;
      }
      public T next() {
        try {
          // 'return array[i++];' almost works
          T t = array[i];
          i++;
          return t;
        } catch (ArrayIndexOutOfBoundsException e) {
          throw new NoSuchElementException();
        }
      }
    };
  }

  /**
   * Returns an iterator containing the elements in the specified range of
   * {@code array} in order. The returned iterator is a view of the array;
   * subsequent changes to the array will be reflected in the iterator.
   *
   * @param array array to read elements out of
   * @param offset index of first array element to retrieve
   * @param length number of elements in iteration
   *
   * @throws IndexOutOfBoundsException if {@code offset} is negative,
   *    {@code length} is negative, or {@code offset + length > array.length}
   */
  public static <T> UnmodifiableIterator<T> forArray(
      final T[] array, final int offset, final int length) {
    Preconditions.checkArgument(length >= 0);
    final int end = offset + length;

    // Technically we should give a slightly more descriptive error on overflow
    Preconditions.checkPositionIndexes(offset, end, array.length);

    // If length == 0 is a common enough case, we could return emptyIterator().

    return new UnmodifiableIterator<T>() {
      int i = offset;
      public boolean hasNext() {
        return i < end;
      }
      public T next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        return array[i++];
      }
    };
  }

  /**
   * Returns an iterator containing only {@code value}.
   */
  public static <T> UnmodifiableIterator<T> singletonIterator(
      @Nullable final T value) {
    return new UnmodifiableIterator<T>() {
      boolean done;
      public boolean hasNext() {
        return !done;
      }
      public T next() {
        if (done) {
          throw new NoSuchElementException();
        }
        done = true;
        return value;
      }
    };
  }

  /**
   * Adapts an {@code Iterator} to the {@code Enumeration} interface.
   *
   * @see Collections#enumeration(Collection)
   */
  public static <T> Enumeration<T> asEnumeration(final Iterator<T> iterator) {
    Preconditions.checkNotNull(iterator);
    return new Enumeration<T>() {
      public boolean hasMoreElements() {
        return iterator.hasNext();
      }
      public T nextElement() {
        return iterator.next();
      }
    };
  }
}
