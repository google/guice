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
import com.google.inject.internal.ConfigurationException;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.Keys;
import com.google.inject.internal.MoreTypes;
import com.google.inject.internal.Nullability;
import java.io.Serializable;
import java.io.ObjectStreamException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * A constructor, method or field that can receive injections.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class InjectionPoint implements Serializable {

  private final boolean optional;
  private final Member member;
  private final ImmutableList<Dependency<?>> dependencies;

  private InjectionPoint(Member member,
      ImmutableList<Dependency<?>> dependencies, boolean optional) {
    this.member = member;
    this.dependencies = dependencies;
    this.optional = optional;
  }

  private InjectionPoint(Method method) {
    this.member = method;

    Inject inject = method.getAnnotation(Inject.class);
    this.optional = inject.optional();

    this.dependencies = forMember(method, method.getGenericParameterTypes(),
        method.getParameterAnnotations());
  }

  private InjectionPoint(Constructor<?> constructor) {
    this.member = constructor;
    this.optional = false;
    // TODO(jessewilson): make sure that if @Inject it exists, its not optional
    this.dependencies = forMember(constructor, constructor.getGenericParameterTypes(),
        constructor.getParameterAnnotations());
  }

  private InjectionPoint(Field field) {
    this.member = field;

    Inject inject = field.getAnnotation(Inject.class);
    this.optional = inject.optional();

    Annotation[] annotations = field.getAnnotations();

    Errors errors = new Errors(field);
    Key<?> key = null;
    try {
      key = Keys.get(field.getGenericType(), field, annotations, errors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
    ConfigurationException.throwNewIfNonEmpty(errors);

    this.dependencies = ImmutableList.<Dependency<?>>of(
        newDependency(key, Nullability.allowsNull(annotations), -1));
  }

  private ImmutableList<Dependency<?>> forMember(Member member, Type[] genericParameterTypes,
      Annotation[][] annotations) {
    Errors errors = new Errors(member);
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

    ConfigurationException.throwNewIfNonEmpty(errors);
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

  private Object writeReplace() throws ObjectStreamException {
    Member serializableMember = member != null ? MoreTypes.serializableCopy(member) : null;
    return new InjectionPoint(serializableMember, dependencies, optional);
  }

  /**
   * Returns a new injection point for {@code constructor}.
   *
   * @param constructor a no arguments constructor, or a constructor with any number of arguments
   *      and the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Constructor constructor) {
    return new InjectionPoint(constructor);
  }

  /**
   * Returns a new injection point for {@code method}.
   *
   * @param method a method with the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Method method) {
    return new InjectionPoint(method);
  }

  /**
   * Returns a new injection point for {@code field}.
   *
   * @param field a field with the {@literal @}{@link Inject} annotation.
   */
  public static InjectionPoint get(Field field) {
    return new InjectionPoint(field);
  }

  /**
   * Adds all static method and field injection points on {@code type} to {@code injectionPoints}.
   * All fields are added first, and then all methods. Within the fields, supertype fields are added
   * before subtype fields. Similarly, supertype methods are added before subtype methods.
   *
   * @throws RuntimeException if there is a malformed injection point on {@code type}, such as a
   *      field with multiple binding annotations. When such an exception is thrown, the valid
   *      injection points are still added to the collection.
   */
  public static void addForStaticMethodsAndFields(Class<?> type,
      Collection<InjectionPoint> injectionPoints) {
    Errors errors = new Errors();
    addInjectionPoints(type, Factory.FIELDS, true, injectionPoints, errors);
    addInjectionPoints(type, Factory.METHODS, true, injectionPoints, errors);
    ConfigurationException.throwNewIfNonEmpty(errors);
  }

  /**
   * Adds all instance method and field injection points on {@code type} to {@code injectionPoints}.
   * All fields are added first, and then all methods. Within the fields, supertype fields are added
   * before subtype fields. Similarly, supertype methods are added before subtype methods.
   *
   * @throws RuntimeException if there is a malformed injection point on {@code type}, such as a
   *      field with multiple binding annotations. When such an exception is thrown, the valid
   *      injection points are still added to the collection.
   */
  public static void addForInstanceMethodsAndFields(Class<?> type,
      List<InjectionPoint> injectionPoints) {
    // TODO (crazybob): Filter out overridden members.
    Errors errors = new Errors();
    addInjectionPoints(type, Factory.FIELDS, false, injectionPoints, errors);
    addInjectionPoints(type, Factory.METHODS, false, injectionPoints, errors);
    ConfigurationException.throwNewIfNonEmpty(errors);
  }

  private static <M extends Member & AnnotatedElement> void addInjectionPoints(Class<?> type,
      Factory<M> factory, boolean statics, Collection<InjectionPoint> injectionPoints,
      Errors errors) {
    if (type == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectionPoints(type.getSuperclass(), factory, statics, injectionPoints, errors);

    // Add injectors for all members next
    addInjectorsForMembers(type, factory, statics, injectionPoints, errors);
  }

  private static <M extends Member & AnnotatedElement> void addInjectorsForMembers(Class<?> type,
      Factory<M> factory, boolean statics, Collection<InjectionPoint> injectionPoints,
      Errors errors) {
    for (M member : factory.getMembers(type)) {
      if (isStatic(member) != statics) {
        continue;
      }

      Inject inject = member.getAnnotation(Inject.class);
      if (inject == null) {
        continue;
      }

      try {
        injectionPoints.add(factory.create(member));
      } catch (ConfigurationException e) {
        if (!inject.optional()) {
          errors.merge(e.getErrorMessages());
        }
      }
    }
  }

  private static boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private interface Factory<M extends Member & AnnotatedElement> {
    Factory<Field> FIELDS = new Factory<Field>() {
      public Field[] getMembers(Class<?> type) {
        return type.getDeclaredFields();
      }
      public InjectionPoint create(Field member) {
        return get(member);
      }
    };

    Factory<Method> METHODS = new Factory<Method>() {
      public Method[] getMembers(Class<?> type) {
        return type.getDeclaredMethods();
      }
      public InjectionPoint create(Method member) {
        return get(member);
      }
    };

    M[] getMembers(Class<?> type);
    InjectionPoint create(M member);
  }
}
