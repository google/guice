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

package com.google.inject.grapher;

import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Join;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.util.StackTraceElements;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Reasonable implementation for {@link NameFactory}. Mostly takes various
 * {@link Object#toString()}s and strips package names out of them so that
 * they'll fit on the graph.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class ShortNameFactory implements NameFactory {
  public String getMemberName(Member member) {
    if (member instanceof Constructor) {
      return "<init>";
    } else if (member instanceof Method) {
      return "#" + member.getName() + "(...)";
    } else {
      return member.getName();      
    }
  }

  public String getAnnotationName(Key<?> key) {
    Annotation annotation = key.getAnnotation();
    Class<? extends Annotation> annotationType = key.getAnnotationType();
    if (annotation != null) {
      annotationType = annotation.annotationType();

      String annotationString = annotation.toString();
      String canonicalName = annotationType.getName();
      String simpleName = annotationType.getSimpleName();
 
      return annotationString.replace(canonicalName, simpleName).replace("()", "");
    } else if (annotationType != null) {
      return "@" + annotationType.getSimpleName();
    } else {
      return "";
    }
  }

  public String getClassName(Key<?> key) {
    TypeLiteral<?> typeLiteral = key.getTypeLiteral();
    return stripPackages(typeLiteral.toString());
  }

  public String getInstanceName(Object instance) {
    if (instance instanceof ProviderMethod) {
      return getMethodString(((ProviderMethod<?>) instance).getMethod());
    }

    if (instance instanceof CharSequence) {
      return "\"" + instance + "\"";
    }

    try {
      if (instance.getClass().getMethod("toString").getDeclaringClass().equals(Object.class)) {
        return stripPackages(instance.getClass().getName());
      }
    } catch (SecurityException e) {
      throw new AssertionError(e);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }

    return instance.toString();
  }

  /**
   * Returns a name for a Guice "source" object. This will typically be either
   * a {@link StackTraceElement} for when the binding is made to the instance,
   * or a {@link Method} when a provider method is used.
   */
  public String getSourceName(Object source) {
    if (source instanceof Method) {
      source = StackTraceElements.forMember((Method) source);
    }

    if (source instanceof StackTraceElement) {
      return getFileString((StackTraceElement) source);
    }

    return stripPackages(source.toString());
  }

  protected String getFileString(StackTraceElement stackTraceElement) {
    return stackTraceElement.getFileName() + ":" + stackTraceElement.getLineNumber();
  }

  protected String getMethodString(Method method) {
    List<String> paramStrings = Lists.newArrayList();
    for (Class<?> paramType : method.getParameterTypes()) {
      paramStrings.add(paramType.getSimpleName());
    }

    String paramString = Join.join(", ", paramStrings);
    return "#" + method.getName() + "(" + paramString + ")";
  }

  /**
   * Eliminates runs of lowercase characters and numbers separated by periods.
   * Seems to remove packages from fully-qualified type names pretty well.
   */
  private String stripPackages(String str) {
    return str.replaceAll("(^|[< .\\(])([a-z0-9]+\\.)*", "$1");
  }
}
