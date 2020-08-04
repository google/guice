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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.spi.BindingScopingVisitor;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

/**
 * Basic unit test for lifecycle of a ServletDefinition (wrapper).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class ServletDefinitionTest extends TestCase {

  @SuppressWarnings("unchecked") // Safe because mock will only ever return HttpServlet
  public final void testServletInitAndConfig() throws ServletException {
    Injector injector = createMock(Injector.class);
    Binding<HttpServlet> binding = createMock(Binding.class);

    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class))).andReturn(binding);
    final HttpServlet mockServlet = new HttpServlet() {};
    expect(injector.getInstance(Key.get(HttpServlet.class))).andReturn(mockServlet).anyTimes();

    replay(injector, binding);

    //some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .build();

    String pattern = "/*";
    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            initParams,
            null);

    ServletContext servletContext = createMock(ServletContext.class);
    final String contextName = "thing__!@@44__SRV" + getClass();
    expect(servletContext.getServletContextName()).andReturn(contextName);

    replay(servletContext);

    servletDefinition.init(servletContext, injector, Sets.<HttpServlet>newIdentityHashSet());

    assertNotNull(mockServlet.getServletContext());
    assertEquals(contextName, mockServlet.getServletContext().getServletContextName());
    assertEquals(Key.get(HttpServlet.class).toString(), mockServlet.getServletName());

    final ServletConfig servletConfig = mockServlet.getServletConfig();
    final Enumeration<?> names = servletConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertEquals(initParams.get(name), servletConfig.getInitParameter(name));
    }

    verify(injector, binding, servletContext);
  }

  public void testServiceWithContextPath() throws IOException, ServletException {
    String pattern = "/*";
    //some init params
    Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .build();

    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            initParams,
            null);
    HttpServletResponse servletResponse = createMock(HttpServletResponse.class);
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);

    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/test.html");
    replay(servletRequest, servletResponse);
    servletDefinition.service(servletRequest, servletResponse);
    verify(servletRequest, servletResponse);
  }
}
