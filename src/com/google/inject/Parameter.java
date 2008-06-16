/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.common.collect.Lists;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.Keys;
import com.google.inject.internal.Nullability;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A method or constructor parameter, plus Guice metadata.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class Parameter<T> {
  private final int index;
  private final Key<T> key;
  private final boolean allowsNull;

  private Parameter(int index, Key<T> key, boolean allowsNull) {
    this.index = index;
    this.key = key;
    this.allowsNull = allowsNull;
  }

  public static <T> Parameter<T> create(int index, Key<T> key, boolean allowsNull) {
    return new Parameter<T>(index, key, allowsNull);
  }

  public Key<T> getKey() {
    return key;
  }

  public boolean allowsNull() {
    return allowsNull;
  }

  public int getIndex() {
    return index;
  }

  public static List<Parameter<?>> forMethod(Method method, Errors errors)
      throws ErrorsException {
    return forMember(method, method.getGenericParameterTypes(),
        method.getParameterAnnotations(), errors);
  }

  public static List<Parameter<?>> forConstructor(Constructor constructor, Errors errors)
      throws ErrorsException {
    return forMember(constructor, constructor.getGenericParameterTypes(),
        constructor.getParameterAnnotations(), errors);
  }

  private static List<Parameter<?>> forMember(Member member, Type[] genericParameterTypes,
      Annotation[][] annotations, Errors errors) throws ErrorsException {
    Iterator<Annotation[]> annotationsIterator = Arrays.asList(annotations).iterator();

    List<Parameter<?>> parameters = Lists.newArrayList();
    int index = 0;
    for (Type parameterType : genericParameterTypes) {
      try {
        Annotation[] parameterAnnotations = annotationsIterator.next();
        Key<?> key = Keys.get(parameterType, member, parameterAnnotations, errors);
        parameters.add(create(index, key, Nullability.allowsNull(parameterAnnotations)));
        index++;
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwIfNecessary();
    return parameters;
  }
}
