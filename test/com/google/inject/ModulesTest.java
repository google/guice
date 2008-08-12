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

import com.google.inject.util.Modules;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ModulesTest extends TestCase {

  public void testCombine() {
    Module one = new AbstractModule() {
      protected void configure() {
        bind(Integer.class).toInstance(1);
      }
    };
    Module two = new AbstractModule() {
      protected void configure() {
        bind(Long.class).toInstance(2L);
      }
    };
    Module three = new AbstractModule() {
      protected void configure() {
        bind(Short.class).toInstance((short) 3);
      }
    };

    Injector injector = Guice.createInjector(Modules.combine(one, two, three));
    assertEquals(1, injector.getInstance(Integer.class).intValue());
    assertEquals(2, injector.getInstance(Long.class).longValue());
    assertEquals(3, injector.getInstance(Short.class).shortValue());
  }
}
