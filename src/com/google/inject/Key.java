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
 * Binding key. A type token and a name. Matches the type and name ({@link
 * Inject#value()}) at a point of injection.
 *
 * <p>For example, {@code new Key<List<String>>("names") {}} will match:
 *
 * <pre>
 *   @Inject("names")
 *   public void setList(List<String> list) {
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
  final TypeToken<T> typeToken;
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
    this.typeToken =
        (TypeToken<T>) TypeToken.fromSuperclassTypeParameter(getClass());
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
    this.typeToken = (TypeToken<T>) TypeToken.get(type);
    this.hashCode = computeHashCode();
  }

  /**
   * Constructs a key from a manually specified type token.
   */
  private Key(TypeToken<T> typeToken, String name) {
    this.name = nonNull(name, "name");
    this.typeToken = typeToken;
    this.hashCode = computeHashCode();
  }

  private int computeHashCode() {
    return typeToken.hashCode() * 31 + name.hashCode();
  }

  /**
   * Gets token representing the type to be injected.
   */
  public TypeToken<T> getTypeToken() {
    return typeToken;
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

  @Deprecated
  Class<T> getRawType() {
    return (Class<T>) typeToken.getRawType();
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Key<?>)) {
      return false;
    }
    Key<?> other = (Key<?>) o;
    return name.equals(other.name) && typeToken.equals(other.typeToken);
  }

  public String toString() {
    return Key.class.getSimpleName()
        + "[type=" + typeToken + ", name='" + name + "']";
  }

  /**
   * Gets a key for a {@code Class}. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static <T> Key<T> get(Class<T> type) {
    return new ManualKey<T>(type, DEFAULT_NAME);
  }

  /**
   * Gets a key for a {@code Class} and a name.
   */
  public static <T> Key<T> get(Class<T> type, String name) {
    return new ManualKey<T>(type, name);
  }

  /**
   * Gets a key for a type. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static Key<?> get(Type type) {
    return new ManualKey<Object>(type, DEFAULT_NAME);
  }

  /**
   * Gets a key for a type and a name.
   */
  public static Key<?> get(Type type, String name) {
    return new ManualKey<Object>(type, name);
  }

  /**
   * Gets a key for a type token. Defaults name to {@link #DEFAULT_NAME}.
   */
  public static <T> Key<T> get(TypeToken<T> typeToken) {
    return new ManualKey<T>(typeToken, DEFAULT_NAME);
  }

  /**
   * Gets key for a type token and a name.
   */
  public static <T> Key<T> get(TypeToken<T> typeToken, String name) {
    return new ManualKey<T>(typeToken, name);
  }

  private static class ManualKey<T> extends Key<T> {

    private ManualKey(Type type, String name) {
      super(type, name);
    }

    private ManualKey(TypeToken<T> typeToken, String name) {
      super(typeToken, name);
    }
  }
}
