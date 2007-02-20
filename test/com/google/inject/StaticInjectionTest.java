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

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class StaticInjectionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface I {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface S {}

  public void testInjectStatics() throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant(S.class).to("test");
    builder.bindConstant(I.class).to(5);
    builder.requestStaticInjection(StaticInjectionTest.Static.class);

    Container c = builder.createContainer();

    assertEquals("test", StaticInjectionTest.Static.s);
    assertEquals(5, StaticInjectionTest.Static.i);
  }

  static class Static {

    @Inject @I static int i;

    static String s;

    @Inject static void setS(@S String s) {
      StaticInjectionTest.Static.s = s;
    }
  }
}
