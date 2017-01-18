/*
 * Copyright (C) 2014 Google Inc.
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

import com.google.inject.Key;
import com.google.inject.Provides;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * An {@literal @}{@link Provides} binding or binding produced by a {@link
 * ModuleAnnotatedMethodScanner}.
 *
 * @since 4.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ProvidesMethodBinding<T> extends HasDependencies {

  /** Returns the method this binding uses. */
  Method getMethod();

  /** Returns the instance of the object the method is defined in. */
  Object getEnclosingInstance();

  /** Returns the key of the binding. */
  Key<T> getKey();

  /**
   * Returns the annotation that caused this binding to be created. For {@code @Provides} methods,
   * this is an instance of the {@code @Provides} annotation. For bindings from {@link
   * ModuleAnnotatedMethodScanner}, this is the annotation that caused the scanner to produce the
   * binding.
   */
  Annotation getAnnotation();
}
