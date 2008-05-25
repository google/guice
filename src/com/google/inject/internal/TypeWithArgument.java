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

package com.google.inject.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class TypeWithArgument implements ParameterizedType {

  private final Type rawType;
  private final Type[] typeArguments;

  public TypeWithArgument(Type rawType, Type... typeArguments) {
    this.rawType = rawType;
    this.typeArguments = typeArguments.clone();
  }

  public Type[] getActualTypeArguments() {
    return typeArguments.clone();
  }

  public Type getRawType() {
    return rawType;
  }

  public Type getOwnerType() {
    return null;
  }

  @Override public int hashCode() {
    return Arrays.hashCode(typeArguments) ^ rawType.hashCode();
  }

  @Override public boolean equals(Object other) {
    if (!(other instanceof ParameterizedType)) {
      return false;
    }

    ParameterizedType that = (ParameterizedType) other;
    return getRawType().equals(that.getRawType())
        && Arrays.equals(getActualTypeArguments(), that.getActualTypeArguments())
        && that.getOwnerType() == null;
  }

  @Override public String toString() {
    return toString(this);
  }

  private String toString(Type type) {
    if (type instanceof Class<?>) {
      return ((Class) type).getName();
    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Type[] arguments = parameterizedType.getActualTypeArguments();
      StringBuilder stringBuilder = new StringBuilder()
          .append(toString(parameterizedType.getRawType()))
          .append("<")
          .append(toString(arguments[0]));
      for (int i = 1; i < arguments.length; i++) {
        stringBuilder.append(", ").append(toString(arguments[i]));
      }
      return stringBuilder.append(">").toString();
    } else {
      return type.toString();
    }
  }
}