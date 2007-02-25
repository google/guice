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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * Represents a generic type {@code T}. Java doesn't yet provide a way to
 * represent generic types, so this class does. Forces clients to create a
 * subclass of this class which enables retrieval the type information even at
 * runtime.
 *
 * <p>For example, to create a type literal for {@code List<String>}, you can
 * create an empty anonymous inner class:
 *
 * <p>
 * {@code TypeLiteral<List<String>> list = new TypeLiteral<List<String>>() {};}
 *
 * <p>Assumes that type {@code T} implements {@link Object#equals} and
 * {@link Object#hashCode()} as value (as opposed to identity) comparison.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class TypeLiteral<T> {

  final Class<? super T> rawType;
  final Type type;
  final int hashCode;

  /**
   * Constructs a new type literal. Derives represented class from type
   * parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute it
   * at runtime despite erasure.
   */
  @SuppressWarnings("unchecked")
  protected TypeLiteral() {
    this.type = getSuperclassTypeParameter(getClass());
    this.rawType = (Class<? super T>) getRawType(type);
    this.hashCode = hashCode(type);
  }

  /**
   * Unsafe. Constructs a type literal manually.
   */
  @SuppressWarnings("unchecked")
  TypeLiteral(Type type) {
    this.rawType = (Class<? super T>) getRawType(nonNull(type, "type"));
    this.type = type;
    this.hashCode = hashCode(type);
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

  @SuppressWarnings({ "unchecked" })
  private static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<?>) type;
    }
    else {
      if (type instanceof ParameterizedType) {
        ParameterizedType parameterizedType = (ParameterizedType) type;

        // I'm not exactly sure why getRawType() returns Type instead of Class.
        // Neal isn't either but suspects some pathological case related
        // to nested classes exists.
        Type rawType = parameterizedType.getRawType();
        if (!(rawType instanceof Class<?>)) {
          throw unexpectedType(rawType, Class.class);
        }
        return (Class<?>) rawType;
      }

      if (type instanceof GenericArrayType) {
        // TODO: Is this sufficient?
        return Object[].class;
      }

      // type is a parameterized type.
      throw unexpectedType(type, ParameterizedType.class);
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
    return this.hashCode;
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TypeLiteral<?>)) {
      return false;
    }
    TypeLiteral<?> other = (TypeLiteral<?>) o;

    return equals(type, other.type);
  }

  public String toString() {
    return type instanceof Class<?>
        ? ((Class<?>) type).getName()
        : type.toString();
  }

  static AssertionError unexpectedType(Type type, Class<?> expected) {
    return new AssertionError(
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

  static int hashCode(Type type) {
    if (type instanceof ParameterizedType) {
      ParameterizedType p = (ParameterizedType) type;
      int h = p.getRawType().hashCode();
      for (Type argument : p.getActualTypeArguments()) {
        h = h * 31 + hashCode(argument);
      }
      return h;
    }

    if (type instanceof Class) {
      // Class specifies hashCode().
      return type.hashCode();
    }

    if (type instanceof GenericArrayType) {
      return hashCode(((GenericArrayType) type).getGenericComponentType()) * 31;
    }

    // This isn't a type we support. Could be a generic array type, wildcard
    // type, etc.
    return type.hashCode();
  }

  static boolean equals(Type a, Type b) {
    if (a instanceof Class) {
      // Class already specifies equals().
      return a.equals(b);
    }

    if (a instanceof ParameterizedType) {
      if (!(b instanceof ParameterizedType)) {
        return false;
      }

      ParameterizedType pa = (ParameterizedType) a;
      ParameterizedType pb = (ParameterizedType) b;

      if (!pa.getRawType().equals(pb.getRawType())) {
        return false;
      }

      Type[] aa = pa.getActualTypeArguments();
      Type[] ba = pb.getActualTypeArguments();
      if (aa.length != ba.length) {
        return false;
      }

      for (int i = 0; i < aa.length; i++) {
        if (!equals(aa[i], ba[i])) {
          return false;
        }
      }

      return true;
    }

    if (a instanceof GenericArrayType) {
      if (!(b instanceof GenericArrayType)) {
        return false;
      }

      return equals(
          ((GenericArrayType) a).getGenericComponentType(),
          ((GenericArrayType) b).getGenericComponentType()
      );
    }

    // This isn't a type we support. Could be a generic array type, wildcard
    // type, etc.
    return false;
  }
}
