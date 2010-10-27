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

import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.RandomAccess;

/**
 * A high-performance, immutable, random-access {@code List} implementation.
 * Does not permit null elements.
 *
 * <p>Unlike {@link Collections#unmodifiableList}, which is a <i>view</i> of a
 * separate collection that can still change, an instance of {@code
 * ImmutableList} contains its own private data and will <i>never</i> change.
 * {@code ImmutableList} is convenient for {@code public static final} lists
 * ("constant lists") and also lets you easily make a "defensive copy" of a list
 * provided to your class by a caller.
 *
 * <p><b>Note</b>: Although this class is not final, it cannot be subclassed as
 * it has no public or protected constructors. Thus, instances of this type are
 * guaranteed to be immutable.
 *
 * @see ImmutableMap
 * @see ImmutableSet
 * @author Kevin Bourrillion
 */
@SuppressWarnings("serial") // we're overriding default serialization
public abstract class ImmutableList<E> extends ImmutableCollection<E>
    implements List<E>, RandomAccess {
  private static final ImmutableList<?> EMPTY_IMMUTABLE_LIST
      = new EmptyImmutableList();

  /**
   * Returns the empty immutable list. This set behaves and performs comparably
   * to {@link Collections#emptyList}, and is preferable mainly for consistency
   * and maintainability of your code.
   */
  // Casting to any type is safe because the list will never hold any elements.
  @SuppressWarnings("unchecked")
  public static <E> ImmutableList<E> of() {
    return (ImmutableList<E>) EMPTY_IMMUTABLE_LIST;
  }

  /**
   * Returns an immutable list containing a single element. This list behaves
   * and performs comparably to {@link Collections#singleton}, but will not
   * accept a null element. It is preferable mainly for consistency and
   * maintainability of your code.
   *
   * @throws NullPointerException if {@code element} is null
   */
  public static <E> ImmutableList<E> of(E element) {
    // TODO: evaluate a specialized SingletonImmutableList
    return new RegularImmutableList<E>(copyIntoArray(element));
  }

  // TODO: Add similar overloadings to ImmutableSet and ImmutableSortedSet

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> of(E e1, E e2) {
    return new RegularImmutableList<E>(copyIntoArray(e1, e2));
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> of(E e1, E e2, E e3) {
    return new RegularImmutableList<E>(copyIntoArray(e1, e2, e3));
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4) {
    return new RegularImmutableList<E>(copyIntoArray(e1, e2, e3, e4));
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any element is null
   */
  public static <E> ImmutableList<E> of(E e1, E e2, E e3, E e4, E e5) {
    return new RegularImmutableList<E>(copyIntoArray(e1, e2, e3, e4, e5));
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any of {@code elements} is null
   */
  public static <E> ImmutableList<E> of(E... elements) {
    return (elements.length == 0)
        ? ImmutableList.<E>of()
        : new RegularImmutableList<E>(copyIntoArray(elements));
  }

  /**
   * Returns an immutable list containing the given elements, in order. This
   * method iterates over {@code elements} at most once. Note that if {@code
   * list} is a {@code List<String>}, then {@code ImmutableList.copyOf(list)}
   * returns an {@code ImmutableList<String>} containing each of the strings
   * in {@code list}, while ImmutableList.of(list)} returns an {@code
   * ImmutableList<List<String>>} containing one element (the given list
   * itself).
   *
   * <p><b>Note:</b> Despite what the method name suggests, if {@code elements}
   * is an {@code ImmutableList}, no copy will actually be performed, and the
   * given list itself will be returned.
   *
   * @throws NullPointerException if any of {@code elements} is null
   */
  public static <E> ImmutableList<E> copyOf(Iterable<? extends E> elements) {
    if (elements instanceof ImmutableList) {
      /*
       * TODO: If the given ImmutableList is a sublist, copy the referenced
       * portion of the array into a new array to save space?
       */
      @SuppressWarnings("unchecked") // all supported methods are covariant
      ImmutableList<E> list = (ImmutableList<E>) elements;
      return list;
    } else if (elements instanceof Collection) {
      @SuppressWarnings("unchecked")
      Collection<? extends E> coll = (Collection<? extends E>) elements;
      return copyOfInternal(coll);
    } else {
      return copyOfInternal(Lists.newArrayList(elements));
    }
  }

  /**
   * Returns an immutable list containing the given elements, in order.
   *
   * @throws NullPointerException if any of {@code elements} is null
   */
  public static <E> ImmutableList<E> copyOf(Iterator<? extends E> elements) {
    return copyOfInternal(Lists.newArrayList(elements));
  }

  private static <E> ImmutableList<E> copyOfInternal(
      ArrayList<? extends E> list) {
    return (list.isEmpty())
        ? ImmutableList.<E>of()
        : new RegularImmutableList<E>(nullChecked(list.toArray()));
  }

  /**
   * Checks that all the array elements are non-null.
   *
   * @return the argument array
   * @throws NullPointerException if any element is null
   */
  private static Object[] nullChecked(Object[] array) {
    for (int i = 0, len = array.length; i < len; i++) {
      if (array[i] == null) {
        throw new NullPointerException("at index " + i);
      }
    }
    return array;
  }

  private static <E> ImmutableList<E> copyOfInternal(
      Collection<? extends E> collection) {
    int size = collection.size();
    return (size == 0)
        ? ImmutableList.<E>of()
        : ImmutableList.<E>createFromIterable(collection, size);
  }

  private ImmutableList() {}

  // This declaration is needed to make List.iterator() and
  // ImmutableCollection.iterator() consistent.
  @Override public abstract UnmodifiableIterator<E> iterator();

  // Mark these two methods with @Nullable

  public abstract int indexOf(@Nullable Object object);

  public abstract int lastIndexOf(@Nullable Object object);

  // constrain the return type to ImmutableList<E>

  /**
   * Returns an immutable list of the elements between the specified {@code
   * fromIndex}, inclusive, and {@code toIndex}, exclusive. (If {@code
   * fromIndex} and {@code toIndex} are equal, the empty immutable list is
   * returned.)
   */
  public abstract ImmutableList<E> subList(int fromIndex, int toIndex);

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final boolean addAll(int index, Collection<? extends E> newElements) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final E set(int index, E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final void add(int index, E element) {
    throw new UnsupportedOperationException();
  }

  /**
   * Guaranteed to throw an exception and leave the list unmodified.
   *
   * @throws UnsupportedOperationException always
   */
  public final E remove(int index) {
    throw new UnsupportedOperationException();
  }

  private static final class EmptyImmutableList extends ImmutableList<Object> {
    public int size() {
      return 0;
    }

    @Override public boolean isEmpty() {
      return true;
    }

    @Override public boolean contains(Object target) {
      return false;
    }

    @Override public UnmodifiableIterator<Object> iterator() {
      return Iterators.emptyIterator();
    }

    private static final Object[] EMPTY_ARRAY = new Object[0];

    @Override public Object[] toArray() {
      return EMPTY_ARRAY;
    }

    @Override public <T> T[] toArray(T[] a) {
      if (a.length > 0) {
        a[0] = null;
      }
      return a;
    }

    public Object get(int index) {
      // guaranteed to fail, but at least we get a consistent message
      Preconditions.checkElementIndex(index, 0);
      throw new AssertionError("unreachable");
    }

    @Override public int indexOf(Object target) {
      return -1;
    }

    @Override public int lastIndexOf(Object target) {
      return -1;
    }

    @Override public ImmutableList<Object> subList(int fromIndex, int toIndex) {
      Preconditions.checkPositionIndexes(fromIndex, toIndex, 0);
      return this;
    }

    public ListIterator<Object> listIterator() {
      return Iterators.emptyListIterator();
    }

    public ListIterator<Object> listIterator(int start) {
      Preconditions.checkPositionIndex(start, 0);
      return Iterators.emptyListIterator();
    }

    @Override public boolean containsAll(Collection<?> targets) {
      return targets.isEmpty();
    }

    @Override public boolean equals(@Nullable Object object) {
      if (object instanceof List) {
        List<?> that = (List<?>) object;
        return that.isEmpty();
      }
      return false;
    }

    @Override public int hashCode() {
      return 1;
    }

    @Override public String toString() {
      return "[]";
    }
  }

  private static final class RegularImmutableList<E> extends ImmutableList<E> {
    private final int offset;
    private final int size;
    private final Object[] array;

    private RegularImmutableList(Object[] array, int offset, int size) {
      this.offset = offset;
      this.size = size;
      this.array = array;
    }

    private RegularImmutableList(Object[] array) {
      this(array, 0, array.length);
    }

    public int size() {
      return size;
    }

    @Override public boolean isEmpty() {
      return false;
    }

    @Override public boolean contains(Object target) {
      return indexOf(target) != -1;
    }

    // The fake cast to E is safe because the creation methods only allow E's
    @SuppressWarnings("unchecked")
    @Override public UnmodifiableIterator<E> iterator() {
      return (UnmodifiableIterator<E>) Iterators.forArray(array, offset, size);
    }

    @Override public Object[] toArray() {
      Object[] newArray = new Object[size()];
      System.arraycopy(array, offset, newArray, 0, size);
      return newArray;
    }

    @Override public <T> T[] toArray(T[] other) {
      if (other.length < size) {
        other = ObjectArrays.newArray(other, size);
      } else if (other.length > size) {
        other[size] = null;
      }
      System.arraycopy(array, offset, other, 0, size);
      return other;
    }

    // The fake cast to E is safe because the creation methods only allow E's
    @SuppressWarnings("unchecked")
    public E get(int index) {
      Preconditions.checkElementIndex(index, size);
      return (E) array[index + offset];
    }

    @Override public int indexOf(Object target) {
      if (target != null) {
        for (int i = offset; i < offset + size; i++) {
          if (array[i].equals(target)) {
            return i - offset;
          }
        }
      }
      return -1;
    }

    @Override public int lastIndexOf(Object target) {
      if (target != null) {
        for (int i = offset + size - 1; i >= offset; i--) {
          if (array[i].equals(target)) {
            return i - offset;
          }
        }
      }
      return -1;
    }

    @Override public ImmutableList<E> subList(int fromIndex, int toIndex) {
      Preconditions.checkPositionIndexes(fromIndex, toIndex, size);
      return (fromIndex == toIndex)
          ? ImmutableList.<E>of()
          : new RegularImmutableList<E>(
              array, offset + fromIndex, toIndex - fromIndex);
    }

    public ListIterator<E> listIterator() {
      return listIterator(0);
    }

    public ListIterator<E> listIterator(final int start) {
      Preconditions.checkPositionIndex(start, size);

      return new ListIterator<E>() {
        int index = start;

        public boolean hasNext() {
          return index < size;
        }
        public boolean hasPrevious() {
          return index > 0;
        }

        public int nextIndex() {
          return index;
        }
        public int previousIndex() {
          return index - 1;
        }

        public E next() {
          E result;
          try {
            result = get(index);
          } catch (IndexOutOfBoundsException rethrown) {
            throw new NoSuchElementException();
          }
          index++;
          return result;
        }
        public E previous() {
          E result;
          try {
            result = get(index - 1);
          } catch (IndexOutOfBoundsException rethrown) {
            throw new NoSuchElementException();
          }
          index--;
          return result;
        }

        public void set(E o) {
          throw new UnsupportedOperationException();
        }
        public void add(E o) {
          throw new UnsupportedOperationException();
        }
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Override public boolean equals(@Nullable Object object) {
      if (object == this) {
        return true;
      }
      if (!(object instanceof List)) {
        return false;
      }

      List<?> that = (List<?>) object;
      if (this.size() != that.size()) {
        return false;
      }

      int index = offset;
      if (object instanceof RegularImmutableList) {
        RegularImmutableList<?> other = (RegularImmutableList<?>) object;
        for (int i = other.offset; i < other.offset + other.size; i++) {
          if (!array[index++].equals(other.array[i])) {
            return false;
          }
        }
      } else {
        for (Object element : that) {
          if (!array[index++].equals(element)) {
            return false;
          }
        }
      }
      return true;
    }

    @Override public int hashCode() {
      // not caching hash code since it could change if the elements are mutable
      // in a way that modifies their hash codes
      int hashCode = 1;
      for (int i = offset; i < offset + size; i++) {
        hashCode = 31 * hashCode + array[i].hashCode();
      }
      return hashCode;
    }

    @Override public String toString() {
      StringBuilder sb = new StringBuilder(size() * 16);
      sb.append('[').append(array[offset]);
      for (int i = offset + 1; i < offset + size; i++) {
        sb.append(", ").append(array[i]);
      }
      return sb.append(']').toString();
    }
  }

  private static Object[] copyIntoArray(Object... source) {
    Object[] array = new Object[source.length];
    int index = 0;
    for (Object element : source) {
      if (element == null) {
        throw new NullPointerException("at index " + index);
      }
      array[index++] = element;
    }
    return array;
  }

  private static <E> ImmutableList<E> createFromIterable(
      Iterable<?> source, int estimatedSize) {
    Object[] array = new Object[estimatedSize];
    int index = 0;

    for (Object element : source) {
      if (index == estimatedSize) {
        // At least one element was added after our call to size().
        estimatedSize = ((estimatedSize / 2) + 1) * 3;
        array = copyOf(array, estimatedSize);
      }
      if (element == null) {
        throw new NullPointerException("at index " + index);
      }
      array[index++] = element;
    }

    if (index == 0) {
      return of();
    }

    if (index != estimatedSize) {
      array = copyOf(array, index);
    }

    return new RegularImmutableList<E>(array, 0, index);
  }

  // Avoid using Arrays.copyOf(), which is not present until JDK6.
  private static Object[] copyOf(Object[] oldArray, int newSize) {
    Object[] newArray = new Object[newSize];
    System.arraycopy(oldArray, 0, newArray, 0,
        Math.min(oldArray.length, newSize));
    return newArray;
  }

  /*
   * Serializes ImmutableLists as their logical contents. This ensures that
   * implementation types do not leak into the serialized representation.
   */
  private static class SerializedForm implements Serializable {
    final Object[] elements;
    SerializedForm(Object[] elements) {
      this.elements = elements;
    }
    Object readResolve() {
      return of(elements);
    }
    private static final long serialVersionUID = 0;
  }

  private void readObject(ObjectInputStream stream)
      throws InvalidObjectException {
    throw new InvalidObjectException("Use SerializedForm");
  }

  @Override Object writeReplace() {
    return new SerializedForm(toArray());
  }

  /**
   * Returns a new builder. The generated builder is equivalent to the builder
   * created by the {@link Builder} constructor.
   */
  public static <E> Builder<E> builder() {
    return new Builder<E>();
  }

  /**
   * A builder for creating immutable list instances, especially
   * {@code public static final} lists ("constant lists").
   *
   * <p>Example:
   * <pre>{@code
   *   public static final ImmutableList<Color> GOOGLE_COLORS
   *       = new ImmutableList.Builder<Color>()
   *           .addAll(WEBSAFE_COLORS)
   *           .add(new Color(0, 191, 255))
   *           .build();}</pre>
   *
   * <p>Builder instances can be reused - it is safe to call {@link #build}
   * multiple times to build multiple lists in series. Each new list
   * contains the one created before it.
   */
  public static class Builder<E> {
    private final ArrayList<E> contents = Lists.newArrayList();

    /**
     * Creates a new builder. The returned builder is equivalent to the builder
     * generated by {@link ImmutableList#builder}.
     */
    public Builder() {}

    /**
     * Adds {@code element} to the {@code ImmutableList}.
     *
     * @param element the element to add
     * @return this {@code Builder} object
     * @throws NullPointerException if {@code element} is null
     */
    public Builder<E> add(E element) {
      Preconditions.checkNotNull(element, "element cannot be null");
      contents.add(element);
      return this;
    }

    /**
     * Adds each element of {@code elements} to the {@code ImmutableList}.
     *
     * @param elements the {@code Iterable} to add to the {@code ImmutableList}
     * @return this {@code Builder} object
     * @throws NullPointerException if {@code elements} is or contains null
     */
    public Builder<E> addAll(Iterable<? extends E> elements) {
      if (elements instanceof Collection) {
        @SuppressWarnings("unchecked")
        Collection<? extends E> collection = (Collection<? extends E>) elements;
        contents.ensureCapacity(contents.size() + collection.size());
      }
      for (E elem : elements) {
        Preconditions.checkNotNull(elem, "elements contains a null");
        contents.add(elem);
      }
      return this;
    }

    /**
     * Returns a newly-created {@code ImmutableList} based on the contents of
     * the {@code Builder}.
     */
    public ImmutableList<E> build() {
      return copyOf(contents);
    }
  }
}
