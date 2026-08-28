/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.inject.assistedinject;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.invoke.MethodHandles;
import junit.framework.TestCase;

public class PassthroughDefaultMethodsTest extends TestCase {
  private static class Thing {
    final int i;

    @Inject
    Thing(@Assisted int i) {
      this.i = i;
    }
  }

  @PassthroughDefaultMethods
  private interface Factory {
    Thing create(int i);

    default Thing one() {
      return this.create(1);
    }

    default Thing createPow(int i, int pow) {
      return this.create((int) Math.pow(i, pow));
    }
  }

  public void testAssistedInjection() throws IllegalAccessException {
    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Factory.class, MethodHandles.lookup());
    Injector injector =
      Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            install(new FactoryModuleBuilder().withLookups(lookup).build(Factory.class));
          }
        });
    Factory factory = injector.getInstance(Factory.class);
    assertEquals(1, factory.create(1).i);
    assertEquals(1, factory.one().i);
    assertEquals(256, factory.createPow(2, 8).i);
  }
}
