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
public class FactoryInjectionTest extends TestCase {

  public void testFactoryInjection() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();

    builder.bind(Bar.class);
    builder.bind(ContainerScoped.class).in(Scopes.CONTAINER);

    Container container = builder.create(false);

    Foo foo = container.getCreator(Foo.class).get();

    Bar bar = foo.barFactory.get();
    assertNotNull(bar);
    assertNotSame(bar, foo.barFactory.get());

    ContainerScoped containerScoped = foo.containerScopedFactory.get();
    assertNotNull(containerScoped);
    assertSame(containerScoped, foo.containerScopedFactory.get());
  }

  static class Foo {
    @Inject Factory<Bar> barFactory;
    @Inject Factory<ContainerScoped> containerScopedFactory;
  }

  static class Bar {}

  static class ContainerScoped {}
}
