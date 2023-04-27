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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.BindingScopingVisitor;
import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

/**
 * Ensures servlet spec compliance for CGI-style variables and general path/pattern matching.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
@SuppressWarnings("unchecked") // Safe because mock will only ever return HttpServlet
public class ServletDefinitionPathsTest {

  // Data-driven test.
  @Test
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

  private void servletPath(
      final String requestPath, String mapping, final String expectedServletPath)
      throws IOException, ServletException {

    Injector injector = mock(Injector.class);
    Binding<HttpServlet> binding = mock(Binding.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);

    final boolean[] run = new boolean[1];
    // get an instance of this servlet
    when(injector.getInstance(Key.get(HttpServlet.class)))
        .thenReturn(
            new HttpServlet() {

              @Override
              protected void service(
                  HttpServletRequest servletRequest, HttpServletResponse httpServletResponse)
                  throws ServletException, IOException {

                final String path = servletRequest.getServletPath();
                assertEquals(
                    expectedServletPath,
                    path,
                    String.format("expected [%s] but was [%s]", expectedServletPath, path));
                run[0] = true;
              }
            });

    when(request.getServletPath()).thenReturn(requestPath);

    ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, mapping),
            new HashMap<String, String>(),
            null);

    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());
    servletDefinition.doService(request, response);

    assertTrue(run[0], "Servlet did not run!");
  }

  // Data-driven test.
  @Test
  public final void testPathInfoWithServletStyleMatching() throws IOException, ServletException {
    pathInfoWithServletStyleMatching("/path/index.html", "/path", "/*", "/index.html", "");
    pathInfoWithServletStyleMatching(
        "/path//hulaboo///index.html", "/path", "/*", "/hulaboo/index.html", "");
    pathInfoWithServletStyleMatching("/path/", "/path", "/*", "/", "");
    pathInfoWithServletStyleMatching("/path////////", "/path", "/*", "/", "");

    // a servlet mapping of /thing/*
    pathInfoWithServletStyleMatching("/path/thing////////", "/path", "/thing/*", "/", "/thing");
    pathInfoWithServletStyleMatching("/path/thing/stuff", "/path", "/thing/*", "/stuff", "/thing");
    pathInfoWithServletStyleMatching(
        "/path/thing/stuff.html", "/path", "/thing/*", "/stuff.html", "/thing");
    pathInfoWithServletStyleMatching("/path/thing", "/path", "/thing/*", null, "/thing");

    // see external issue 372
    pathInfoWithServletStyleMatching(
        "/path/some/path/of.jsp", "/path", "/thing/*", null, "/some/path/of.jsp");

    // *.xx style mapping
    pathInfoWithServletStyleMatching("/path/thing.thing", "/path", "*.thing", null, "/thing.thing");
    pathInfoWithServletStyleMatching("/path///h.thing", "/path", "*.thing", null, "/h.thing");
    pathInfoWithServletStyleMatching(
        "/path///...//h.thing", "/path", "*.thing", null, "/.../h.thing");
    pathInfoWithServletStyleMatching("/path/my/h.thing", "/path", "*.thing", null, "/my/h.thing");

    // Encoded URLs
    pathInfoWithServletStyleMatching("/path/index%2B.html", "/path", "/*", "/index+.html", "");
    pathInfoWithServletStyleMatching(
        "/path/a%20file%20with%20spaces%20in%20name.html",
        "/path", "/*", "/a file with spaces in name.html", "");
    pathInfoWithServletStyleMatching(
        "/path/Tam%C3%A1s%20nem%20m%C3%A1s.html", "/path", "/*", "/Tamás nem más.html", "");

    // see https://github.com/google/guice/issues/1655
    pathInfoWithServletStyleMatching("/index.html", null, "/*", "/index.html", "");
  }

  private void pathInfoWithServletStyleMatching(
      final String requestUri,
      final String contextPath,
      String mapping,
      final String expectedPathInfo,
      final String servletPath)
      throws IOException, ServletException {

    Injector injector = mock(Injector.class);
    Binding<HttpServlet> binding = mock(Binding.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);

    final boolean[] run = new boolean[1];
    // get an instance of this servlet
    when(injector.getInstance(Key.get(HttpServlet.class)))
        .thenReturn(
            new HttpServlet() {

              @Override
              protected void service(
                  HttpServletRequest servletRequest, HttpServletResponse httpServletResponse)
                  throws ServletException, IOException {

                final String path = servletRequest.getPathInfo();

                if (null == expectedPathInfo) {
                  assertNull(
                      path, String.format("expected [%s] but was [%s]", expectedPathInfo, path));
                } else {
                  assertEquals(
                      expectedPathInfo,
                      path,
                      String.format("expected [%s] but was [%s]", expectedPathInfo, path));
                }

                // assert memoizer
                //noinspection StringEquality
                assertSame(path, servletRequest.getPathInfo(), "memo field did not work");

                run[0] = true;
              }
            });

    when(request.getRequestURI()).thenReturn(requestUri);

    when(request.getServletPath()).thenReturn(servletPath);

    when(request.getContextPath()).thenReturn(contextPath);

    when(request.getAttribute(REQUEST_DISPATCHER_REQUEST)).thenReturn(null);

    ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, mapping),
            new HashMap<String, String>(),
            null);

    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());
    servletDefinition.doService(request, response);

    assertTrue(run[0], "Servlet did not run!");
  }

  // Data-driven test.
  @Test
  public final void testPathInfoWithRegexMatching() throws IOException, ServletException {
    // first a mapping of /*
    pathInfoWithRegexMatching("/path/index.html", "/path", "/(.)*", "/index.html", "");
    pathInfoWithRegexMatching(
        "/path//hulaboo///index.html", "/path", "/(.)*", "/hulaboo/index.html", "");
    pathInfoWithRegexMatching("/path/", "/path", "/(.)*", "/", "");
    pathInfoWithRegexMatching("/path////////", "/path", "/(.)*", "/", "");

    // a servlet mapping of /thing/*
    pathInfoWithRegexMatching("/path/thing////////", "/path", "/thing/(.)*", "/", "/thing");
    pathInfoWithRegexMatching("/path/thing/stuff", "/path", "/thing/(.)*", "/stuff", "/thing");
    pathInfoWithRegexMatching(
        "/path/thing/stuff.html", "/path", "/thing/(.)*", "/stuff.html", "/thing");
    pathInfoWithRegexMatching("/path/thing", "/path", "/thing/(.)*", null, "/thing");

    // *.xx style mapping
    pathInfoWithRegexMatching("/path/thing.thing", "/path", ".*\\.thing", null, "/thing.thing");
    pathInfoWithRegexMatching("/path///h.thing", "/path", ".*\\.thing", null, "/h.thing");
    pathInfoWithRegexMatching("/path///...//h.thing", "/path", ".*\\.thing", null, "/.../h.thing");
    pathInfoWithRegexMatching("/path/my/h.thing", "/path", ".*\\.thing", null, "/my/h.thing");

    // path
    pathInfoWithRegexMatching(
        "/path/test.com/com.test.MyServletModule",
        "",
        "/path/[^/]+/(.*)",
        "com.test.MyServletModule",
        "/path/test.com/com.test.MyServletModule");

    // Encoded URLs
    pathInfoWithRegexMatching("/path/index%2B.html", "/path", "/(.)*", "/index+.html", "");
    pathInfoWithRegexMatching(
        "/path/a%20file%20with%20spaces%20in%20name.html",
        "/path", "/(.)*", "/a file with spaces in name.html", "");
    pathInfoWithRegexMatching(
        "/path/Tam%C3%A1s%20nem%20m%C3%A1s.html", "/path", "/(.)*", "/Tamás nem más.html", "");
  }

  public final void pathInfoWithRegexMatching(
      final String requestUri,
      final String contextPath,
      String mapping,
      final String expectedPathInfo,
      final String servletPath)
      throws IOException, ServletException {

    Injector injector = mock(Injector.class);
    Binding<HttpServlet> binding = mock(Binding.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);

    final boolean[] run = new boolean[1];
    // get an instance of this servlet
    when(injector.getInstance(Key.get(HttpServlet.class)))
        .thenReturn(
            new HttpServlet() {

              @Override
              protected void service(
                  HttpServletRequest servletRequest, HttpServletResponse httpServletResponse)
                  throws ServletException, IOException {

                final String path = servletRequest.getPathInfo();

                if (null == expectedPathInfo) {
                  assertNull(
                      path, String.format("expected [%s] but was [%s]", expectedPathInfo, path));
                } else {
                  assertEquals(
                      expectedPathInfo,
                      path,
                      String.format("expected [%s] but was [%s]", expectedPathInfo, path));
                }

                // assert memoizer
                //noinspection StringEquality
                assertSame(path, servletRequest.getPathInfo(), "memo field did not work");

                run[0] = true;
              }
            });

    when(request.getRequestURI()).thenReturn(requestUri);

    when(request.getServletPath()).thenReturn(servletPath);

    when(request.getContextPath()).thenReturn(contextPath);

    when(request.getAttribute(REQUEST_DISPATCHER_REQUEST)).thenReturn(null);

    ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.REGEX, mapping),
            new HashMap<String, String>(),
            null);

    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());
    servletDefinition.doService(request, response);

    assertTrue(run[0], "Servlet did not run!");
  }
}
