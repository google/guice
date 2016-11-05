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

package com.google.inject.jdk8;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import junit.framework.TestCase;

/**
 * Test static methods in interfaces.
 *
 * @author tavianator@tavianator.com (Tavian Barnes)
 */
public class StaticInterfaceMethodsTest extends TestCase {

  private static class Thing {
    final int i;

    @Inject
    Thing(@Assisted int i) {
      this.i = i;
    }
  }

  private interface Factory {
    Thing create(int i);

    static Factory getDefault() {
      return Thing::new;
    }
  }

  public void testAssistedInjection() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(new FactoryModuleBuilder().build(Factory.class));
              }
            });
    Factory factory = injector.getInstance(Factory.class);
    assertEquals(1, factory.create(1).i);
  }
}
