/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.servlet;

import com.google.common.collect.Lists;
import com.google.inject.Binding;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Elements;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests for ServletModule, to ensure it captures bindings correctly.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class ServletModuleTest extends TestCase {

  public void testServletModuleCallOutsideConfigure() {
    try {
      new ServletModule() {
        {
          serve("/*").with(DummyServlet.class);
        }
      };
      fail();
    } catch (IllegalStateException e) {
      // Expected.
    }
  }

  public void testServletModuleReuse() {
    Module module = new Module();
    Elements.getElements(module); // use the module once (to, say, introspect bindings)
    Injector injector = Guice.createInjector(module); // use it again.

    Visitor visitor = new Visitor();
    // Validate only a single servlet binding & a single filter binding exist.
    for (Binding<?> binding : injector.getAllBindings().values()) {
      binding.acceptTargetVisitor(visitor);
    }
    assertEquals(
        "wrong linked servlets: " + visitor.linkedServlets, 0, visitor.linkedServlets.size());
    assertEquals("wrong linked filters: " + visitor.linkedFilters, 0, visitor.linkedFilters.size());
    assertEquals(
        "wrong instance servlets: " + visitor.instanceServlets, 1, visitor.instanceServlets.size());
    assertEquals(
        "wrong instance filters: " + visitor.instanceFilters, 1, visitor.instanceFilters.size());
  }

  public void testServletModule_badPattern() {
    try {
      Guice.createInjector(
          new ServletModule() {
            @Override
            protected void configureServlets() {
              serve("/%2E/*").with(new DummyServlet());
              serveRegex("/(foo|bar/").with(new DummyServlet());
              filter("/%2E/*").through(new DummyFilterImpl());
              filterRegex("/(foo|bar/").through(new DummyFilterImpl());
            }
          });
      fail();
    } catch (CreationException e) {
      assertEquals(4, e.getErrorMessages().size());
    }
  }

  private static class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      serve("/sam/*").with(new DummyServlet());
      filter("/tara/*").through(new DummyFilterImpl());
    }
  }

  private static class Visitor extends DefaultBindingTargetVisitor<Object, Void>
      implements ServletModuleTargetVisitor<Object, Void> {
    List<LinkedFilterBinding> linkedFilters = Lists.newArrayList();
    List<LinkedServletBinding> linkedServlets = Lists.newArrayList();
    List<InstanceFilterBinding> instanceFilters = Lists.newArrayList();
    List<InstanceServletBinding> instanceServlets = Lists.newArrayList();

    @Override
    public Void visit(LinkedFilterBinding binding) {
      linkedFilters.add(binding);
      return null;
    }

    @Override
    public Void visit(InstanceFilterBinding binding) {
      instanceFilters.add(binding);
      return null;
    }

    @Override
    public Void visit(LinkedServletBinding binding) {
      linkedServlets.add(binding);
      return null;
    }

    @Override
    public Void visit(InstanceServletBinding binding) {
      instanceServlets.add(binding);
      return null;
    }
  }
}
