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
public class StaticInjectionTest extends TestCase {

  public void testInjectStatics() {
    Container c = new ContainerBuilder()
        .constant("s", "test")
        .constant("i", 5)
        .injectStatics(StaticInjectionTest.Static.class)
        .create(false);

    assertEquals("test", StaticInjectionTest.Static.s);
    assertEquals(5, StaticInjectionTest.Static.i);
  }

  static class Static {

    @Inject("i") static int i;

    static String s;

    @Inject("s") static void setS(String s) {
      StaticInjectionTest.Static.s = s;
    }
  }
}
