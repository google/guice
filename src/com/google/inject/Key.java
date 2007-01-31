/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import static com.google.inject.util.Objects.nonNull;

import java.lang.reflect.Type;

/**
 * Binding key consisting of a type and a name. Matches the type and
 * name ({@link Inject#value()}) at a point of injection.
 *
 * <p>For example, <tt>new Key&lt;List&lt;String>>("cities") {}</tt> will match:
 *
 * <pre>
 *   &#64;Inject("cities")
 *   public void setList(List&lt;String> cities) {
 *     ...
 *   }
 * </pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class Key<T> {

  /**
   * Default binding name.
   */
  public static final String DEFAULT_NAME = "default";

  final String name;
  final TypeLiteral<T> typeLiteral;
  final int hashCode;

  /**
   * Constructs a new key. Derives the type from this class's type parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute
   * it at runtime despite erasure.
   *
   * <p>Example usage for a binding of type {@code Foo} named "bar":
   * {@code new Key<Foo>("bar") {}}.
   */
  @SuppressWarnings({"unchecked"})
  protected Key(String name) {
    this.name = nonNull(name, "name");
    this.typeLiteral =
        (TypeLiteral<T>) TypeLiteral.fromSuperclassTypeParameter(getClass());
    this.hashCode = computeHashCode();
  }

  /**
   * Convenience method. Delegates to {@link #Key(String)} with
   * {@link #DEFAULT_NAME}.
   */
  protected Key() {
    this(DEFAULT_NAME);
  }

  /**
   * Unsafe. Constructs a key from a manually specified type.
   */
  @SuppressWarnings({"unchecked"})
  private Key(Type type, String name) {
    this.name = nonNull(name, "name");
    this.typeLiteral = (TypeLiteral<T>) TypeLiteral.get(type);
    this.hashCode = computeHashCode();
  }

  /**
   * Constructs a key from a manually specified type.
   */
  private Key(TypeLiteral<T> typeLiteral, String name) {
    this.name = nonNull(name, "name");
    this.typeLiteral = typeLiteral;
    this.hashCode = computeHashCode();
  }

  private int computeHashCode() {
    return typeLiteral.hashCode() * 31 + name.hashCode();
  }

  /**
   * Returns {@code true} if this key has the default name.
   */
  public boolean hasDefaultName() {
    return DEFAULT_NAME.equals(this.name);
  }

  /**
   * Returns a new key with the same type as this key and the given name,
   */
  Key<T> named(String name) {
    return new SimpleKey<T>(this.typeLiteral, name);
  }

  /**
   * Gets the key type.
   */
  public TypeLiteral<T> getType() {
    return typeLiteral;
  }

  /**
   * Gets the binding name.
   */
  public String getName() {
    return name;
  }

  public int hashCode() {
    return this.hashCode;
  }

  Class<? super T> getRawType() {
    return typeLiteral.getRawType();
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Key<?>)) {
      return false;
    }
    Key<?> other = (Key<?>) o;
    return name.equals(other.name) && typeLiteral.equals(other.typeLiteral);
  }

  public String toString() {
    return Key.class.getSimpleName()
        + "[type=" + typeLiteral + ", name='" + name + "']";
  }

  /**
   * Gets a key for a {@code Class}. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static <T> Key<T> get(Class<T> type) {
    return new SimpleKey<T>(type, DEFAULT_NAME);
  }

  /**
   * Gets a key for a {@code Class} and a name.
   */
  public static <T> Key<T> get(Class<T> type, String name) {
    return new SimpleKey<T>(type, name);
  }

  /**
   * Gets a key for a type. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static Key<?> get(Type type) {
    return new SimpleKey<Object>(type, DEFAULT_NAME);
  }

  /**
   * Gets a key for a type and a name.
   */
  public static Key<?> get(Type type, String name) {
    return new SimpleKey<Object>(type, name);
  }

  /**
   * Gets a key for a type. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static <T> Key<T> get(TypeLiteral<T> typeLiteral) {
    return new SimpleKey<T>(typeLiteral, DEFAULT_NAME);
  }

  /**
   * Gets key for a type and a name.
   */
  public static <T> Key<T> get(TypeLiteral<T> typeLiteral, String name) {
    return new SimpleKey<T>(typeLiteral, name);
  }

  private static class SimpleKey<T> extends Key<T> {

    private SimpleKey(Type type, String name) {
      super(type, name);
    }

    private SimpleKey(TypeLiteral<T> typeLiteral, String name) {
      super(typeLiteral, name);
    }
  }
}
