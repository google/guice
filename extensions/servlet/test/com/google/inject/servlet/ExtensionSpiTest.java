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

import static com.google.inject.servlet.UriPatternType.REGEX;
import static com.google.inject.servlet.UriPatternType.SERVLET;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.inject.servlet.ServletSpiVisitor.Params;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import java.util.Iterator;
import java.util.List;
import junit.framework.TestCase;

/**
 * A very basic test that servletmodule works with bindings.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class ExtensionSpiTest extends TestCase {

  private DummyFilterImpl dummyFilter1 = new DummyFilterImpl();
  private DummyFilterImpl dummyFilter2 = new DummyFilterImpl();
  private DummyFilterImpl dummyFilter3 = new DummyFilterImpl();
  private DummyFilterImpl dummyFilter4 = new DummyFilterImpl();

  private DummyServlet dummyServlet1 = new DummyServlet();
  private DummyServlet dummyServlet2 = new DummyServlet();
  private DummyServlet dummyServlet3 = new DummyServlet();
  private DummyServlet dummyServlet4 = new DummyServlet();

  public final void testSpiOnElements() {
    ServletSpiVisitor visitor = new ServletSpiVisitor(false);
    int count = 0;
    for (Element element : Elements.getElements(new Module())) {
      if (element instanceof Binding) {
        int actual = ((Binding<?>) element).acceptTargetVisitor(visitor);
        assertEquals(count++, actual);
      }
    }
    validateVisitor(visitor);
  }

  public final void testSpiOnInjector() {
    ServletSpiVisitor visitor = new ServletSpiVisitor(true);
    int count = 0;
    Injector injector = Guice.createInjector(new Module());
    for (Binding<?> binding : injector.getBindings().values()) {
      int actual = binding.acceptTargetVisitor(visitor);
      assertEquals(count++, actual);
    }
    validateVisitor(visitor);
  }

  private void validateVisitor(ServletSpiVisitor visitor) {
    assertEquals(48, visitor.currentCount - visitor.otherCount);

    // This is the expected param list, in order..
    List<Params> expected =
        ImmutableList.of(
            new Params("/class", Key.get(DummyFilterImpl.class), ImmutableMap.of(), SERVLET),
            new Params("/class/2", Key.get(DummyFilterImpl.class), ImmutableMap.of(), SERVLET),
            new Params(
                "/key",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of(),
                SERVLET),
            new Params(
                "/key/2",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of(),
                SERVLET),
            new Params("/instance", dummyFilter1, ImmutableMap.of(), SERVLET),
            new Params("/instance/2", dummyFilter1, ImmutableMap.of(), SERVLET),
            new Params(
                "/class/keyvalues",
                Key.get(DummyFilterImpl.class),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/class/keyvalues/2",
                Key.get(DummyFilterImpl.class),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/key/keyvalues",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/key/keyvalues/2",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/instance/keyvalues", dummyFilter2, ImmutableMap.of("key", "value"), SERVLET),
            new Params(
                "/instance/keyvalues/2", dummyFilter2, ImmutableMap.of("key", "value"), SERVLET),
            new Params("/class[0-9]", Key.get(DummyFilterImpl.class), ImmutableMap.of(), REGEX),
            new Params("/class[0-9]/2", Key.get(DummyFilterImpl.class), ImmutableMap.of(), REGEX),
            new Params(
                "/key[0-9]",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of(),
                REGEX),
            new Params(
                "/key[0-9]/2",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of(),
                REGEX),
            new Params("/instance[0-9]", dummyFilter3, ImmutableMap.of(), REGEX),
            new Params("/instance[0-9]/2", dummyFilter3, ImmutableMap.of(), REGEX),
            new Params(
                "/class[0-9]/keyvalues",
                Key.get(DummyFilterImpl.class),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/class[0-9]/keyvalues/2",
                Key.get(DummyFilterImpl.class),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/key[0-9]/keyvalues",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/key[0-9]/keyvalues/2",
                Key.get(DummyFilterImpl.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/instance[0-9]/keyvalues", dummyFilter4, ImmutableMap.of("key", "value"), REGEX),
            new Params(
                "/instance[0-9]/keyvalues/2", dummyFilter4, ImmutableMap.of("key", "value"), REGEX),
            new Params("/class", Key.get(DummyServlet.class), ImmutableMap.of(), SERVLET),
            new Params("/class/2", Key.get(DummyServlet.class), ImmutableMap.of(), SERVLET),
            new Params(
                "/key",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of(),
                SERVLET),
            new Params(
                "/key/2",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of(),
                SERVLET),
            new Params("/instance", dummyServlet1, ImmutableMap.of(), SERVLET),
            new Params("/instance/2", dummyServlet1, ImmutableMap.of(), SERVLET),
            new Params(
                "/class/keyvalues",
                Key.get(DummyServlet.class),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/class/keyvalues/2",
                Key.get(DummyServlet.class),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/key/keyvalues",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/key/keyvalues/2",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                SERVLET),
            new Params(
                "/instance/keyvalues", dummyServlet2, ImmutableMap.of("key", "value"), SERVLET),
            new Params(
                "/instance/keyvalues/2", dummyServlet2, ImmutableMap.of("key", "value"), SERVLET),
            new Params("/class[0-9]", Key.get(DummyServlet.class), ImmutableMap.of(), REGEX),
            new Params("/class[0-9]/2", Key.get(DummyServlet.class), ImmutableMap.of(), REGEX),
            new Params(
                "/key[0-9]",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of(),
                REGEX),
            new Params(
                "/key[0-9]/2",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of(),
                REGEX),
            new Params("/instance[0-9]", dummyServlet3, ImmutableMap.of(), REGEX),
            new Params("/instance[0-9]/2", dummyServlet3, ImmutableMap.of(), REGEX),
            new Params(
                "/class[0-9]/keyvalues",
                Key.get(DummyServlet.class),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/class[0-9]/keyvalues/2",
                Key.get(DummyServlet.class),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/key[0-9]/keyvalues",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/key[0-9]/keyvalues/2",
                Key.get(DummyServlet.class, Names.named("foo")),
                ImmutableMap.of("key", "value"),
                REGEX),
            new Params(
                "/instance[0-9]/keyvalues", dummyServlet4, ImmutableMap.of("key", "value"), REGEX),
            new Params(
                "/instance[0-9]/keyvalues/2",
                dummyServlet4,
                ImmutableMap.of("key", "value"),
                REGEX));

    assertEquals(expected.size(), visitor.actual.size());
    Iterator<Params> actualIterator = visitor.actual.iterator();
    int i = 0;
    for (Params param : expected) {
      assertEquals("wrong " + i++ + "th param", param, actualIterator.next());
    }
  }

  private class Module extends ServletModule {
    @Override
    protected void configureServlets() {
      binder().requireExplicitBindings();

      filter("/class", "/class/2").through(DummyFilterImpl.class);
      filter("/key", "/key/2").through(Key.get(DummyFilterImpl.class, Names.named("foo")));
      filter("/instance", "/instance/2").through(dummyFilter1);
      filter("/class/keyvalues", "/class/keyvalues/2")
          .through(DummyFilterImpl.class, ImmutableMap.of("key", "value"));
      filter("/key/keyvalues", "/key/keyvalues/2")
          .through(
              Key.get(DummyFilterImpl.class, Names.named("foo")), ImmutableMap.of("key", "value"));
      filter("/instance/keyvalues", "/instance/keyvalues/2")
          .through(dummyFilter2, ImmutableMap.of("key", "value"));

      filterRegex("/class[0-9]", "/class[0-9]/2").through(DummyFilterImpl.class);
      filterRegex("/key[0-9]", "/key[0-9]/2")
          .through(Key.get(DummyFilterImpl.class, Names.named("foo")));
      filterRegex("/instance[0-9]", "/instance[0-9]/2").through(dummyFilter3);
      filterRegex("/class[0-9]/keyvalues", "/class[0-9]/keyvalues/2")
          .through(DummyFilterImpl.class, ImmutableMap.of("key", "value"));
      filterRegex("/key[0-9]/keyvalues", "/key[0-9]/keyvalues/2")
          .through(
              Key.get(DummyFilterImpl.class, Names.named("foo")), ImmutableMap.of("key", "value"));
      filterRegex("/instance[0-9]/keyvalues", "/instance[0-9]/keyvalues/2")
          .through(dummyFilter4, ImmutableMap.of("key", "value"));

      serve("/class", "/class/2").with(DummyServlet.class);
      serve("/key", "/key/2").with(Key.get(DummyServlet.class, Names.named("foo")));
      serve("/instance", "/instance/2").with(dummyServlet1);
      serve("/class/keyvalues", "/class/keyvalues/2")
          .with(DummyServlet.class, ImmutableMap.of("key", "value"));
      serve("/key/keyvalues", "/key/keyvalues/2")
          .with(Key.get(DummyServlet.class, Names.named("foo")), ImmutableMap.of("key", "value"));
      serve("/instance/keyvalues", "/instance/keyvalues/2")
          .with(dummyServlet2, ImmutableMap.of("key", "value"));

      serveRegex("/class[0-9]", "/class[0-9]/2").with(DummyServlet.class);
      serveRegex("/key[0-9]", "/key[0-9]/2").with(Key.get(DummyServlet.class, Names.named("foo")));
      serveRegex("/instance[0-9]", "/instance[0-9]/2").with(dummyServlet3);
      serveRegex("/class[0-9]/keyvalues", "/class[0-9]/keyvalues/2")
          .with(DummyServlet.class, ImmutableMap.of("key", "value"));
      serveRegex("/key[0-9]/keyvalues", "/key[0-9]/keyvalues/2")
          .with(Key.get(DummyServlet.class, Names.named("foo")), ImmutableMap.of("key", "value"));
      serveRegex("/instance[0-9]/keyvalues", "/instance[0-9]/keyvalues/2")
          .with(dummyServlet4, ImmutableMap.of("key", "value"));
    }
  }
}
