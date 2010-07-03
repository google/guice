/**
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.assistedinject;

import com.google.inject.ConfigurationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.Annotations;
import static com.google.inject.internal.util.Preconditions.checkArgument;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Models a method or constructor parameter.
 *
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class Parameter {
  
  private final Type type;
  private final boolean isAssisted;
  private final Annotation bindingAnnotation;
  private final boolean isProvider;

  private volatile Provider<? extends Object> provider;

  public Parameter(Type type, Annotation[] annotations) {
    this.type = type;
    this.bindingAnnotation = getBindingAnnotation(annotations);
    this.isAssisted = hasAssistedAnnotation(annotations);
    this.isProvider = isProvider(type);
  }

  public boolean isProvidedByFactory() {
    return isAssisted;
  }
  
  public Type getType() {
    return type;
  }

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    if (isAssisted) {
      result.append("@Assisted");
      result.append(" ");
    }
    if (bindingAnnotation != null) {
      result.append(bindingAnnotation.toString());
      result.append(" ");
    }
    result.append(type.toString());
    return result.toString();
  }

  private boolean hasAssistedAnnotation(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().equals(Assisted.class)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the Guice {@link Key} for this parameter.
   */
  public Object getValue(Injector injector) {
    if (null == provider) {
      synchronized (this) {
        if (null == provider) {
          provider = isProvider
              ? injector.getProvider(getBindingForType(getProvidedType(type)))
              : injector.getProvider(getPrimaryBindingKey());
        }
      }
    }

    return isProvider ? provider : provider.get();
  }

  public boolean isBound(Injector injector) {
    return isBound(injector, getPrimaryBindingKey())
        || isBound(injector, fixAnnotations(getPrimaryBindingKey()));
  }

  private boolean isBound(Injector injector, Key<?> key) {
    // This method is particularly lame - we really need an API that can test
    // for any binding, implicit or explicit
    try {
      return injector.getBinding(key) != null;
    } catch (ConfigurationException e) {
      return false;
    }
  }

  /**
   * Replace annotation instances with annotation types, this is only
   * appropriate for testing if a key is bound and not for injecting.
   *
   * See Guice bug 125,
   * http://code.google.com/p/google-guice/issues/detail?id=125
   */
  public Key<?> fixAnnotations(Key<?> key) {
    return key.getAnnotation() == null
        ? key
        : Key.get(key.getTypeLiteral(), key.getAnnotation().annotationType());
  }

  Key<?> getPrimaryBindingKey() {
    return isProvider
        ? getBindingForType(getProvidedType(type))
        : getBindingForType(type);
  }

  private Type getProvidedType(Type type) {
    return ((ParameterizedType) type).getActualTypeArguments()[0];
  }

  private boolean isProvider(Type type) {
    return type instanceof ParameterizedType
        && ((ParameterizedType) type).getRawType() == Provider.class;
  }

  private Key<?> getBindingForType(Type type) {
    return bindingAnnotation != null
        ? Key.get(type, bindingAnnotation)
        : Key.get(type);
  }

  /**
   * Returns the unique binding annotation from the specified list, or
   * {@code null} if there are none.
   *
   * @throws IllegalStateException if multiple binding annotations exist.
   */
  private Annotation getBindingAnnotation(Annotation[] annotations) {
    Annotation bindingAnnotation = null;
    for (Annotation annotation : annotations) {
      if (Annotations.isBindingAnnotation(annotation.annotationType())) {
        checkArgument(bindingAnnotation == null,
            "Parameter has multiple binding annotations: %s and %s", bindingAnnotation, annotation);
        bindingAnnotation = annotation;
      }
    }
    return bindingAnnotation;
  }
}
