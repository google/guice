/*
 * Copyright (C) 2015 Google Inc.
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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Allows users define customized key type annotations for map bindings by annotating an annotation
 * of a {@code Map}'s key type. The custom key annotation can be applied to methods also annotated
 * with {@literal @}{@link ProvidesIntoMap}.
 *
 * <p>A {@link StringMapKey} and {@link ClassMapKey} are provided for convenience with maps whose
 * keys are strings or classes. For maps with enums or primitive types as keys, you must provide
 * your own MapKey annotation, such as this one for an enum:
 *
 * <pre>
 * {@literal @}MapKey(unwrapValue = true)
 * {@literal @}Retention(RUNTIME)
 * public {@literal @}interface MyCustomEnumKey {
 *   MyCustomEnum value();
 * }
 * </pre>
 *
 * You can also use the whole annotation as the key, if {@code unwrapValue=false}. When unwrapValue
 * is false, the annotation type will be the key type for the injected map and the annotation
 * instances will be the key values. If {@code unwrapValue=true}, the value() type will be the key
 * type for injected map and the value() instances will be the keys values.
 *
 * @since 4.0
 */
@Documented
@Target(ANNOTATION_TYPE)
@Retention(RUNTIME)
public @interface MapKey {
  /**
   * if {@code unwrapValue} is false, then the whole annotation will be the type and annotation
   * instances will be the keys. If {@code unwrapValue} is true, the value() type of key type
   * annotation will be the key type for injected map and the value instances will be the keys.
   */
  boolean unwrapValue() default true;
}
