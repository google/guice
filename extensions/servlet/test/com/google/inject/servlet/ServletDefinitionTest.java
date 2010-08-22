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
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.verify;

/**
 * Basic unit test for lifecycle of a ServletDefinition (wrapper).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class ServletDefinitionTest extends TestCase {

  public final void testServletInitAndConfig() throws ServletException {
    Injector injector = createMock(Injector.class);
    Binding binding = createMock(Binding.class);

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);
    final HttpServlet mockServlet = new HttpServlet() {
    };
    expect(injector.getInstance(Key.get(HttpServlet.class)))
        .andReturn(mockServlet)
        .anyTimes();

    replay(injector, binding);

    //some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams = new HashMap<String, String>() {
      {
        put("ahsd", "asdas24dok");
        put("ahssd", "asdasd124ok");
        put("ahfsasd", "asda124sdok");
        put("ahsasgd", "a124sdasdok");
        put("ahsd124124", "as124124124dasdok");
      }
    };

    String pattern = "/*";
    final ServletDefinition servletDefinition = new ServletDefinition(pattern,
        Key.get(HttpServlet.class), UriPatternType.get(UriPatternType.SERVLET, pattern), initParams, null);

    ServletContext servletContext = createMock(ServletContext.class);
    final String contextName = "thing__!@@44__SRV" + getClass();
    expect(servletContext.getServletContextName())
        .andReturn(contextName);

    replay(servletContext);

    servletDefinition.init(servletContext, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));

    assertNotNull(mockServlet.getServletContext());
    assertEquals(contextName, mockServlet.getServletContext().getServletContextName());
    assertEquals(Key.get(HttpServlet.class).toString(), mockServlet.getServletName());

    final ServletConfig servletConfig = mockServlet.getServletConfig();
    final Enumeration names = servletConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertEquals(initParams.get(name), servletConfig.getInitParameter(name));
    }
    
    verify(injector, binding, servletContext);
  }
}
