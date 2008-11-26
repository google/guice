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

package com.google.inject;

import com.google.common.collect.Lists;
import com.google.inject.internal.Errors;
import com.google.inject.spi.PrivateEnvironment;
import java.util.List;

/**
 * Handles {@link Binder#newPrivateBinder()} elements.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class PrivateEnvironmentProcessor extends AbstractProcessor {

  private final Stage stage;
  private final List<InjectorShell.Builder> injectorShellBuilders = Lists.newArrayList();

  PrivateEnvironmentProcessor(Errors errors, Stage stage) {
    super(errors);
    this.stage = stage;
  }

  @Override public Boolean visitPrivateEnvironment(PrivateEnvironment privateEnvironment) {
    InjectorShell.Builder builder = new InjectorShell.Builder()
        .parent(injector)
        .stage(stage)
        .privateEnvironment(privateEnvironment);
    injectorShellBuilders.add(builder);
    return true;
  }

  public List<InjectorShell.Builder> getInjectorShellBuilders() {
    return injectorShellBuilders;
  }
}
