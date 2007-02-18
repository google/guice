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

  public void testProvided() throws CreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(Bar.class).to(BarImpl.class);
    Container c = builder.create();
    Foo foo = c.getLocator(Foo.class).get();
    assertNotNull(foo.bar);
    assertNotNull(foo.fromMethod);
  }

  public void testNotProvided() throws CreationException {
    Container c = new ContainerBuilder().create();
    Foo foo = c.getLocator(Foo.class).get();
    assertNull(foo.bar);
    assertNull(foo.fromMethod);
  }

  static class Foo {
    @Inject(optional=true) Bar bar;

    Bar fromMethod;

    @Inject(optional=true) void setBar(Bar bar) {
      fromMethod = bar;
    }
  }

  interface Bar {}

  static class BarImpl implements Bar {}
}
