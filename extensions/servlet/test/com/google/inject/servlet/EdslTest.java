/**
 * Copyright (C) 2008 Google Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import java.util.HashMap;
import junit.framework.TestCase;

/**
 * Sanity checks the EDSL and resultant bound module(s).
 *
 * @author Dhanji R. Prasanna (dhanji gmail com)
 */
public class EdslTest extends TestCase {

  public final void testExplicitBindingsWorksWithGuiceServlet() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            binder().requireExplicitBindings();
          }
        }, new ServletModule() {
          @Override protected void configureServlets() {
            bind(DummyServlet.class).in(Singleton.class);
            serve("/*").with(DummyServlet.class);
          }
        });

    assertNotNull(injector.getInstance(DummyServlet.class));
  }

  public final void testConfigureServlets() {

    //the various possible config calls--
    Module webModule = new ServletModule() {

      @Override
      protected void configureServlets() {
        filter("/*").through(DummyFilterImpl.class);
        filter("*.html").through(DummyFilterImpl.class);
        filter("/*").through(Key.get(DummyFilterImpl.class));
        filter("/*").through(new DummyFilterImpl());

        filter("*.html").through(DummyFilterImpl.class,
            new HashMap<String, String>());

        filterRegex("/person/[0-9]*").through(DummyFilterImpl.class);
        filterRegex("/person/[0-9]*").through(DummyFilterImpl.class,
            new HashMap<String, String>());

        filterRegex("/person/[0-9]*").through(Key.get(DummyFilterImpl.class));
        filterRegex("/person/[0-9]*").through(Key.get(DummyFilterImpl.class),
            new HashMap<String, String>());

        filterRegex("/person/[0-9]*").through(new DummyFilterImpl());
        filterRegex("/person/[0-9]*").through(new DummyFilterImpl(),
            new HashMap<String, String>());


        serve("/1/*").with(DummyServlet.class);
        serve("/2/*").with(Key.get(DummyServlet.class));
        serve("/3/*").with(new DummyServlet());
        serve("/4/*").with(DummyServlet.class, new HashMap<String, String>());

        serve("*.htm").with(Key.get(DummyServlet.class));
        serve("*.html").with(Key.get(DummyServlet.class),
            new HashMap<String, String>());

        serveRegex("/person/[0-8]*").with(DummyServlet.class);
        serveRegex("/person/[0-9]*").with(DummyServlet.class,
            new HashMap<String, String>());

        serveRegex("/person/[0-6]*").with(Key.get(DummyServlet.class));
        serveRegex("/person/[0-9]/2/*").with(Key.get(DummyServlet.class),
            new HashMap<String, String>());

        serveRegex("/person/[0-5]*").with(new DummyServlet());
        serveRegex("/person/[0-9]/3/*").with(new DummyServlet(),
            new HashMap<String, String>());
      }
    };

    //verify that it doesn't blow up!
    Guice.createInjector(Stage.TOOL, webModule);
  }
}
