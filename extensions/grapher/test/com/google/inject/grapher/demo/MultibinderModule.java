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

package com.google.inject.grapher.demo;

import com.google.inject.config.AbstractModule;
import com.google.inject.multibindings.Multibinder;

/**
 * Module to add {@link Multibinder}-based bindings to the injector.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class MultibinderModule extends AbstractModule {
  @Override
  protected void configure() {
    Multibinder<Person> charactersBinder = Multibinder.newSetBinder(binder(), Person.class);
    charactersBinder.addBinding().to(MartyMcFly.class);
    charactersBinder.addBinding().to(DocBrown.class);
  }
}
