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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.Member;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Iterator;
import java.util.Arrays;
import java.util.ArrayList;

import com.google.inject.internal.Keys;
import com.google.inject.internal.ErrorHandler;
import net.sf.cglib.reflect.FastConstructor;

/**
 * A method or constructor parameter, plus Guice metadata.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class Parameter<T> {
  private final int index;
  private final Key<T> key;
  private final Nullability nullability;

  private Parameter(int index, Key<T> key, Nullability nullability) {
    this.index = index;
    this.key = key;
    this.nullability = nullability;
  }

  public static <T> Parameter<T> create(int index, Key<T> key, Nullability nullability) {
    return new Parameter<T>(index, key, nullability);
  }

  public Key<T> getKey() {
    return key;
  }

  public Nullability getNullability() {
    return nullability;
  }

  public int getIndex() {
    return index;
  }

  public static List<Parameter<?>> forMethod(ErrorHandler errorHandler, Method method) {
    return forMember(errorHandler, method, method.getGenericParameterTypes(),
        method.getParameterAnnotations());
  }

  public static List<Parameter<?>> forConstructor(
      ErrorHandler errorHandler, Constructor constructor) {
    return forMember(errorHandler, constructor, constructor.getGenericParameterTypes(),
        constructor.getParameterAnnotations());
  }

  public static List<Parameter<?>> forConstructor(
      ErrorHandler errorHandler, FastConstructor constructor) {
    return forConstructor(errorHandler, constructor.getJavaConstructor());
  }

  private static List<Parameter<?>> forMember(ErrorHandler errorHandler, Member member,
      Type[] genericParameterTypes, Annotation[][] annotations) {
    Iterator<Annotation[]> annotationsIterator = Arrays.asList(annotations).iterator();

    List<Parameter<?>> parameters = new ArrayList<Parameter<?>>();
    int index = 0;
    for (Type parameterType : genericParameterTypes) {
      Annotation[] parameterAnnotations = annotationsIterator.next();
      Key<?> key = Keys.get(parameterType, member, parameterAnnotations, errorHandler);
      parameters.add(create(index, key, Nullability.forAnnotations(parameterAnnotations)));
      index++;
    }

    return parameters;
  }
}
