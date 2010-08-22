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

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import com.google.inject.spi.BindingScopingVisitor;
import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

import static com.google.inject.servlet.ManagedServletPipeline.REQUEST_DISPATCHER_REQUEST;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Ensures servlet spec compliance for CGI-style variables and general
 *  path/pattern matching.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class ServletDefinitionPathsTest extends TestCase {

  // Data-driven test.
  public final void testServletPathMatching() throws IOException, ServletException {
    servletPath("/index.html", "*.html", "/index.html");
    servletPath("/somewhere/index.html", "*.html", "/somewhere/index.html");
    servletPath("/somewhere/index.html", "/*", "");
    servletPath("/index.html", "/*", "");
    servletPath("/", "/*", "");
    servletPath("//", "/*", "");
    servletPath("/////", "/*", "");
    servletPath("", "/*", "");
    servletPath("/thing/index.html", "/thing/*", "/thing");
    servletPath("/thing/wing/index.html", "/thing/*", "/thing");
  }

  private void servletPath(final String requestPath, String mapping,
      final String expectedServletPath) throws IOException, ServletException {

    Injector injector = createMock(Injector.class);
    Binding binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpServletResponse response = createMock(HttpServletResponse.class);

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);

    final boolean[] run = new boolean[1];
    //get an instance of this servlet
    expect(injector.getInstance(Key.get(HttpServlet.class)))
        .andReturn(new HttpServlet() {

          @Override
          protected void service(HttpServletRequest servletRequest,
              HttpServletResponse httpServletResponse) throws ServletException, IOException {

            final String path = servletRequest.getServletPath();
            assertEquals(String.format("expected [%s] but was [%s]", expectedServletPath, path),
                expectedServletPath, path);
            run[0] = true;
          }
        });

    expect(request.getServletPath())
        .andReturn(requestPath);

    replay(injector, binding, request);

    ServletDefinition servletDefinition = new ServletDefinition(mapping, Key.get(HttpServlet.class),
        UriPatternType.get(UriPatternType.SERVLET, mapping), new HashMap<String, String>(), null);

    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));
    servletDefinition.doService(request, response);

    assertTrue("Servlet did not run!", run[0]);
    
    verify(injector, binding, request);

  }

  // Data-driven test.
  public final void testPathInfoWithServletStyleMatching() throws IOException, ServletException {
    pathInfoWithServletStyleMatching("/path/index.html", "/path", "/*", "/index.html", "");
    pathInfoWithServletStyleMatching("/path//hulaboo///index.html", "/path", "/*",
        "/hulaboo/index.html", "");
    pathInfoWithServletStyleMatching("/path/", "/path", "/*", "/", "");
    pathInfoWithServletStyleMatching("/path////////", "/path", "/*", "/", "");

    // a servlet mapping of /thing/*
    pathInfoWithServletStyleMatching("/path/thing////////", "/path", "/thing/*", "/", "/thing");
    pathInfoWithServletStyleMatching("/path/thing/stuff", "/path", "/thing/*", "/stuff", "/thing");
    pathInfoWithServletStyleMatching("/path/thing/stuff.html", "/path", "/thing/*", "/stuff.html",
        "/thing");
    pathInfoWithServletStyleMatching("/path/thing", "/path", "/thing/*", null, "/thing");

    // *.xx style mapping
    pathInfoWithServletStyleMatching("/path/thing.thing", "/path", "*.thing", null, "/thing.thing");
    pathInfoWithServletStyleMatching("/path///h.thing", "/path", "*.thing", null, "/h.thing");
    pathInfoWithServletStyleMatching("/path///...//h.thing", "/path", "*.thing", null,
        "/.../h.thing");
    pathInfoWithServletStyleMatching("/path/my/h.thing", "/path", "*.thing", null, "/my/h.thing");

  }

  private void pathInfoWithServletStyleMatching(final String requestUri, final String contextPath,
      String mapping, final String expectedPathInfo, final String servletPath)
      throws IOException, ServletException {

    Injector injector = createMock(Injector.class);
    Binding binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpServletResponse response = createMock(HttpServletResponse.class);

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);

    final boolean[] run = new boolean[1];
    //get an instance of this servlet
    expect(injector.getInstance(Key.get(HttpServlet.class)))
        .andReturn(new HttpServlet() {

          @Override
          protected void service(HttpServletRequest servletRequest,
              HttpServletResponse httpServletResponse) throws ServletException, IOException {

            final String path = servletRequest.getPathInfo();

            if (null == expectedPathInfo) {
              assertNull(String.format("expected [%s] but was [%s]", expectedPathInfo, path),
                  path);
            }
            else {
              assertEquals(String.format("expected [%s] but was [%s]", expectedPathInfo, path),
                  expectedPathInfo, path);
            }

            //assert memoizer
            //noinspection StringEquality
            assertSame("memo field did not work", path, servletRequest.getPathInfo());

            run[0] = true;
          }
        });

    expect(request.getRequestURI())
        .andReturn(requestUri);

    expect(request.getServletPath())
        .andReturn(servletPath)
        .anyTimes();

    expect(request.getContextPath())
        .andReturn(contextPath);

    expect(request.getAttribute(REQUEST_DISPATCHER_REQUEST)).andReturn(null);

    replay(injector, binding, request);

    ServletDefinition servletDefinition = new ServletDefinition(mapping, Key.get(HttpServlet.class),
        UriPatternType.get(UriPatternType.SERVLET, mapping), new HashMap<String, String>(), null);

    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));
    servletDefinition.doService(request, response);

    assertTrue("Servlet did not run!", run[0]);

    verify(injector, binding, request);
  }

  // Data-driven test.
  public final void testPathInfoWithRegexMatching() throws IOException, ServletException {
    // first a mapping of /*
    pathInfoWithRegexMatching("/path/index.html", "/path", "/(.)*", "/index.html", "");
    pathInfoWithRegexMatching("/path//hulaboo///index.html", "/path", "/(.)*",
        "/hulaboo/index.html", "");
    pathInfoWithRegexMatching("/path/", "/path", "/(.)*", "/", "");
    pathInfoWithRegexMatching("/path////////", "/path", "/(.)*", "/", "");

    // a servlet mapping of /thing/*
    pathInfoWithRegexMatching("/path/thing////////", "/path", "/thing/(.)*", "/", "/thing");
    pathInfoWithRegexMatching("/path/thing/stuff", "/path", "/thing/(.)*", "/stuff", "/thing");
    pathInfoWithRegexMatching("/path/thing/stuff.html", "/path", "/thing/(.)*", "/stuff.html",
        "/thing");
    pathInfoWithRegexMatching("/path/thing", "/path", "/thing/(.)*", null, "/thing");

    // *.xx style mapping
    pathInfoWithRegexMatching("/path/thing.thing", "/path", ".*\\.thing", null, "/thing.thing");
    pathInfoWithRegexMatching("/path///h.thing", "/path", ".*\\.thing", null, "/h.thing");
    pathInfoWithRegexMatching("/path///...//h.thing", "/path", ".*\\.thing", null,
        "/.../h.thing");
    pathInfoWithRegexMatching("/path/my/h.thing", "/path", ".*\\.thing", null, "/my/h.thing");

    // path
    pathInfoWithRegexMatching("/path/test.com/com.test.MyServletModule", "", "/path/[^/]+/(.*)",
        "com.test.MyServletModule", "/path/test.com/com.test.MyServletModule");
  }

  public final void pathInfoWithRegexMatching(final String requestUri, final String contextPath,
      String mapping, final String expectedPathInfo, final String servletPath)
      throws IOException, ServletException {

    Injector injector = createMock(Injector.class);
    Binding binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpServletResponse response = createMock(HttpServletResponse.class);

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);

    final boolean[] run = new boolean[1];
    //get an instance of this servlet
    expect(injector.getInstance(Key.get(HttpServlet.class)))
        .andReturn(new HttpServlet() {

          @Override
          protected void service(HttpServletRequest servletRequest,
              HttpServletResponse httpServletResponse) throws ServletException, IOException {

            final String path = servletRequest.getPathInfo();

            if (null == expectedPathInfo) {
              assertNull(String.format("expected [%s] but was [%s]", expectedPathInfo, path),
                  path);
            }
            else {
              assertEquals(String.format("expected [%s] but was [%s]", expectedPathInfo, path),
                  expectedPathInfo, path);
            }

            //assert memoizer
            //noinspection StringEquality
            assertSame("memo field did not work", path, servletRequest.getPathInfo());

            run[0] = true;
          }
        });

    expect(request.getRequestURI())
        .andReturn(requestUri);

    expect(request.getServletPath())
        .andReturn(servletPath)
        .anyTimes();

    expect(request.getContextPath())
        .andReturn(contextPath);

    expect(request.getAttribute(REQUEST_DISPATCHER_REQUEST)).andReturn(null);

    replay(injector, binding, request);

    ServletDefinition servletDefinition = new ServletDefinition(mapping, Key.get(HttpServlet.class),
        UriPatternType.get(UriPatternType.REGEX, mapping), new HashMap<String, String>(), null);

    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));
    servletDefinition.doService(request, response);

    assertTrue("Servlet did not run!", run[0]);
    
    verify(injector, binding, request);
  }
}
