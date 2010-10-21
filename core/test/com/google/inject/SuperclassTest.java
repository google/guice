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
public class SuperclassTest extends TestCase {

  public void testSuperclassInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class);
      }
    });

    Provider<Sub> creator = injector.getProvider(Sub.class);
    Sub sub = creator.get();
    sub = creator.get();
    sub = creator.get();
    sub = creator.get();
    sub = creator.get();
    assertNotNull(sub.field);
    assertNotNull(sub.fromMethod);
  }

  static abstract class Super {
    @Inject Foo field;

    Foo fromMethod;
    @Inject void setC(Foo foo) {
      fromMethod = foo;
    }
  }

  static class Sub extends Super {
  }

  static class Foo {}
}
