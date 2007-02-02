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
 * Represents a generic type {@code T}. Java doesn't yet provide a way to
 * represent generic types, so this class does. Forces clients to create a
 * subclass of this class which enables retrieval the type information
 * even at runtime.
 *
 * <p>For example, to create a {@code TypeLiteral} for {@code List<String>},
 * you can create an empty anonymous inner class:
 *
 * <pre>
 * TypeLiteral<List<String>> listOfString =
 *   new TypeLiteral<List<String>>() {};
 * </pre>
 *
 * <p>Assumes {@code Type} implements {@code equals()} and {@code hashCode()}
 * as a value (as opposed to identity) comparison.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class TypeLiteral<T> {

  final Class<? super T> rawType;
  final Type type;

  /**
   * Constructs a new type literal. Derives represented class from type
   * parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute
   * it at runtime despite erasure.
   *
   * <p>For example: {@code TypeLiteral<List<String>> t = new
   * TypeLiteral<List<String>>() {};}
   */
  @SuppressWarnings({"unchecked"})
  protected TypeLiteral() {
    this.type = getSuperclassTypeParameter(getClass());
    this.rawType = (Class<? super T>) getRawType(type);
  }

  /**
   * Unsafe. Constructs a type literal manually.
   */
  @SuppressWarnings({"unchecked"})
  private TypeLiteral(Type type) {
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
   * Gets type literal from super class's type parameter.
   */
  static TypeLiteral<?> fromSuperclassTypeParameter(Class<?> subclass) {
    return new SimpleTypeLiteral<Object>(getSuperclassTypeParameter(subclass));
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
    if (!(o instanceof TypeLiteral<?>)) {
      return false;
    }
    TypeLiteral<?> t = (TypeLiteral<?>) o;
    return type.equals(t.type);
  }

  public String toString() {
    return type instanceof Class<?>
          ? ((Class<?>) type).getName()
          : type.toString();
  }

  static void unexpectedType(Type type, Class<?> expected) {
    throw new AssertionError(
        "Unexpected type. Expected: " + expected.getName()
        + ", got: " + type.getClass().getName()
        + ", for type literal: " + type.toString() + ".");
  }

  /**
   * Gets type literal for the given {@code Type} instance.
   */
  public static TypeLiteral<?> get(Type type) {
    return new SimpleTypeLiteral<Object>(type);
  }

  /**
   * Gets type literal for the given {@code Class} instance.
   */
  public static <T> TypeLiteral<T> get(Class<T> type) {
    return new SimpleTypeLiteral<T>(type);
  }

  private static class SimpleTypeLiteral<T> extends TypeLiteral<T> {
    public SimpleTypeLiteral(Type type) {
      super(type);
    }
  }
}
