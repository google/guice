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

import com.google.inject.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Models a method or constructor parameter.
 *
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class Parameter {
  
  private static final Map<Type, Type> PRIMITIVE_COUNTERPARTS;
  static {
    Map<Type, Type> primitiveToWrapper = new HashMap<Type, Type>() {{
        put(int.class, Integer.class);
        put(long.class, Long.class);
        put(boolean.class, Boolean.class);
        put(byte.class, Byte.class);
        put(short.class, Short.class);
        put(float.class, Float.class);
        put(double.class, Double.class);
        put(char.class, Character.class);
    }};

    Map<Type, Type> counterparts = new HashMap<Type, Type>();
    for (Map.Entry<Type, Type> entry : primitiveToWrapper.entrySet()) {
      Type key = entry.getKey();
      Type value = entry.getValue();
      counterparts.put(key, value);
      counterparts.put(value, key);
    }

    PRIMITIVE_COUNTERPARTS = Collections.unmodifiableMap(counterparts);
  }

  private final Type type;
  private final boolean isAssisted;
  private final Annotation bindingAnnotation;
  private final boolean isProvider;

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
    return isProvider
        ? injector.getProvider(getBindingForType(getProvidedType(type)))
        : injector.getInstance(getPrimaryBindingKey());
  }

  public boolean isBound(Injector injector) {
    return isBound(injector, getPrimaryBindingKey())
        || isBound(injector, getAlternateGuiceBindingKey())
        || isBound(injector, fixAnnotations(getPrimaryBindingKey()))
        || isBound(injector, fixAnnotations(getAlternateGuiceBindingKey()));
  }

  private boolean isBound(Injector injector, Key<?> key) {
    /* This method is particularly lame - we really need an API that can test
       for any binding, implicit or explicit */
    try {
      return injector.getBinding(key) != null
          || injector.getProvider(key) != null;
    } catch (ProvisionException e) {
      return false;
    } catch (RuntimeException re) {
      // TODO: make ConfigurationException public?
      if (re.getClass().getName().contains("ConfigurationException")) {
        return false;
      } else {
        throw re;
      }
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

  private Key<?> getPrimaryBindingKey() {
    return isProvider
        ? getBindingForType(getProvidedType(type))
        : getBindingForType(type);
  }

  private Key<?> getAlternateGuiceBindingKey() {
    Type counterpart = (PRIMITIVE_COUNTERPARTS.containsKey(type))
      ? PRIMITIVE_COUNTERPARTS.get(type)
      : type;
    return isProvider
        ? getBindingForType(getProvidedType(counterpart))
        : getBindingForType(counterpart);
  }


  private Type getProvidedType(Type type) {
    return ((ParameterizedType)type).getActualTypeArguments()[0];
  }

  private boolean isProvider(Type type) {
    return type instanceof ParameterizedType
        && ((ParameterizedType)type).getRawType() == Provider.class;
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
    for (Annotation a : annotations) {
      if (a.annotationType().getAnnotation(BindingAnnotation.class) != null) {
        if (bindingAnnotation != null) {
          throw new IllegalArgumentException(String.format("Parameter has " +
              "multiple binding annotations: %s and %s", bindingAnnotation, a));
        }
        bindingAnnotation = a;
      }
    }
    return bindingAnnotation;
  }
}
