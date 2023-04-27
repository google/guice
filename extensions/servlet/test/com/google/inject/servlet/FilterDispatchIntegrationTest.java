/*
 * Copyright (C) 2011 Google Inc.
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
import static com.google.inject.servlet.ServletTestUtils.newFakeHttpServletRequest;
import static com.google.inject.servlet.ServletTestUtils.newNoOpFilterChain;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This tests that filter stage of the pipeline dispatches correctly to guice-managed filters.
 *
 * <p>WARNING(dhanji): Non-parallelizable test =(
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class FilterDispatchIntegrationTest {
  private static int inits, doFilters, destroys;

  @BeforeEach
  public final void setUp() {
    inits = 0;
    doFilters = 0;
    destroys = 0;
    GuiceFilter.reset();
  }

  @Test
  public final void testDispatchRequestToManagedPipeline() throws ServletException, IOException {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/*").through(TestFilter.class);
                filter("*.html").through(TestFilter.class);
                filter("/*").through(Key.get(TestFilter.class));

                // These filters should never fire
                filter("/index/*").through(Key.get(TestFilter.class));
                filter("*.jsp").through(Key.get(TestFilter.class));

                // Bind a servlet
                serve("*.html").with(TestServlet.class);
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html");
    when(requestMock.getContextPath()).thenReturn("");

    HttpServletResponse responseMock = mock(HttpServletResponse.class);
    when(responseMock.isCommitted()).thenReturn(false);

    FilterChain filterChain = mock(FilterChain.class);

    // dispatch request

    pipeline.dispatch(requestMock, responseMock, filterChain);
    pipeline.destroyPipeline();

    verify(requestMock).setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    verify(requestMock).removeAttribute(REQUEST_DISPATCHER_REQUEST);
    verify(responseMock).resetBuffer();

    TestServlet servlet = injector.getInstance(TestServlet.class);
    assertEquals(2, servlet.processedUris.size());
    assertTrue(servlet.processedUris.contains("/index.html"));
    assertTrue(servlet.processedUris.contains(TestServlet.FORWARD_TO));

    assertTrue(
        inits == 1 && doFilters == 3 && destroys == 1,
        "lifecycle states did not"
            + " fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys);
  }

  @Test
  public final void testDispatchThatNoFiltersFire() throws ServletException, IOException {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/public/*").through(TestFilter.class);
                filter("*.html").through(TestFilter.class);
                filter("*.xml").through(Key.get(TestFilter.class));

                // These filters should never fire
                filter("/index/*").through(Key.get(TestFilter.class));
                filter("*.jsp").through(Key.get(TestFilter.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.xhtml");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request
    FilterChain filterChain = mock(FilterChain.class);
    filterChain.doFilter(requestMock, null);

    pipeline.dispatch(requestMock, null, filterChain);
    pipeline.destroyPipeline();

    assertTrue(
        inits == 1 && doFilters == 0 && destroys == 1,
        "lifecycle states did not "
            + "fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys);
  }

  @Test
  public final void testDispatchFilterPipelineWithRegexMatching()
      throws ServletException, IOException {

    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filterRegex("/[A-Za-z]*").through(TestFilter.class);
                filterRegex("/index").through(TestFilter.class);
                //these filters should never fire
                filterRegex("\\w").through(Key.get(TestFilter.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request
    FilterChain filterChain = mock(FilterChain.class);
    filterChain.doFilter(requestMock, null);

    pipeline.dispatch(requestMock, null, filterChain);
    pipeline.destroyPipeline();

    assertTrue(
        inits == 1 && doFilters == 2 && destroys == 1,
        "lifecycle states did not fire "
            + "correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys);
  }

  @Singleton
  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {
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

  @Test
  public final void testFilterBypass() throws ServletException, IOException {

    final Injector injector =
        Guice.createInjector(
            new ServletModule() {
              @Override
              protected void configureServlets() {
                filter("/protected/*").through(TestFilter.class);
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);
    assertEquals(1, inits);

    runRequestForPath(pipeline, "/./protected/resource", true);
    runRequestForPath(pipeline, "/protected/../resource", false);
    runRequestForPath(pipeline, "/protected/../protected/resource", true);

    assertEquals(0, destroys);
    pipeline.destroyPipeline();
    assertEquals(1, destroys);
  }

  private void runRequestForPath(FilterPipeline pipeline, String value, boolean matches)
      throws IOException, ServletException {
    assertEquals(0, doFilters);
    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);
    when(requestMock.getRequestURI()).thenReturn(value);
    when(requestMock.getContextPath()).thenReturn("");
    // dispatch request
    FilterChain filterChain = mock(FilterChain.class);
    pipeline.dispatch(requestMock, null, filterChain);
    verify(filterChain).doFilter(requestMock, null);

    if (matches) {
      assertEquals(1, doFilters, "filter was not run");
      doFilters = 0;
    } else {
      assertEquals(0, doFilters, "filter was run");
    }
  }

  @Singleton
  public static class TestServlet extends HttpServlet {
    public static final String FORWARD_FROM = "/index.html";
    public static final String FORWARD_TO = "/forwarded.html";
    public List<String> processedUris = new ArrayList<>();

    @Override
    protected void service(
        HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      String requestUri = httpServletRequest.getRequestURI();
      processedUris.add(requestUri);

      // If the client is requesting /index.html then we forward to /forwarded.html
      if (FORWARD_FROM.equals(requestUri)) {
        httpServletRequest
            .getRequestDispatcher(FORWARD_TO)
            .forward(httpServletRequest, httpServletResponse);
      }
    }

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws ServletException, IOException {
      service((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
    }
  }

  @Test
  public void testFilterOrder() throws Exception {
    AtomicInteger counter = new AtomicInteger();
    final CountFilter f1 = new CountFilter(counter);
    final CountFilter f2 = new CountFilter(counter);

    Injector injector =
        Guice.createInjector(
            new ServletModule() {
              @Override
              protected void configureServlets() {
                filter("/").through(f1);
                install(
                    new ServletModule() {
                      @Override
                      protected void configureServlets() {
                        filter("/").through(f2);
                      }
                    });
              }
            });

    HttpServletRequest request = newFakeHttpServletRequest();
    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);
    pipeline.dispatch(request, null, newNoOpFilterChain());
    assertEquals(0, f1.calledAt);
    assertEquals(1, f2.calledAt);
  }

  /** A filter that keeps count of when it was called by increment a counter. */
  private static class CountFilter implements Filter {
    private final AtomicInteger counter;
    private int calledAt = -1;

    public CountFilter(AtomicInteger counter) {
      this.counter = counter;
    }

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      if (calledAt != -1) {
        fail("not expecting to be called twice");
      }
      calledAt = counter.getAndIncrement();
      chain.doFilter(request, response);
    }

    @Override
    public void init(FilterConfig filterConfig) {}
  }

  @Test
  public final void testFilterExceptionPrunesStack() throws Exception {
    Injector injector =
        Guice.createInjector(
            new ServletModule() {
              @Override
              protected void configureServlets() {
                filter("/").through(TestFilter.class);
                filter("/nothing").through(TestFilter.class);
                filter("/").through(ThrowingFilter.class);
              }
            });

    HttpServletRequest request = newFakeHttpServletRequest();
    FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);
    try {
      pipeline.dispatch(request, null, null);
      fail("expected exception");
    } catch (ServletException ex) {
      for (StackTraceElement element : ex.getStackTrace()) {
        String className = element.getClassName();
        assertTrue(
            !className.equals(FilterChainInvocation.class.getName())
                && !className.equals(FilterDefinition.class.getName()),
            "was: " + element);
      }
    }
  }

  @Test
  public final void testServletExceptionPrunesStack() throws Exception {
    Injector injector =
        Guice.createInjector(
            new ServletModule() {
              @Override
              protected void configureServlets() {
                filter("/").through(TestFilter.class);
                filter("/nothing").through(TestFilter.class);
                serve("/").with(ThrowingServlet.class);
              }
            });

    HttpServletRequest request = newFakeHttpServletRequest();
    FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);
    try {
      pipeline.dispatch(request, null, null);
      fail("expected exception");
    } catch (ServletException ex) {
      for (StackTraceElement element : ex.getStackTrace()) {
        String className = element.getClassName();
        assertTrue(
            !className.equals(FilterChainInvocation.class.getName())
                && !className.equals(FilterDefinition.class.getName()),
            "was: " + element);
      }
    }
  }

  @Singleton
  private static class ThrowingServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException {
      throw new ServletException("failure!");
    }
  }

  @Singleton
  private static class ThrowingFilter implements Filter {
    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws ServletException {
      throw new ServletException("we failed!");
    }

    @Override
    public void init(FilterConfig filterConfig) {}
  }
}
