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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

class TypeWithArgument implements ParameterizedType {

  final Type rawType;
  final Type typeArgument;

  TypeWithArgument(Type rawType, Type typeArgument) {
    this.rawType = rawType;
    this.typeArgument = typeArgument;
  }

  public Type[] getActualTypeArguments() {
    return new Type[] { typeArgument };
  }

  public Type getRawType() {
    return rawType;
  }

  public Type getOwnerType() {
    return null;
  }
}
