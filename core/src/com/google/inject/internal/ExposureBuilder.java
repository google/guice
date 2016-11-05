/*
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.internal;

import com.google.common.base.Preconditions;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.AnnotatedElementBuilder;
import java.lang.annotation.Annotation;

/** For private binder's expose() method. */
public class ExposureBuilder<T> implements AnnotatedElementBuilder {
  private final Binder binder;
  private final Object source;
  private Key<T> key;

  public ExposureBuilder(Binder binder, Object source, Key<T> key) {
    this.binder = binder;
    this.source = source;
    this.key = key;
  }

  protected void checkNotAnnotated() {
    if (key.getAnnotationType() != null) {
      binder.addError(AbstractBindingBuilder.ANNOTATION_ALREADY_SPECIFIED);
    }
  }

  @Override
  public void annotatedWith(Class<? extends Annotation> annotationType) {
    Preconditions.checkNotNull(annotationType, "annotationType");
    checkNotAnnotated();
    key = Key.get(key.getTypeLiteral(), annotationType);
  }

  @Override
  public void annotatedWith(Annotation annotation) {
    Preconditions.checkNotNull(annotation, "annotation");
    checkNotAnnotated();
    key = Key.get(key.getTypeLiteral(), annotation);
  }

  public Key<?> getKey() {
    return key;
  }

  public Object getSource() {
    return source;
  }

  @Override
  public String toString() {
    return "AnnotatedElementBuilder";
  }
}
