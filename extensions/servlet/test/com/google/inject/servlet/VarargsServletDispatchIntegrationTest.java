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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Tests the FilterPipeline that dispatches to guice-managed servlets,
 * is a full integration test, with a real injector.
 *
 * @author Dhanji R. Prasanna (dhanji gmail com)
 */
public class VarargsServletDispatchIntegrationTest extends TestCase {
  private static int inits, services, destroys, doFilters;

  @Override
  public void setUp() {
    inits = 0;
    services = 0;
    destroys = 0;
    doFilters = 0;

    GuiceFilter.reset();
  }

  public final void testDispatchRequestToManagedPipelineServlets()
      throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        serve("/*", "/index.html").with(TestServlet.class);

        // These servets should never fire... (ordering test)
        serve("*.html", "/o/*", "/index/*", "*.jsp").with(Key.get(NeverServlet.class));
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
        .andReturn("/index.html")
        .times(1);
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);

    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));
    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not fire correct number of times-- inits: " + inits + "; dos: "
            + services + "; destroys: " + destroys,
        inits == 2 && services == 1 && destroys == 2);
  }

  public final void testVarargsSkipDispatchRequestToManagedPipelineServlets()
      throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        serve("/notindex", "/&*", "/index.html").with(TestServlet.class);

        // These servets should never fire... (ordering test)
        serve("*.html", "/*", "/index/*", "*.jsp").with(Key.get(NeverServlet.class));
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
        .andReturn("/index.html")
        .times(3);
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);

    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));
    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not fire correct number of times-- inits: " + inits + "; dos: "
            + services + "; destroys: " + destroys,
        inits == 2 && services == 1 && destroys == 2);
  }

  public final void testDispatchRequestToManagedPipelineWithFilter()
      throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        filter("/*").through(TestFilter.class);

        serve("/*").with(TestServlet.class);

        // These servets should never fire...
        serve("*.html", "/y/*", "/index/*", "*.jsp").with(Key.get(NeverServlet.class));

      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
        .andReturn("/index.html")
        .times(2);
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);

    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));

    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not fire correct number of times-- inits: " + inits + "; dos: "
            + services + "; destroys: " + destroys,
        inits == 3 && services == 1 && destroys == 3 && doFilters == 1);
  }

  @Singleton
  public static class TestServlet extends HttpServlet {
    public void init(ServletConfig filterConfig) throws ServletException {
      inits++;
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      services++;
    }

    public void destroy() {
      destroys++;
    }
  }

  @Singleton
  public static class NeverServlet extends HttpServlet {
    public void init(ServletConfig filterConfig) throws ServletException {
      inits++;
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      assertTrue("NeverServlet was fired, when it should not have been.", false);
    }

    public void destroy() {
      destroys++;
    }
  }

  @Singleton
  public static class TestFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
      inits++;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      doFilters++;
      filterChain.doFilter(servletRequest, servletResponse);
    }

    public void destroy() {
      destroys++;
    }
  }
}
