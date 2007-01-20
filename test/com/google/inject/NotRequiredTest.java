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
public class NotRequiredTest extends TestCase {

  public void testProvided() {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(Bar.class);
    Container c = builder.create(false);
    Foo foo = c.inject(Foo.class);
    assertNotNull(foo.bar);
    assertNotNull(foo.fromMethod);
  }

  public void testNotProvided() {
    Container c = new ContainerBuilder()
        .create(false);
    Foo foo = c.inject(Foo.class);
    assertNull(foo.bar);
    assertNull(foo.fromMethod);
  }

  static class Foo {
    @Inject(required=false) Bar bar;

    Bar fromMethod;

    @Inject(required=false) void setBar(Bar bar) {
      fromMethod = bar;
    }
  }

  static class Bar {}
}
