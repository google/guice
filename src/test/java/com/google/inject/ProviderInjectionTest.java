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
public class ProviderInjectionTest extends TestCase {

  public void testProviderInjection() throws CreationException {
    BinderImpl builder = new BinderImpl();

    builder.bind(Bar.class);
    builder.bind(ContainerScoped.class).in(Scopes.CONTAINER);

    Container container = builder.createContainer();

    Foo foo = container.getProvider(Foo.class).get();

    Bar bar = foo.barProvider.get();
    assertNotNull(bar);
    assertNotSame(bar, foo.barProvider.get());

    ContainerScoped containerScoped = foo.containerScopedProvider.get();
    assertNotNull(containerScoped);
    assertSame(containerScoped, foo.containerScopedProvider.get());
  }

  static class Foo {
    @Inject Provider<Bar> barProvider;
    @Inject Provider<ContainerScoped> containerScopedProvider;
  }

  static class Bar {}

  static class ContainerScoped {}
}
