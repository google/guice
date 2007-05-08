/**
 * Copyright (C) 2007 Google Inc.
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
public class BinderTest extends TestCase {

  Provider<Foo> fooProvider;

  public void testProviderFromBinder() {
    Guice.createInjector(new Module() {
      public void configure(Binder binder) {
        fooProvider = binder.getProvider(Foo.class);

        try {
          fooProvider.get();
        } catch (IllegalStateException e) { /* expected */ }
      }
    });

    assertTrue(fooProvider.get() instanceof Foo);
  }

  static class Foo {}

  public void testInvalidProviderFromBinder() {
    try {
      Guice.createInjector(new Module() {
        public void configure(Binder binder) {
          binder.getProvider(Runnable.class);
        }
      });
    }
    catch (CreationException e) {
      assertEquals(1, e.getErrorMessages().size());
    }
  }
}
