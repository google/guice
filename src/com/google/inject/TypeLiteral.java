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

import com.google.inject.internal.MoreTypes;
import static com.google.inject.internal.MoreTypes.canonicalize;
import com.google.inject.util.Types;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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
public class TypeLiteral<T> implements Serializable {

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
    this.rawType = (Class<? super T>) MoreTypes.getRawType(type);
    this.hashCode = MoreTypes.hashCode(type);
  }

  /**
   * Unsafe. Constructs a type literal manually.
   */
  @SuppressWarnings("unchecked")
  TypeLiteral(Type type) {
    this.type = canonicalize(checkNotNull(type, "type"));
    this.rawType = (Class<? super T>) MoreTypes.getRawType(this.type);
    this.hashCode = MoreTypes.hashCode(this.type);
  }

  /**
   * Returns the type from super class's type parameter in {@link
   * com.google.inject.internal.MoreTypes#canonicalize(Type) canonical form}.
   */
  static Type getSuperclassTypeParameter(Class<?> subclass) {
    Type superclass = subclass.getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new RuntimeException("Missing type parameter.");
    }
    ParameterizedType parameterized = (ParameterizedType) superclass;
    return canonicalize(parameterized.getActualTypeArguments()[0]);
  }

  /**
   * Gets type literal from super class's type parameter.
   */
  static TypeLiteral<?> fromSuperclassTypeParameter(Class<?> subclass) {
    return new TypeLiteral<Object>(getSuperclassTypeParameter(subclass));
  }

  /**
   * Gets the raw type.
   */
  final Class<? super T> getRawType() {
    return rawType;
  }

  /**
   * Gets underlying {@code Type} instance.
   */
  public final Type getType() {
    return type;
  }

  /**
   * Gets the type of this type's provider.
   */
  @SuppressWarnings("unchecked")
  final TypeLiteral<Provider<T>> providerType() {
    // This cast is safe and wouldn't generate a warning if Type had a type
    // parameter.
    return (TypeLiteral<Provider<T>>) get(Types.providerOf(getType()));
  }

  @Override public final int hashCode() {
    return this.hashCode;
  }

  @Override public final boolean equals(Object o) {
    return o instanceof TypeLiteral<?>
        && MoreTypes.equals(type, ((TypeLiteral) o).type);
  }

  @Override public final String toString() {
    return MoreTypes.toString(type);
  }

  /**
   * Gets type literal for the given {@code Type} instance.
   */
  public static TypeLiteral<?> get(Type type) {
    return new TypeLiteral<Object>(type);
  }

  /**
   * Gets type literal for the given {@code Class} instance.
   */
  public static <T> TypeLiteral<T> get(Class<T> type) {
    return new TypeLiteral<T>(type);
  }

  /**
   * Returns the canonical form of this type literal for serialization. The
   * returned instance is always a {@code TypeLiteral}, never a subclass. This
   * prevents problems caused by serializing anonymous types.
   */
  protected final Object writeReplace() {
    return getClass() == TypeLiteral.class
        ? this
        : get(type);
  }

  private static final long serialVersionUID = 0;
}
