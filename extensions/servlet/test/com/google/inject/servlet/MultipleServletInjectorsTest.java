/**
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

import com.google.inject.Guice;
import com.google.inject.Injector;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.http.HttpServlet;
import junit.framework.TestCase;

import static com.google.inject.servlet.GuiceServletContextListener.INJECTOR_NAME;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * This gorgeous test asserts that multiple servlet pipelines can
 * run in the SAME JVM. booya.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class MultipleServletInjectorsTest extends TestCase {

  private Injector injectorOne;
  private Injector injectorTwo;

  public final void testTwoInjectors() {
    ServletContext fakeContextOne = createMock(ServletContext.class);
    ServletContext fakeContextTwo = createMock(ServletContext.class);

    fakeContextOne.setAttribute(eq(INJECTOR_NAME), isA(Injector.class));
    expectLastCall().once();

    fakeContextTwo.setAttribute(eq(INJECTOR_NAME), isA(Injector.class));
    expectLastCall().once();

    replay(fakeContextOne);

    // Simulate the start of a servlet container.
    new GuiceServletContextListener() {

      @Override
      protected Injector getInjector() {
        // Cache this injector in the test for later testing...
        return injectorOne = Guice.createInjector(new ServletModule() {

          @Override
          protected void configureServlets() {
            // This creates a ManagedFilterPipeline internally...
            serve("/*").with(DummyServlet.class);
          }
        });
      }
    }.contextInitialized(new ServletContextEvent(fakeContextOne));

    ServletContext contextOne = injectorOne.getInstance(ServletContext.class);
    assertNotNull(contextOne);

    // Now simulate a second injector with a slightly different config.
    replay(fakeContextTwo);
    new GuiceServletContextListener() {

      @Override
      protected Injector getInjector() {
        return injectorTwo = Guice.createInjector(new ServletModule() {

          @Override
          protected void configureServlets() {
            // This creates a ManagedFilterPipeline internally...
            filter("/8").through(DummyFilterImpl.class);

            serve("/*").with(HttpServlet.class);
          }
        });
      }
    }.contextInitialized(new ServletContextEvent(fakeContextTwo));

    ServletContext contextTwo = injectorTwo.getInstance(ServletContext.class);

    // Make sure they are different.
    assertNotNull(contextTwo);
    assertNotSame(contextOne, contextTwo);

    // Make sure they are as expected
    assertSame(fakeContextOne, contextOne);
    assertSame(fakeContextTwo, contextTwo);

    // Make sure they are consistent.
    assertSame(contextOne, injectorOne.getInstance(ServletContext.class));
    assertSame(contextTwo, injectorTwo.getInstance(ServletContext.class));

    verify(fakeContextOne, fakeContextTwo);
  }
}
