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
import com.google.inject.Provider;
import com.google.inject.name.Names;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletTest extends TestCase {

  final Key<InSession> sessionKey = Key.get(InSession.class);
  final Key<InRequest> requestKey = Key.get(InRequest.class);
  final Key<String> nullSessionKey = Key.get(String.class, Names.named("null session"));
  final Key<String> nullRequestKey = Key.get(String.class, Names.named("null request"));
  final Injector injector = createInjector();
  final GuiceFilter filter = new GuiceFilter();
  final HttpServletRequest request = createMock(HttpServletRequest.class);
  final HttpSession session = createMock(HttpSession.class);

  FilterChain filterChain;
  boolean invoked;

  public void testNewRequestObject() throws Exception {
    expect(request.getAttribute(requestKey.toString())).andReturn(null);
    request.setAttribute(eq(requestKey.toString()), isA(InRequest.class));

    filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        invoked = true;
        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InRequest.class));
      }
    };

    replayFilterAndVerify();
  }

  public void testNullRequestObject() throws Exception {
    expect(request.getAttribute(nullRequestKey.toString())).andReturn(null).times(2);
    request.setAttribute(eq(nullRequestKey.toString()), isNull());

    expect(request.getSession()).andReturn(session);
    expect(session.getAttribute(nullSessionKey.toString())).andReturn(null);
    session.setAttribute(eq(nullSessionKey.toString()), isNull());

    filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        invoked = true;
        assertSame(request, servletRequest);
        assertNull(injector.getInstance(nullRequestKey));
        assertNull(injector.getInstance(nullRequestKey));
        assertNull(injector.getInstance(nullSessionKey));
        assertNull(injector.getInstance(nullSessionKey));
      }
    };

    replayFilterAndVerify();
  }

  public void testExistingRequestObject() throws Exception {
    final InRequest inRequest = new InRequest();
    expect(request.getAttribute(requestKey.toString())).andReturn(inRequest).times(2);

    filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        invoked = true;
        assertSame(request, servletRequest);
        assertSame(inRequest, injector.getInstance(InRequest.class));
        assertSame(inRequest, injector.getInstance(InRequest.class));
      }
    };

    replayFilterAndVerify();
  }

  public void testNewSessionObject() throws Exception {
    expect(request.getSession()).andReturn(session);
    expect(session.getAttribute(sessionKey.toString())).andReturn(null);
    session.setAttribute(eq(sessionKey.toString()), isA(InSession.class));

    filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        invoked = true;
        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InSession.class));
      }
    };

    replayFilterAndVerify();
  }

  public void testExistingSessionObject() throws Exception {
    final InSession inSession = new InSession();
    expect(request.getSession()).andReturn(session).times(2);
    expect(session.getAttribute(sessionKey.toString())).andReturn(inSession).times(2);

    filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
        invoked = true;
        assertSame(request, servletRequest);
        assertSame(inSession, injector.getInstance(InSession.class));
        assertSame(inSession, injector.getInstance(InSession.class));
      }
    };

    replayFilterAndVerify();
  }

  private Injector createInjector() throws CreationException {
    return Guice.createInjector(new AbstractModule() {
      protected void configure() {
        install(new ServletModule());
        bind(InSession.class);
        bind(InRequest.class);
        bind(nullRequestKey).toProvider(new NullThenNonNullProvider()).in(RequestScoped.class);
        bind(nullSessionKey).toProvider(new NullThenNonNullProvider()).in(SessionScoped.class);
      }
    });
  }

  private void replayFilterAndVerify() throws IOException, ServletException {
    replay(request);
    replay(session);

    filter.doFilter(request, null, filterChain);

    verify(request);
    verify(session);
    assertTrue(invoked);
  }

  @SessionScoped
  static class InSession {}

  @RequestScoped
  static class InRequest {}

  static class NullThenNonNullProvider implements Provider<String> {
    final Iterator<String> iterator = Arrays.asList(null, "A").iterator();
    public String get() {
      return iterator.next();
    }
  }
}
