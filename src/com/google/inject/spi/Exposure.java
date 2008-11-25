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

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.binder.AnnotatedElementBuilder;
import com.google.inject.internal.ModuleBinding;
import java.lang.annotation.Annotation;

/**
 * Exposes a binding to its enclosing environment.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class Exposure implements Element {
  private final Object source;
  private final PrivateEnvironment privateEnvironment;
  private Key<?> key;

  Exposure(Object source, PrivateEnvironment privateEnvironment, Key<?> key) {
    this.source = checkNotNull(source, "source");
    this.privateEnvironment = privateEnvironment;
    this.key = checkNotNull(key, "key");
  }

  public Object getSource() {
    return source;
  }

  /**
   * Returns the environment that owns this binding. Its enclosing environment gains access to the
   * binding via this exposure.
   */
  public PrivateEnvironment getPrivateEnvironment() {
    return privateEnvironment;
  }

  /**
   * Returns the exposed key.
   */
  public Key<?> getKey() {
    return key;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visitExposure(this);
  }

  /** Returns a builder that applies annotations to this exposed key. */
  AnnotatedElementBuilder annotatedElementBuilder(final Binder binder) {
    return new AnnotatedElementBuilder() {
      public void annotatedWith(Class<? extends Annotation> annotationType) {
        checkNotNull(annotationType, "annotationType");
        checkNotAnnotated();
        key = Key.get(key.getTypeLiteral(), annotationType);
      }

      public void annotatedWith(Annotation annotation) {
        checkNotNull(annotation, "annotation");
        checkNotAnnotated();
        key = Key.get(key.getTypeLiteral(), annotation);
      }

      private void checkNotAnnotated() {
        if (key.getAnnotationType() != null) {
          binder.addError(ModuleBinding.ANNOTATION_ALREADY_SPECIFIED);
        }
      }
    };
  }
}
