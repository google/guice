/*
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

package com.google.inject.internal;

import static com.google.inject.internal.util.Preconditions.checkNotNull;
import static com.google.inject.internal.util.Preconditions.checkState;

import com.google.inject.Stage;
import com.google.inject.internal.InjectorImpl.InjectorOptions;
import com.google.inject.spi.DisableCircularProxiesOption;
import com.google.inject.spi.RequireExplicitBindingsOption;

/**
 * A processor to gather injector options.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class InjectorOptionsProcessor extends AbstractProcessor {

  private boolean disableCircularProxies = false;
  private boolean jitDisabled = false;

  InjectorOptionsProcessor(Errors errors) {
    super(errors);
  }

  @Override
  public Boolean visit(DisableCircularProxiesOption option) {
    disableCircularProxies = true;
    return true;
  }

  @Override
  public Boolean visit(RequireExplicitBindingsOption option) {
    jitDisabled = true;
    return true;
  }

  InjectorOptions getOptions(Stage stage, InjectorOptions parentOptions) {
    checkNotNull(stage, "stage must be set");
    if(parentOptions == null) {
      return new InjectorOptions(
          stage,
          jitDisabled,
          disableCircularProxies);
    } else {
      checkState(stage == parentOptions.stage, "child & parent stage don't match");
      return new InjectorOptions(
          stage,
          jitDisabled || parentOptions.jitDisabled,
          disableCircularProxies || parentOptions.disableCircularProxies); 
    }
  }

}
