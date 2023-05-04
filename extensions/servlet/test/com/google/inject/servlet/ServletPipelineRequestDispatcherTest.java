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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import junit.framework.TestCase;

/**
 * Tests forwarding and inclusion (RequestDispatcher actions from the servlet spec).
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
@SuppressWarnings("unchecked") // Safe because mock will only ever return HttpServlet
public class ServletPipelineRequestDispatcherTest extends TestCase {
  private static final Key<HttpServlet> HTTP_SERLVET_KEY = Key.get(HttpServlet.class);
  private static final String A_KEY = "thinglyDEgintly" + new Date() + UUID.randomUUID();
  private static final String A_VALUE =
      ServletPipelineRequestDispatcherTest.class.toString() + new Date() + UUID.randomUUID();

  public final void testIncludeManagedServlet() throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);

    final Injector injector = mock(Injector.class);
    final Binding<HttpServlet> binding = mock(Binding.class);
    final HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getAttribute(A_KEY)).thenReturn(A_VALUE);

    final boolean[] run = new boolean[1];
    final HttpServlet mockServlet =
        new HttpServlet() {
          @Override
          protected void service(
              HttpServletRequest request, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            run[0] = true;

            final Object o = request.getAttribute(A_KEY);
            assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
          }
        };

    when(binding.acceptScopingVisitor((BindingScopingVisitor) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);
    when(injector.getInstance(HTTP_SERLVET_KEY)).thenReturn(mockServlet);

    final Key<ServletDefinition> servetDefsKey = Key.get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = mock(Binding.class);
    when(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .thenReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    when(mockBinding.getProvider()).thenReturn(bindingProvider);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());

    final RequestDispatcher dispatcher =
        new ManagedServletPipeline(injector).getRequestDispatcher(pattern);

    assertNotNull(dispatcher);
    dispatcher.include(requestMock, mock(HttpServletResponse.class));

    assertTrue("Include did not dispatch to our servlet!", run[0]);

    verify(requestMock).setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    verify(requestMock).removeAttribute(REQUEST_DISPATCHER_REQUEST);
  }

  public final void testForwardToManagedServlet() throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);

    final Injector injector = mock(Injector.class);
    final Binding<HttpServlet> binding = mock(Binding.class);
    final HttpServletRequest requestMock = mock(HttpServletRequest.class);
    final HttpServletResponse mockResponse = mock(HttpServletResponse.class);

    when(requestMock.getAttribute(A_KEY)).thenReturn(A_VALUE);

    when(mockResponse.isCommitted()).thenReturn(false);

    final List<String> paths = new ArrayList<>();
    final HttpServlet mockServlet =
        new HttpServlet() {
          @Override
          protected void service(
              HttpServletRequest request, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {
            paths.add(request.getRequestURI());

            final Object o = request.getAttribute(A_KEY);
            assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
          }
        };

    when(binding.acceptScopingVisitor((BindingScopingVisitor) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);

    when(injector.getInstance(HTTP_SERLVET_KEY)).thenReturn(mockServlet);

    final Key<ServletDefinition> servetDefsKey = Key.get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = mock(Binding.class);
    when(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .thenReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    when(mockBinding.getProvider()).thenReturn(bindingProvider);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());

    final RequestDispatcher dispatcher =
        new ManagedServletPipeline(injector).getRequestDispatcher(pattern);

    assertNotNull(dispatcher);
    dispatcher.forward(requestMock, mockResponse);

    assertTrue("Include did not dispatch to our servlet!", paths.contains(pattern));

    verify(requestMock).setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    verify(requestMock).removeAttribute(REQUEST_DISPATCHER_REQUEST);
    verify(mockResponse).resetBuffer();
  }

  public final void testForwardToManagedServletFailureOnCommittedBuffer()
      throws IOException, ServletException {
    IllegalStateException expected = null;
    try {
      forwardToManagedServletFailureOnCommittedBuffer();
    } catch (IllegalStateException ise) {
      expected = ise;
    } finally {
      assertNotNull("Expected IllegalStateException was not thrown", expected);
    }
  }

  public final void forwardToManagedServletFailureOnCommittedBuffer()
      throws IOException, ServletException {
    String pattern = "blah.html";
    final ServletDefinition servletDefinition =
        new ServletDefinition(
            Key.get(HttpServlet.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);

    final Injector injector = mock(Injector.class);
    final Binding<HttpServlet> binding = mock(Binding.class);
    final HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    final HttpServletResponse mockResponse = mock(HttpServletResponse.class);

    when(mockResponse.isCommitted()).thenReturn(true);

    final HttpServlet mockServlet =
        new HttpServlet() {
          @Override
          protected void service(
              HttpServletRequest request, HttpServletResponse httpServletResponse)
              throws ServletException, IOException {

            final Object o = request.getAttribute(A_KEY);
            assertEquals("Wrong attrib returned - " + o, A_VALUE, o);
          }
        };

    when(binding.acceptScopingVisitor((BindingScopingVisitor) any())).thenReturn(true);
    when(injector.getBinding(Key.get(HttpServlet.class))).thenReturn(binding);

    when(injector.getInstance(Key.get(HttpServlet.class))).thenReturn(mockServlet);

    final Key<ServletDefinition> servetDefsKey = Key.get(TypeLiteral.get(ServletDefinition.class));

    Binding<ServletDefinition> mockBinding = mock(Binding.class);
    when(injector.findBindingsByType(eq(servetDefsKey.getTypeLiteral())))
        .thenReturn(ImmutableList.<Binding<ServletDefinition>>of(mockBinding));
    Provider<ServletDefinition> bindingProvider = Providers.of(servletDefinition);
    when(mockBinding.getProvider()).thenReturn(bindingProvider);

    // Have to init the Servlet before we can dispatch to it.
    servletDefinition.init(null, injector, Sets.<HttpServlet>newIdentityHashSet());

    final RequestDispatcher dispatcher =
        new ManagedServletPipeline(injector).getRequestDispatcher(pattern);

    assertNotNull(dispatcher);

    dispatcher.forward(mockRequest, mockResponse);
  }

  public final void testWrappedRequestUriAndUrlConsistency() {
    final HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getScheme()).thenReturn("http");
    when(mockRequest.getServerName()).thenReturn("the.server");
    when(mockRequest.getServerPort()).thenReturn(12345);

    HttpServletRequest wrappedRequest = ManagedServletPipeline.wrapRequest(mockRequest, "/new-uri");
    assertEquals("/new-uri", wrappedRequest.getRequestURI());
    assertEquals("http://the.server:12345/new-uri", wrappedRequest.getRequestURL().toString());
  }

  public final void testWrappedRequestUrlNegativePort() {
    final HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getScheme()).thenReturn("http");
    when(mockRequest.getServerName()).thenReturn("the.server");
    when(mockRequest.getServerPort()).thenReturn(-1);

    HttpServletRequest wrappedRequest = ManagedServletPipeline.wrapRequest(mockRequest, "/new-uri");
    assertEquals("/new-uri", wrappedRequest.getRequestURI());
    assertEquals("http://the.server/new-uri", wrappedRequest.getRequestURL().toString());
  }

  public final void testWrappedRequestUrlDefaultPort() {
    final HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getScheme()).thenReturn("http");
    when(mockRequest.getServerName()).thenReturn("the.server");
    when(mockRequest.getServerPort()).thenReturn(80);

    HttpServletRequest wrappedRequest = ManagedServletPipeline.wrapRequest(mockRequest, "/new-uri");
    assertEquals("/new-uri", wrappedRequest.getRequestURI());
    assertEquals("http://the.server/new-uri", wrappedRequest.getRequestURL().toString());
  }

  public final void testWrappedRequestUrlDefaultHttpsPort() {
    final HttpServletRequest mockRequest = mock(HttpServletRequest.class);
    when(mockRequest.getScheme()).thenReturn("https");
    when(mockRequest.getServerName()).thenReturn("the.server");
    when(mockRequest.getServerPort()).thenReturn(443);

    HttpServletRequest wrappedRequest = ManagedServletPipeline.wrapRequest(mockRequest, "/new-uri");
    assertEquals("/new-uri", wrappedRequest.getRequestURI());
    assertEquals("https://the.server/new-uri", wrappedRequest.getRequestURL().toString());
  }
}
