/**
 * Copyright (C) 2006 Google Inc.
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


import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

import junit.framework.TestCase;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletTest extends TestCase {

  @Override
  public void setUp() {
    //we need to clear the reference to the pipeline every test =(
    GuiceFilter.reset();
  }

  public void testNewRequestObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);

    String name = Key.get(InRequest.class).toString();
    expect(request.getAttribute(name)).andReturn(null);
    request.setAttribute(eq(name), isA(InRequest.class));

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InRequest.class));
      }
    };

    replay(request);

    filter.doFilter(request, null, filterChain);

    verify(request);
    assertTrue(invoked[0]);
  }

  public void testExistingRequestObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);

    final InRequest inRequest = new InRequest();
    String name = Key.get(InRequest.class).toString();
    expect(request.getAttribute(name)).andReturn(inRequest).times(2);

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        
        assertSame(inRequest, injector.getInstance(InRequest.class));
        assertSame(inRequest, injector.getInstance(InRequest.class));
      }
    };

    replay(request);

    filter.doFilter(request, null, filterChain);

    verify(request);
    assertTrue(invoked[0]);
  }

  public void testNewSessionObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);
    final HttpSession session = createMock(HttpSession.class);

    String name = Key.get(InSession.class).toString();

    expect(request.getSession()).andReturn(session);
    expect(session.getAttribute(name)).andReturn(null);
    session.setAttribute(eq(name), isA(InSession.class));

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InSession.class));
      }
    };

    replay(request, session);

    filter.doFilter(request, null, filterChain);

    verify(request, session);
    assertTrue(invoked[0]);
  }

  public void testExistingSessionObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);
    final HttpSession session = createMock(HttpSession.class);

    String name = Key.get(InSession.class).toString();

    final InSession inSession = new InSession();
    expect(request.getSession()).andReturn(session).times(2);
    expect(session.getAttribute(name)).andReturn(inSession).times(2);

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);

        assertSame(inSession, injector.getInstance(InSession.class));
        assertSame(inSession, injector.getInstance(InSession.class));
      }
    };

    replay(request, session);

    filter.doFilter(request, null, filterChain);

    verify(request, session);
    assertTrue(invoked[0]);
  }

  private Injector createInjector() throws CreationException {

    return Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new ServletModule());
        bind(InSession.class);
        bind(InRequest.class);
      }
    });
  }

  @SessionScoped
  static class InSession {}

  @RequestScoped
  static class InRequest {}
}
