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
 * Represents a generic type {@code T}. Due to erasure, Java doesn't provide
 * a way to represent generic types. This class enables that.
 *
 * <p>Assumes {@code Type} implements {@code equals()} and {@code hashCode()}
 * as a value (as opposed to identity) comparison.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class TypeToken<T> {

  final Class<? super T> rawType;
  final Type type;

  /**
   * Constructs a new type token. Derives represented class from type
   * parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute
   * it at runtime despite erasure.
   *
   * <p>For example: {@code TypeToken<List<String>> t = new
   * TypeToken<List<String>>() {};}
   */
  @SuppressWarnings({"unchecked"})
  protected TypeToken() {
    this.type = getSuperclassTypeParameter(getClass());
    this.rawType = (Class<? super T>) getRawType(type);
  }

  /**
   * Unsafe. Constructs a type token manually.
   */
  @SuppressWarnings({"unchecked"})
  private TypeToken(Type type) {
    this.rawType = (Class<? super T>) getRawType(nonNull(type, "type"));
    this.type = type;
  }

  /**
   * Gets type from super class's type parameter.
   */
  static Type getSuperclassTypeParameter(Class<?> subclass) {
    Type superclass = subclass.getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new RuntimeException("Missing type parameter.");
    }
    return ((ParameterizedType) superclass).getActualTypeArguments()[0];
  }

  /**
   * Gets type token from super class's type parameter.
   */
  static TypeToken<?> fromSuperclassTypeParameter(Class<?> subclass) {
    return new SimpleTypeToken<Object>(getSuperclassTypeParameter(subclass));
  }

  @SuppressWarnings({"unchecked"})
  private static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<?>) type;
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
      return (Class<?>) rawType;
    }
  }

  /**
   * Gets the raw type.
   */
  Class<? super T> getRawType() {
    return rawType;
  }

  /**
   * Gets underlying {@code Type} instance.
   */
  public Type getType() {
    return type;
  }

  public int hashCode() {
    return type.hashCode();
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TypeToken<?>)) {
      return false;
    }
    TypeToken<?> t = (TypeToken<?>) o;
    return type.equals(t.type);
  }

  public String toString() {
    return type instanceof Class<?>
          ? ((Class<?>) type).getName()
          : type.toString();
  }

  static void unexpectedType(Type token, Class<?> expected) {
    throw new AssertionError(
        "Unexpected type. Expected: " + expected.getName()
        + ", got: " + token.getClass().getName()
        + ", for type token: " + token.toString() + ".");
  }

  /**
   * Gets type token for the given {@code Type} instance.
   */
  public static TypeToken<?> get(Type type) {
    return new SimpleTypeToken<Object>(type);
  }

  /**
   * Gets type token for the given {@code Class} instance.
   */
  public static <T> TypeToken<T> get(Class<T> type) {
    return new SimpleTypeToken<T>(type);
  }

  private static class SimpleTypeToken<T> extends TypeToken<T> {
    public SimpleTypeToken(Type type) {
      super(type);
    }
  }
}
