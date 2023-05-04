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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.BindingScopingVisitor;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

/**
 * Basic unit test for lifecycle of a ServletDefinition (wrapper).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class ServletDefinitionTest extends TestCase {

  @SuppressWarnings("unchecked") // Safe because mock will only ever return HttpServlet
  public final void testServletInitAndConfig() throws ServletException {
    Injector injector = mock(Injector.class);
    Binding<HttpServlet> binding = mock(Binding.class);

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);
    final HttpServlet mockServlet = new HttpServlet() {};
    when(injector.getInstance(Key.get(HttpServlet.class))).thenReturn(mockServlet);

    // some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .buildOrThrow();

    String pattern = "/*";
    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            initParams,
            null);

    ServletContext servletContext = mock(ServletContext.class);
    final String contextName = "thing__!@@44__SRV" + getClass();
    when(servletContext.getServletContextName()).thenReturn(contextName);

    servletDefinition.init(servletContext, injector, Sets.<HttpServlet>newIdentityHashSet());

    assertNotNull(mockServlet.getServletContext());
    assertEquals(contextName, mockServlet.getServletContext().getServletContextName());
    assertEquals(Key.get(HttpServlet.class).toString(), mockServlet.getServletName());

    final ServletConfig servletConfig = mockServlet.getServletConfig();
    final Enumeration<String> names = servletConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertEquals(initParams.get(name), servletConfig.getInitParameter(name));
    }
  }

  public void testServiceWithContextPath() throws IOException, ServletException {
    String pattern = "/*";
    // some init params
    Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .buildOrThrow();

    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            initParams,
            null);
    HttpServletResponse servletResponse = mock(HttpServletResponse.class);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);

    when(servletRequest.getContextPath()).thenReturn("/a_context_path");
    when(servletRequest.getRequestURI()).thenReturn("/test.html");

    servletDefinition.service(servletRequest, servletResponse);
  }
}
