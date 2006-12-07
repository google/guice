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
import java.lang.reflect.ParameterizedType;

/**
 * Binding key. Composed of the type to be injected and a name.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class Key<T> {

  /**
   * Default binding name.
   */
  public static final String DEFAULT_NAME = "default";

  final Class<T> rawType;
  final String name;
  final String typeString;
  final Type type;

  /**
   * Constructs a new key. Derives the type from this class's type parameter.
   * Requires client to create an empty anonymous class.
   *
   * <p>Example usage for a binding of type {@code Foo} named "bar":
   * {@code new Key<Foo>("bar") {}}.
   *
   * <p>The curly braces in the example serve to create an empty anonymous
   * class.
   */
  protected Key(String name) {
    this.name = nonNull(name, "name");
    Type superclass = getClass().getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new RuntimeException("Missing type parameter.");
    }
    this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    this.rawType = getRawType(type);
    this.typeString = toString(type);
  }

  /**
   * Convenience method. Delegates to {@link #Key(String)} with
   * {@link #DEFAULT_NAME}.
   */
  protected Key() {
    this(DEFAULT_NAME);
  }

  private Key(Type type, String name) {
    this.name = nonNull(name, "name");
    this.type = nonNull(type, "type");
    this.rawType = getRawType(nonNull(type, "type"));
    this.typeString = toString(type);
  }

  /**
   * Calls {@code getName()} on {@code Class}es and {@code toString()} on other
   * {@code Type}s.
   */
  private static String toString(Type type) {
    return type instanceof Class<?>
        ? ((Class<?>) type).getName()
        : type.toString();
  }

  @SuppressWarnings({"unchecked"})
  private static <T> Class<T> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<T>) type;
    } else {
      // type is a parameterized type.
      if (!(type instanceof ParameterizedType)) {
        unexpectedType(type, ParameterizedType.class);
      }
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // I'm not exactly sure why getRawType() returns Type instead of Class.
      // Neal isn't either but suspects some pathological case related
      // to nested classes exists.
      Type rawType = parameterizedType.getRawType();
      if (!(rawType instanceof Class<?>)) {
        unexpectedType(rawType, Class.class);
      }
      return (Class<T>) rawType;
    }
  }

  static void unexpectedType(Type token, Class<?> expected) {
    throw new AssertionError(
        "Unexpected type. Expected: " + expected.getName()
        + ", got: " + token.getClass().getName()
        + ", for type token: " + token.toString() + ".");
  }

  /**
   * Gets the raw type of this binding.
   */
  public Class<T> getRawType() {
    return rawType;
  }

  /**
   * Gets the binding name.
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the type of this binding.
   */
  public Type getType() {
    return type;
  }

  public int hashCode() {
    return typeString.hashCode();
  }

  /**
   * Gets the key corresponding the raw type.
   */
  public Key<?> rawKey() {
    return Key.newInstance(rawType, name);
  }

  /**
   * Compares the binding name and type. Uses {@code String} representations
   * to compare types as the reflection API doesn't give us a lot of options
   * when it comes to comparing parameterized types.
   *
   * @inheritDoc
   */
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Key)) {
      return false;
    }
    Key other = (Key) o;
    return name.equals(other.name) && typeString.equals(other.typeString);
  }

  public String toString() {
    return "Key[type=" + typeString + ", name='" + name + "']";
  }

  /**
   * Constructs a key from a raw type.
   */
  public static <T> Key<T> newInstance(Class<T> type) {
    return new Key<T>(type, DEFAULT_NAME) {};
  }

  /**
   * Constructs a key from a raw type and a name.
   */
  public static <T> Key<T> newInstance(Class<T> type, String name) {
    return new Key<T>(type, name) {};
  }

  /**
   * Constructs a key from a type.
   */
  public static Key<?> newInstance(Type type) {
    return new Key<Object>(type, DEFAULT_NAME) {};
  }

  /**
   * Constructs a key from a type and a name.
   */
  public static Key<?> newInstance(Type type, String name) {
    return new Key<Object>(type, name) {};
  }
}
