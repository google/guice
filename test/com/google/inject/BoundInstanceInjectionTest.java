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
public class BoundInstanceInjectionTest extends TestCase {

  public void testInstancesAreInjected() throws CreationException {
    final O o = new O();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(O.class).toInstance(o);
        bind(int.class).toInstance(5);
      }
    });

    assertEquals(5, o.fromMethod);
  }

  static class O {
    int fromMethod;
    @Inject
    void setInt(int i) {
      this.fromMethod = i;
    }
  }

  public void testProvidersAreInjected() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(O.class).toProvider(new Provider<O>() {
          @Inject int i;
          public O get() {
            O o = new O();
            o.setInt(i);
            return o;
          }
        });
        bind(int.class).toInstance(5);
      }
    });

    assertEquals(5, injector.getInstance(O.class).fromMethod);
  }

}
