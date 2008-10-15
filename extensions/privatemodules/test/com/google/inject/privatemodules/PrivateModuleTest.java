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

package com.google.inject.privatemodules;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class PrivateModuleTest extends TestCase {

  public void testBasicUsage() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).annotatedWith(named("a")).toInstance("public");

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("b")).toInstance("i");

            bind(AB.class).annotatedWith(named("one")).to(AB.class);
            expose(AB.class).annotatedWith(named("one"));
          }
        });

        install(new PrivateModule() {
          public void configurePrivateBindings() {
            bind(String.class).annotatedWith(named("b")).toInstance("ii");

            bind(AB.class).annotatedWith(named("two")).to(AB.class);
            expose(AB.class).annotatedWith(named("two"));
          }
        });
      }
    });

    AB ab1 = injector.getInstance(Key.get(AB.class, named("one")));
    assertEquals("public", ab1.a);
    assertEquals("i", ab1.b);

    AB ab2 = injector.getInstance(Key.get(AB.class, named("two")));
    assertEquals("public", ab2.a);
    assertEquals("ii", ab2.b);
  }

  static class AB {
    @Inject @Named("a") String a;
    @Inject @Named("b") String b;
  }
}
