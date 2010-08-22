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
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.util.Providers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

import static com.google.inject.servlet.ManagedServletPipeline.REQUEST_DISPATCHER_REQUEST;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Tests forwarding and inclusion (RequestDispatcher actions from the
 * servlet spec).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class ServletPipelineRequestDispatcherTest extends TestCase {
  private static final Key<HttpServlet> HTTP_SERLVET_KEY = Key.get(HttpServlet.class);
  private static final String A_KEY = "thinglyDEgintly" + new Date() + UUID.randomUUID();
  private static final String A_VALUE = ServletPipelineRequestDispatcherTest.class.toString()
      + new Date() + UUID.randomUUID();

  public final void testIncludeManagedServlet() throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition = new ServletDefinition(pattern,
        Key.get(HttpServlet.class), UriPatternType.get(UriPatternType.SERVLET, pattern),
        new HashMap<String, String>(), null);

    final Injector injector = createMock(Injector.class);
    final Binding binding = createMock(Binding.class);
    final HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getAttribute(A_KEY))
        .andReturn(A_VALUE);


    requestMock.setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    requestMock.removeAttribute(REQUEST_DISPATCHER_REQUEST);

    final boolean[] run = new boolean[1];
    final HttpServlet mockServlet = new HttpServlet() {
      protected void service(HttpServletRequest request, HttpServletResponse httpServletResponse)
          throws ServletException, IOException {
        run[0] = true;

        final Object o = request.getAttribute(A_KEY);
        assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
      }
    };

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);
    expect(injector.getInstance(HTTP_SERLVET_KEY))
        .andReturn(mockServlet);


    final Key<ServletDefinition> servetDefsKey = Key
        .get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = createMock(Binding.class);
    expect(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .andReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    expect(mockBinding.getProvider())
        .andReturn(bindingProvider);

    replay(injector, binding, requestMock, mockBinding);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));

    final RequestDispatcher dispatcher = new ManagedServletPipeline(
        injector)
        .getRequestDispatcher(pattern);

    assertNotNull(dispatcher);
    dispatcher.include(requestMock, createMock(HttpServletResponse.class));

    assertTrue("Include did not dispatch to our servlet!", run[0]);

    verify(injector, requestMock, mockBinding);
  }

  public final void testForwardToManagedServlet() throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition = new ServletDefinition(pattern,
        Key.get(HttpServlet.class), UriPatternType.get(UriPatternType.SERVLET, pattern),
        new HashMap<String, String>(), null);

    final Injector injector = createMock(Injector.class);
    final Binding binding = createMock(Binding.class);
    final HttpServletRequest requestMock = createMock(HttpServletRequest.class);
    final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);

    expect(requestMock.getAttribute(A_KEY))
        .andReturn(A_VALUE);


    requestMock.setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    requestMock.removeAttribute(REQUEST_DISPATCHER_REQUEST);

    expect(mockResponse.isCommitted())
        .andReturn(false);

    mockResponse.resetBuffer();
    expectLastCall().once();

    final List<String> paths = new ArrayList<String>();
    final HttpServlet mockServlet = new HttpServlet() {
      protected void service(HttpServletRequest request, HttpServletResponse httpServletResponse)
          throws ServletException, IOException {
        paths.add(request.getRequestURI());

        final Object o = request.getAttribute(A_KEY);
        assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
      }
    };

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);

    expect(injector.getInstance(HTTP_SERLVET_KEY))
        .andReturn(mockServlet);

    final Key<ServletDefinition> servetDefsKey = Key
        .get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = createMock(Binding.class);
    expect(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .andReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    expect(mockBinding.getProvider())
        .andReturn(bindingProvider);

    replay(injector, binding, requestMock, mockResponse, mockBinding);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));

    final RequestDispatcher dispatcher = new ManagedServletPipeline(injector)
        .getRequestDispatcher(pattern);

    assertNotNull(dispatcher);
    dispatcher.forward(requestMock, mockResponse);

    assertTrue("Include did not dispatch to our servlet!", paths.contains(pattern));

    verify(injector, requestMock, mockResponse, mockBinding);
  }

  public final void testForwardToManagedServletFailureOnCommittedBuffer()
      throws IOException, ServletException {
    IllegalStateException expected = null;
    try {
      forwardToManagedServletFailureOnCommittedBuffer();
    }
    catch (IllegalStateException ise) {
      expected = ise;
    } finally {
      assertNotNull("Expected IllegalStateException was not thrown", expected);
    }
  }

  public final void forwardToManagedServletFailureOnCommittedBuffer()
      throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition = new ServletDefinition(pattern,
        Key.get(HttpServlet.class), UriPatternType.get(UriPatternType.SERVLET, pattern),
        new HashMap<String, String>(), null);

    final Injector injector = createMock(Injector.class);
    final Binding binding = createMock(Binding.class);
    final HttpServletRequest mockRequest = createMock(HttpServletRequest.class);
    final HttpServletResponse mockResponse = createMock(HttpServletResponse.class);

    expect(mockResponse.isCommitted())
        .andReturn(true);

    final HttpServlet mockServlet = new HttpServlet() {
      protected void service(HttpServletRequest request, HttpServletResponse httpServletResponse)
          throws ServletException, IOException {

        final Object o = request.getAttribute(A_KEY);
        assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
      }
    };

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(HttpServlet.class)))
        .andReturn(binding);

    expect(injector.getInstance(Key.get(HttpServlet.class)))
        .andReturn(mockServlet);


    final Key<ServletDefinition> servetDefsKey = Key
        .get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = createMock(Binding.class);
    expect(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .andReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    expect(mockBinding.getProvider())
        .andReturn(bindingProvider);

    replay(injector, binding, mockRequest, mockResponse, mockBinding);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector,
        Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap()));

    final RequestDispatcher dispatcher = new ManagedServletPipeline(injector)
        .getRequestDispatcher(pattern);

    assertNotNull(dispatcher);

    try {
      dispatcher.forward(mockRequest, mockResponse);
    }
    finally {
      verify(injector, mockRequest, mockResponse, mockBinding);
    }

  }
}
