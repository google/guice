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

package com.google.inject.spi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.Keys;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.Nullability;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * A constructor, method or field that can receive injections.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class InjectionPoint implements Serializable {

  private final boolean optional;
  private final Member member;
  private final ImmutableList<Dependency<?>> dependencies;

  private InjectionPoint(Member member,
      ImmutableList<Dependency<?>> dependencies, boolean optional) {
    this.member = member;
    this.dependencies = dependencies;
    this.optional = optional;
  }

  private InjectionPoint(Method method) throws ErrorsException {
    this.member = method;

    Inject inject = method.getAnnotation(Inject.class);
    this.optional = inject.optional();

    this.dependencies = forMember(method, method.getGenericParameterTypes(),
        method.getParameterAnnotations(), new Errors());
  }

  private InjectionPoint(Constructor<?> constructor, Errors errors) throws ErrorsException {
    this.member = constructor;
    this.optional = false;
    // TODO(jessewilson): make sure that if @Inject it exists, its not optional
    this.dependencies = forMember(constructor, constructor.getGenericParameterTypes(),
        constructor.getParameterAnnotations(), errors);
  }

  private InjectionPoint(Field field) throws ErrorsException {
    this.member = field;

    Inject inject = field.getAnnotation(Inject.class);
    this.optional = inject.optional();

    Annotation[] annotations = field.getAnnotations();
    Key<?> key = Keys.get(field.getGenericType(), field, annotations, new Errors());
    this.dependencies = ImmutableList.<Dependency<?>>of(
        newDependency(key, Nullability.allowsNull(annotations), -1));
  }

  private ImmutableList<Dependency<?>> forMember(Member member, Type[] genericParameterTypes,
      Annotation[][] annotations, Errors errors) throws ErrorsException {
    Iterator<Annotation[]> annotationsIterator = Arrays.asList(annotations).iterator();

    List<Dependency<?>> dependencies = Lists.newArrayList();
    int index = 0;
    for (Type parameterType : genericParameterTypes) {
      try {
        Annotation[] parameterAnnotations = annotationsIterator.next();
        Key<?> key = Keys.get(parameterType, member, parameterAnnotations, errors);
        dependencies.add(newDependency(key, Nullability.allowsNull(parameterAnnotations), index));
        index++;
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwIfNecessary();
    return ImmutableList.copyOf(dependencies);
  }

  private <T> Dependency<T> newDependency(Key<T> key, boolean allowsNull, int parameterIndex) {
    return new Dependency<T>(this, key, allowsNull, parameterIndex);
  }

  public Member getMember() {
    return member;
  }

  /**
   * Returns the dependencies for this injection point. If the injection point is for a method or
   * constructor, the dependencies will correspond to that member's parameters. Field injection
   * points always have a single dependency for the field itself.
   */
  public List<Dependency<?>> getDependencies() {
    return dependencies;
  }

  public boolean isOptional() {
    return optional;
  }

  @Override public boolean equals(Object o) {
    return o instanceof InjectionPoint
        && member == ((InjectionPoint) o).member;
  }

  @Override public int hashCode() {
    return member.hashCode();
  }

  @Override public String toString() {
    return MoreTypes.toString(member);
  }

  /**
   * Returns a new injection point for {@code constructor}.
   *
   * @param constructor a no arguments constructor, or a constructor with any number of arguments
   *      and the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Constructor constructor, Errors errors)
      throws ErrorsException {
    return new InjectionPoint(constructor, errors);
  }

  /**
   * Returns a new injection point for {@code method}.
   *
   * @param method a method with the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Method method) throws ErrorsException {
    return new InjectionPoint(method);
  }

  /**
   * Returns a new injection point for {@code field}.
   *
   * @param field a field with the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Field field) throws ErrorsException {
    return new InjectionPoint(field);
  }

  private Object writeReplace() throws ObjectStreamException {
    Member serializableMember = member != null ? MoreTypes.serializableCopy(member) : null;
    return new InjectionPoint(serializableMember, dependencies, optional);
  }
}
