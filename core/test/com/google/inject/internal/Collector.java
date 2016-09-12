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

package com.google.inject.internal;

import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.multibindings.MultibinderBinding;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
import com.google.inject.spi.DefaultBindingTargetVisitor;

class Collector extends DefaultBindingTargetVisitor<Object, Object>
    implements MultibindingsTargetVisitor<Object, Object> {
  MapBinderBinding<? extends Object> mapbinding;
  MultibinderBinding<? extends Object> setbinding;
  OptionalBinderBinding<? extends Object> optionalbinding;

  @Override
  public Object visit(MapBinderBinding<? extends Object> mapbinding) {
    this.mapbinding = mapbinding;
    return null;
  }

  @Override
  public Object visit(MultibinderBinding<? extends Object> multibinding) {
    this.setbinding = multibinding;
    return null;
  }

  @Override
  public Object visit(OptionalBinderBinding<? extends Object> optionalbinding) {
    this.optionalbinding = optionalbinding;
    return null;
  }
}
