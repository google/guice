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

import java.util.List;
import java.util.Arrays;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class GenericInjectionTest extends TestCase {

  public void testGenericInjection() throws ContainerCreationException {
    List<String> names = Arrays.asList("foo", "bar", "bob");
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(new TypeLiteral<List<String>>() {}).to(names);
    Container container = builder.create(false);
    Foo foo = container.getFactory(Foo.class).get();
    assertEquals(names, foo.names);
  }

  static class Foo {
    @Inject List<String> names;
  }
}
