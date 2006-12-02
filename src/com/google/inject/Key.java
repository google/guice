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
   * Default dependency name.
   */
  public static final String DEFAULT_NAME = "default";

  final Class<T> type;
  final Type[] typeArguments;
  final String name;
  final int hashCode;

  protected Key(String name) {
    this.name = nonNull(name, "name");

    Type superclass = getClass().getGenericSuperclass();
    if (superclass instanceof Class) {
      throw new RuntimeException("Missing type parameter.");
    }
    Type type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
    if (type instanceof Class<?>) {
      this.type = (Class<T>) type;
      this.typeArguments = null;
    } else {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      this.type = (Class<T>) parameterizedType.getRawType();
      this.typeArguments = parameterizedType.getActualTypeArguments();
    }

    this.hashCode = computeHashCode();
  }

  protected Key() {
    this(DEFAULT_NAME);
  }

  private Key(Class<T> type, String name) {
    this.type = nonNull(type, "type");
    this.name = nonNull(name, "name");
    this.typeArguments = null;

    this.hashCode = computeHashCode();
  }

  private int computeHashCode() {
    return type.hashCode() * 31 + name.hashCode();
  }

  Class<T> getType() {
    return type;
  }

  String getName() {
    return name;
  }

  public int hashCode() {
    return hashCode;
  }

  public boolean equals(Object o) {
    if (!(o instanceof Key)) {
      return false;
    }
    if (o == this) {
      return true;
    }
    Key other = (Key) o;
    return name.equals(other.name) && type.equals(other.type);
  }

  public String toString() {
    return "[type=" + type.getName() + ", name='" + name + "']";
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
}
