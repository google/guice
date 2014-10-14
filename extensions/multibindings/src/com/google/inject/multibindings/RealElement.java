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

package com.google.inject.multibindings;

import com.google.inject.Key;
import com.google.inject.internal.Annotations;

import java.lang.annotation.Annotation;
import java.util.concurrent.atomic.AtomicInteger;

/** An implementation of Element. */
// TODO(cgruber): Use AutoAnnotation when available, here & wherever else is makes sense.
class RealElement implements Element {
  private static final AtomicInteger nextUniqueId = new AtomicInteger(1);
  
  private final int uniqueId;
  private final String setName;
  private final Element.Type type;
  private final String keyType;

  RealElement(String setName, Element.Type type, String keyType) {
    this(setName, type, keyType, nextUniqueId.incrementAndGet());
  }
  
  RealElement(String setName, Element.Type type, String keyType, int uniqueId) {
    this.uniqueId = uniqueId;
    this.setName = setName;
    this.type = type;
    this.keyType = keyType;
  }
  
  @Override public String setName() {
    return setName;
  }
  
  @Override public int uniqueId() {
    return uniqueId;
  }
  
  @Override public Element.Type type() {
    return type;
  }
  
  @Override public String keyType() {
    return keyType;
  }

  @Override public Class<? extends Annotation> annotationType() {
    return Element.class;
  }
  
  @Override public String toString() {
    return "@" + Element.class.getName() + "(setName=" + setName
        + ",uniqueId=" + uniqueId + ", type=" + type + ", keyType=" + keyType + ")";
  }

  @Override public boolean equals(Object o) {
    return o instanceof Element
        && ((Element) o).setName().equals(setName())
        && ((Element) o).uniqueId() == uniqueId()
        && ((Element) o).type() == type()
        && ((Element) o).keyType().equals(keyType());
  }

  @Override public int hashCode() {
    return ((127 * "setName".hashCode()) ^ setName.hashCode())
        + ((127 * "uniqueId".hashCode()) ^ uniqueId)
        + ((127 * "type".hashCode()) ^ type.hashCode())
        + ((127 * "keyType".hashCode()) ^ keyType.hashCode());
  }

  /**
   * Returns the name the binding should use.  This is based on the annotation.
   * If the annotation has an instance and is not a marker annotation,
   * we ask the annotation for its toString.  If it was a marker annotation
   * or just an annotation type, we use the annotation's name. Otherwise,
   * the name is the empty string.
   */
  static String nameOf(Key<?> key) {
    Annotation annotation = key.getAnnotation();
    Class<? extends Annotation> annotationType = key.getAnnotationType();
    if (annotation != null && !Annotations.isMarker(annotationType)) {
      return key.getAnnotation().toString();
    } else if (key.getAnnotationType() != null) {
      return "@" + key.getAnnotationType().getName();
    } else {
      return "";
    }
  }
}
