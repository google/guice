/**
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

import static org.easymock.EasyMock.createControl;
import static org.easymock.EasyMock.expect;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.TestCase;

import org.easymock.IMocksControl;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

/** Tests to make sure that servlets with a context path are handled right. */
public class ContextPathTest extends TestCase {

  @Inject @Named("foo")
  private TestServlet fooServlet;

  @Inject @Named("bar") 
  private TestServlet barServlet;

  private IMocksControl globalControl;
  private Injector injector;
  private ServletContext servletContext;
  private FilterConfig filterConfig;
  private GuiceFilter guiceFilter;

  @Override
  public final void setUp() throws Exception {
    injector = Guice.createInjector(new ServletModule() {
      @Override
      protected void configureServlets() {
        bind(TestServlet.class).annotatedWith(Names.named("foo"))
            .to(TestServlet.class).in(Scopes.SINGLETON);
        bind(TestServlet.class).annotatedWith(Names.named("bar"))
            .to(TestServlet.class).in(Scopes.SINGLETON);
        serve("/foo/*").with(Key.get(TestServlet.class, Names.named("foo")));
        serve("/bar/*").with(Key.get(TestServlet.class, Names.named("bar")));
        // TODO: add a filter(..) call and validate it is correct
      }
    });
    injector.injectMembers(this);

    assertNotNull(fooServlet);
    assertNotNull(barServlet);
    assertNotSame(fooServlet, barServlet);

    globalControl = createControl();
    servletContext = globalControl.createMock(ServletContext.class);
    filterConfig = globalControl.createMock(FilterConfig.class);

    expect(servletContext.getAttribute(GuiceServletContextListener.INJECTOR_NAME))
        .andReturn(injector).anyTimes();
    expect(filterConfig.getServletContext()).andReturn(servletContext).anyTimes();

    globalControl.replay();

    guiceFilter = new GuiceFilter();
    guiceFilter.init(filterConfig);
  }

  @Override
  public final void tearDown() {
    assertNotNull(fooServlet);
    assertNotNull(barServlet);

    fooServlet = null;
    barServlet = null;

    guiceFilter.destroy();
    guiceFilter = null;

    injector = null;

    filterConfig = null;
    servletContext = null;

    globalControl.verify();
  }

  public void testSimple() throws Exception {
    IMocksControl testControl = createControl();
    TestFilterChain testFilterChain = new TestFilterChain();
    HttpServletRequest req = testControl.createMock(HttpServletRequest.class);
    HttpServletResponse res = testControl.createMock(HttpServletResponse.class);

    expect(req.getMethod()).andReturn("GET").anyTimes();
    expect(req.getRequestURI()).andReturn("/bar/foo").anyTimes();
    expect(req.getServletPath()).andReturn("/bar/foo").anyTimes();
    expect(req.getContextPath()).andReturn("").anyTimes();

    testControl.replay();

    guiceFilter.doFilter(req, res, testFilterChain);

    assertFalse(testFilterChain.isTriggered());
    assertFalse(fooServlet.isTriggered());
    assertTrue(barServlet.isTriggered());

    testControl.verify();
  }

  //
  // each of the following "runTest" calls takes three path parameters:
  //
  // The value of "getRequestURI()"
  // The value of "getServletPath()"
  // The value of "getContextPath()"
  //
  // these values have been captured using a filter in Apache Tomcat 6.0.32
  // and are used for real-world values that a servlet container would send into
  // the GuiceFilter.
  //
  // the remaining three booleans are:
  //
  // True if the request gets passed down the filter chain
  // True if the request hits the "foo" servlet
  // True if the request hits the "bar" sevlet
  //
  // After adjusting the request URI for the web app deployment location, all
  // calls
  // should always produce the same result.
  //

  // ROOT Web app, using Tomcat default servlet
  public void testRootDefault() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and /bar/*).
    runTest("/", "/", "", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/bar/", "/bar/", "", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/foo/xxx", "/foo/xxx", "", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/xxx", "/xxx", "", true, false, false);
  }

  // ROOT Web app, using explicit backing servlet mounted at /*
  public void testRootExplicit() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and /bar/*).
    runTest("/", "", "", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/bar/", "", "", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/foo/xxx", "", "", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/xxx", "", "", true, false, false);
  }

  // ROOT Web app, using two backing servlets, mounted at /bar/* and /foo/*
  public void testRootSpecific() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and /bar/*).
    runTest("/", "/", "", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/bar/", "/bar", "", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/foo/xxx", "/foo", "", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/xxx", "/xxx", "", true, false, false);
  }

  // Web app located at /webtest, using Tomcat default servlet
  public void testWebtestDefault() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and /bar/*).
    runTest("/webtest/", "/", "/webtest", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/webtest/bar/", "/bar/", "/webtest", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/webtest/foo/xxx", "/foo/xxx", "/webtest", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/webtest/xxx", "/xxx", "/webtest", true, false, false);
  }

  // Web app located at /webtest, using explicit backing servlet mounted at /*
  public void testWebtestExplicit() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and /bar/*).
    runTest("/webtest/", "", "/webtest", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/webtest/bar/", "", "/webtest", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/webtest/foo/xxx", "", "/webtest", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/webtest/xxx", "", "/webtest", true, false, false);
  }

  // Web app located at /webtest, using two backing servlets, mounted at /bar/*
  // and /foo/*
  public void testWebtestSpecific() throws Exception {
    // fetching /. Should go up the filter chain (only mappings on /foo/* and
    // /bar/*).
    runTest("/webtest/", "/", "/webtest", true, false, false);
    // fetching /bar/. Should hit the bar servlet
    runTest("/webtest/bar/", "/bar", "/webtest", false, false, true);
    // fetching /foo/xxx. Should hit the foo servlet
    runTest("/webtest/foo/xxx", "/foo", "/webtest", false, true, false);
    // fetching /xxx. Should go up the chain
    runTest("/webtest/xxx", "/xxx", "/webtest", true, false, false);
  }

  private void runTest(final String requestURI, final String servletPath, final String contextPath,
      final boolean filterResult, final boolean fooResult, final boolean barResult)
      throws Exception {
    IMocksControl testControl = createControl();

    barServlet.clear();
    fooServlet.clear();

    TestFilterChain testFilterChain = new TestFilterChain();
    HttpServletRequest req = testControl.createMock(HttpServletRequest.class);
    HttpServletResponse res = testControl.createMock(HttpServletResponse.class);

    expect(req.getMethod()).andReturn("GET").anyTimes();
    expect(req.getRequestURI()).andReturn(requestURI).anyTimes();
    expect(req.getServletPath()).andReturn(servletPath).anyTimes();
    expect(req.getContextPath()).andReturn(contextPath).anyTimes();

    testControl.replay();

    guiceFilter.doFilter(req, res, testFilterChain);

    assertEquals(filterResult, testFilterChain.isTriggered());
    assertEquals(fooResult, fooServlet.isTriggered());
    assertEquals(barResult, barServlet.isTriggered());

    testControl.verify();
  }

  public static class TestServlet extends HttpServlet {
    private boolean triggered = false;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) {
      triggered = true;
    }

    public boolean isTriggered() {
      return triggered;
    }

    public void clear() {
      triggered = false;
    }
  }

  public static class TestFilterChain implements FilterChain {
    private boolean triggered = false;

    public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
        ServletException {
      triggered = true;
    }

    public boolean isTriggered() {
      return triggered;
    }

    public void clear() {
      triggered = false;
    }
  }
}
