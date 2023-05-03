/*
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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;

public final class InjectionContext implements AnnotatedElement {
  private final Dependency<?> dependency;
  private final Member member;
  private final Parameter parameter;

  InjectionContext(Dependency<?> dependency, Constructor<?> constructor) {
    this.dependency = dependency;
    this.member = constructor;
    this.parameter = null;
  }

  InjectionContext(Dependency<?> dependency, Field field) {
    this.dependency = dependency;
    this.member = field;
    this.parameter = null;
  }

  InjectionContext(Dependency<?> dependency, Method method) {
    this.dependency = dependency;
    this.member = method;
    this.parameter = null;
  }

  InjectionContext(Dependency<?> dependency, Parameter parameter) {
    this.dependency = dependency;
    this.member = null;
    this.parameter = parameter;
  }

  public Dependency<?> getDependency() {
    return this.dependency;
  }

  public Member getMember() {
    return member;
  }

  public Parameter getParameter() {
    return this.parameter;
  }

  @Override
  public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) {
    if (member != null) {
      return ((AnnotatedElement) member).getAnnotation(annotationClass);
    } else if (parameter != null) {
      return ((AnnotatedElement) parameter).getAnnotation(annotationClass);
    }
    return null;
  }

  @Override
  public Annotation[] getAnnotations() {
    if (member != null) {
      return ((AnnotatedElement) member).getAnnotations();
    } else if (parameter != null) {
      return ((AnnotatedElement) parameter).getAnnotations();
    }
    return new Annotation[0];
  }

  @Override
  public Annotation[] getDeclaredAnnotations() {
    if (member != null) {
      return ((AnnotatedElement) member).getDeclaredAnnotations();
    } else if (parameter != null) {
      return ((AnnotatedElement) parameter).getDeclaredAnnotations();
    }
    return new Annotation[0];
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof InjectionContext
        && Objects.equals(member, ((InjectionContext) o).member)
        && Objects.equals(parameter, ((InjectionContext) o).parameter);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(member) ^ Objects.hashCode(parameter);
  }

  @Override
  public String toString() {
    return "InjectionContext[" + (member != null ? member : parameter) + "]";
  }

  public static InjectionContext fromDependency(Dependency<?> dependency) {
    InjectionPoint point = dependency.getInjectionPoint();
    if (point != null) {
      Member member = point.getMember();
      int index = dependency.getParameterIndex();
      if (index != -1) {
        Method method = (Method) member;
        return new InjectionContext(dependency, method.getParameters()[index]);
      } else {
        if (member instanceof Constructor) {
          return new InjectionContext(dependency, (Constructor<?>) member);
        } else if (member instanceof Method) {
          return new InjectionContext(dependency, (Method) member);
        } else if (member instanceof Field) {
          return new InjectionContext(dependency, (Field) member);
        }
      }
    }
    return null;
  }
}
