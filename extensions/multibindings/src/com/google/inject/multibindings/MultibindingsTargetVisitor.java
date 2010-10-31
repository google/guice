/**
 * Copyright (C) 2010 Google Inc.
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

import com.google.inject.spi.BindingTargetVisitor;

/**
 * A visitor for the multibinder extension.
 * <p>
 * If your {@link BindingTargetVisitor} implements this interface, bindings created by using
 * {@link Multibinder} or {@link MapBinder} will be visited through this interface.
 * 
 * @since 3.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface MultibindingsTargetVisitor<T, V> extends BindingTargetVisitor<T, V> {
  
  /**
   * Visits a binding created through {@link Multibinder}.
   */
  V visit(MultibinderBinding<? extends T> multibinding);
  
  /**
   * Visits a binding created through {@link MapBinder}.
   */
  V visit(MapBinderBinding<? extends T> mapbinding);

}
