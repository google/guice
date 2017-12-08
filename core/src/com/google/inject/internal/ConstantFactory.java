/*
 * Copyright (C) 2006 Google Inc.
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

import com.google.common.base.MoreObjects;
import com.google.inject.spi.Dependency;

/** @author crazybob@google.com (Bob Lee) */
final class ConstantFactory<T> implements InternalFactory<T> {

  private final Initializable<T> initializable;

  public ConstantFactory(Initializable<T> initializable) {
    this.initializable = initializable;
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    return initializable.get();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(ConstantFactory.class).add("value", initializable).toString();
  }
}
