/**
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

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class PreloadingTest extends TestCase {

  protected void tearDown() throws Exception {
    Foo.count = 0;
    Bar.count = 0;
  }

  public void testPreloadSome() throws CreationException {
    createBinder(Stage.DEVELOPMENT);
    assertEquals(1, Foo.count);
    assertEquals(0, Bar.count);
  }

  public void testPreloadAll() throws CreationException {
    createBinder(Stage.PRODUCTION);
    assertEquals(1, Foo.count);
    assertEquals(1, Bar.count);
  }

  private Injector createBinder(Stage stage) {
    return Guice.createInjector(stage, new AbstractModule() {
      protected void configure() {
        bind(Foo.class).asEagerSingleton();
        bind(Bar.class);
      }
    });
  }

  static class Foo {
    static int count = 0;
    public Foo() {
      count++;
    }
  }

  @Singleton
  static class Bar {
    static int count = 0;
    public Bar() {
      count++;
    }
  }
}
