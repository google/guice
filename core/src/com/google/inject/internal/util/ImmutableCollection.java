/*
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

package com.google.inject.internal.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An immutable collection. Does not permit null elements.
 *
 * <p><b>Note</b>: Although this class is not final, it cannot be subclassed
 * outside of this package as it has no public or protected constructors. Thus,
 * instances of this type are guaranteed to be immutable.
 *
 * @author Jesse Wilson
 */
@SuppressWarnings("serial") // we're overriding default serialization
public abstract class ImmutableCollection<E>
    implements Collection<E>, Serializable {
  static final ImmutableCollection<Object> EMPTY_IMMUTABLE_COLLECTION
      = new EmptyImmutableCollection();

  /** Copied here for GWT compatibility. */
  private static final Object[] EMPTY_ARRAY = new Object[0];
  private static final UnmodifiableIterator<Object> EMPTY_ITERATOR
      = new UnmodifiableIterator<Object>() {
    public boolean hasNext() {
      return false;
    }
    public Object next() {
      throw new NoSuchElementException();
    }
  };

  ImmutableCollection() {}

  /**
   * Returns an unmodifiable iterator across the elements in this collection.
   */
  public abstract UnmodifiableIterator<E> iterator();

  public Object[] toArray() {
    Object[] newArray = new Object[size()];
    return toArray(newArray);
  }

  public <T> T[] toArray(T[] other) {
    int size = size();
    if (other.length < size) {
      other = ObjectArrays.newArray(other, size);
    } else if (other.length > size) {
      other[size] = null;
    }
    int index = 0;
    for (E element : this) {
      /*
       * Sleazy fake cast. However, if element is not a T, then the very next
       * line must fail with an ArrayStoreException, so we should be safe.
       */
      @SuppressWarnings("unchecked")
      T elementAsT = (T) element;

      other[index++] = elementAsT;
    }
    return other;
  }

  public boolean contains(@Nullable Object object) {
    if (object == null) {
      return false;
    }
    for (E element : this) {
      if (element.equals(object)) {
        return true;
      }
    }
    return false;
  }

  public boolean containsAll(Collection<?> targets) {
    for (Object target : targets) {
      if (!contains(target)) {
        return false;
      }
    }
    return true;
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  @Override public String toString() {
    StringBuilder sb = new StringBuilder(size() * 16);
    sb.append('[');
    Iterator<E> i = iterator();
    if (i.hasNext()) {
      sb.append(i.next());
    }
    while (i.hasNext()) {
      sb.append(", ");
      sb.append(i.next());
    }
    return sb.append(']').toString();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean add(E e) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean remove(Object object) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean addAll(Collection<? extends E> newElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean removeAll(Collection<?> oldElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean retainAll(Collection<?> elementsToKeep) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the collection unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final void clear() {
    throw new UnsupportedOperationException();
  }

  private static class EmptyImmutableCollection
      extends ImmutableCollection<Object> {
    public int size() {
      return 0;
    }

    @Override public boolean isEmpty() {
      return true;
    }

    @Override public boolean contains(@Nullable Object object) {
      return false;
    }

    @Override public UnmodifiableIterator<Object> iterator() {
      return EMPTY_ITERATOR;
    }

    @Override public Object[] toArray() {
      return EMPTY_ARRAY;
    }

    @Override public <T> T[] toArray(T[] array) {
      if (array.length > 0) {
        array[0] = null;
      }
      return array;
    }
  }

  private static class ArrayImmutableCollection<E>
      extends ImmutableCollection<E> {
    private final E[] elements;

    ArrayImmutableCollection(E[] elements) {
      this.elements = elements;
    }

    public int size() {
      return elements.length;
    }

    @Override public boolean isEmpty() {
      return false;
    }

    @Override public UnmodifiableIterator<E> iterator() {
      return new UnmodifiableIterator<E>() {
        int i = 0;
        public boolean hasNext() {
          return i < elements.length;
        }
        public E next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          return elements[i++];
        }
      };
    }
  }

  /*
   * Serializes ImmutableCollections as their logical contents. This ensures
   * that implementation types do not leak into the serialized representation.
   */
  private static class SerializedForm implements Serializable {
    final Object[] elements;
    SerializedForm(Object[] elements) {
      this.elements = elements;
    }
    Object readResolve() {
      return elements.length == 0
          ? EMPTY_IMMUTABLE_COLLECTION
          : new ArrayImmutableCollection<Object>(elements.clone());
    }
    private static final long serialVersionUID = 0;
  }

  Object writeReplace() {
    return new SerializedForm(toArray());
  }
}
