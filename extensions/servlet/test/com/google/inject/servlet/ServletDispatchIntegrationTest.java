/*
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

import static com.google.inject.servlet.ManagedServletPipeline.REQUEST_DISPATCHER_REQUEST;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

/**
 * Tests the FilterPipeline that dispatches to guice-managed servlets, is a full integration test,
 * with a real injector.
 *
 * @author Dhanji R. Prasanna (dhanji gmail com)
 */
public class ServletDispatchIntegrationTest extends TestCase {
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
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                serve("/*").with(TestServlet.class);

                // These servets should never fire... (ordering test)
                serve("*.html").with(NeverServlet.class);
                serve("/test/*").with(Key.get(NeverServlet.class));
                serve("/index/*").with(Key.get(NeverServlet.class));
                serve("*.jsp").with(Key.get(NeverServlet.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));

    pipeline.destroyPipeline();

    assertTrue(
        "lifecycle states did not fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + services
            + "; destroys: "
            + destroys,
        inits == 2 && services == 1 && destroys == 2);
  }

  public final void testDispatchRequestToManagedPipelineWithFilter()
      throws ServletException, IOException {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/*").through(TestFilter.class);

                serve("/*").with(TestServlet.class);

                // These servets should never fire...
                serve("*.html").with(NeverServlet.class);
                serve("/test/*").with(Key.get(NeverServlet.class));
                serve("/index/*").with(Key.get(NeverServlet.class));
                serve("*.jsp").with(Key.get(NeverServlet.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));

    pipeline.destroyPipeline();

    assertTrue(
        "lifecycle states did not fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + services
            + "; destroys: "
            + destroys
            + "; doFilters: "
            + doFilters,
        inits == 3 && services == 1 && destroys == 3 && doFilters == 1);
  }

  @Singleton
  public static class TestServlet extends HttpServlet {
    @Override
    public void init(ServletConfig filterConfig) throws ServletException {
      inits++;
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      services++;
    }

    @Override
    public void destroy() {
      destroys++;
    }
  }

  @Singleton
  public static class NeverServlet extends HttpServlet {
    @Override
    public void init(ServletConfig filterConfig) throws ServletException {
      inits++;
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      fail("NeverServlet was fired, when it should not have been.");
    }

    @Override
    public void destroy() {
      destroys++;
    }
  }

  @Singleton
  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
      inits++;
    }

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      doFilters++;
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
      destroys++;
    }
  }

  @Singleton
  public static class ForwardingServlet extends HttpServlet {
    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      final HttpServletRequest request = (HttpServletRequest) servletRequest;

      request.getRequestDispatcher("/blah.jsp").forward(servletRequest, servletResponse);
    }
  }

  @Singleton
  public static class ForwardedServlet extends HttpServlet {
    static int forwardedTo = 0;

    // Reset for test.
    public ForwardedServlet() {
      forwardedTo = 0;
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws IOException, ServletException {
      final HttpServletRequest request = (HttpServletRequest) servletRequest;

      assertTrue((Boolean) request.getAttribute(REQUEST_DISPATCHER_REQUEST));
      forwardedTo++;
    }
  }

  public void testForwardUsingRequestDispatcher() throws IOException, ServletException {
    Guice.createInjector(
        new ServletModule() {
          @Override
          protected void configureServlets() {
            serve("/").with(ForwardingServlet.class);
            serve("/blah.jsp").with(ForwardedServlet.class);
          }
        });

    final HttpServletRequest requestMock = mock(HttpServletRequest.class);
    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    when(requestMock.getRequestURI()).thenReturn("/");
    when(requestMock.getContextPath()).thenReturn("");

    when(requestMock.getAttribute(REQUEST_DISPATCHER_REQUEST)).thenReturn(true);

    when(responseMock.isCommitted()).thenReturn(false);

    new GuiceFilter().doFilter(requestMock, responseMock, mock(FilterChain.class));

    assertEquals("Incorrect number of forwards", 1, ForwardedServlet.forwardedTo);

    verify(requestMock).setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    verify(requestMock).removeAttribute(REQUEST_DISPATCHER_REQUEST);
    verify(responseMock).resetBuffer();
  }

  public final void testQueryInRequestUri_regex() throws Exception {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filterRegex("(.)*\\.html").through(TestFilter.class);

                serveRegex("(.)*\\.html").with(TestServlet.class);
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html?query=params");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));

    pipeline.destroyPipeline();

    assertEquals(1, doFilters);
    assertEquals(1, services);
  }

  public final void testQueryInRequestUri() throws Exception {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/index.html").through(TestFilter.class);

                serve("/index.html").with(TestServlet.class);
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);

    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html?query=params");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));

    pipeline.destroyPipeline();

    assertEquals(1, doFilters);
    assertEquals(1, services);
  }
}
