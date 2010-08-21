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
import static com.google.inject.Asserts.reserialize;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.Maps;
import static com.google.inject.servlet.ServletScopes.NullObject;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.io.Serializable;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletTest extends TestCase {
  private static final Key<InRequest> IN_REQUEST_KEY = Key.get(InRequest.class);
  private static final Key<InRequest> IN_REQUEST_NULL_KEY = Key.get(InRequest.class, Null.class);
  private static final Key<InSession> IN_SESSION_KEY = Key.get(InSession.class);
  private static final Key<InSession> IN_SESSION_NULL_KEY = Key.get(InSession.class, Null.class);

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

    String inRequestKey = IN_REQUEST_KEY.toString();
    expect(request.getAttribute(inRequestKey)).andReturn(null);
    request.setAttribute(eq(inRequestKey), isA(InRequest.class));
    
    String inRequestNullKey = IN_REQUEST_NULL_KEY.toString();
    expect(request.getAttribute(inRequestNullKey)).andReturn(null);
    request.setAttribute(eq(inRequestNullKey), eq(NullObject.INSTANCE));

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InRequest.class));
        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
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
    String inRequestKey = IN_REQUEST_KEY.toString();
    expect(request.getAttribute(inRequestKey)).andReturn(inRequest).times(2);
    
    String inRequestNullKey = IN_REQUEST_NULL_KEY.toString();
    expect(request.getAttribute(inRequestNullKey)).andReturn(NullObject.INSTANCE).times(2);

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        
        assertSame(inRequest, injector.getInstance(InRequest.class));
        assertSame(inRequest, injector.getInstance(InRequest.class));

        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
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

    String inSessionKey = IN_SESSION_KEY.toString();
    String inSessionNullKey = IN_SESSION_NULL_KEY.toString();

    expect(request.getSession()).andReturn(session).times(2);
    expect(session.getAttribute(inSessionKey)).andReturn(null);
    session.setAttribute(eq(inSessionKey), isA(InSession.class));

    expect(session.getAttribute(inSessionNullKey)).andReturn(null);
    session.setAttribute(eq(inSessionNullKey), eq(NullObject.INSTANCE));

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);
        assertNotNull(injector.getInstance(InSession.class));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
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

    String inSessionKey = IN_SESSION_KEY.toString();
    String inSessionNullKey = IN_SESSION_NULL_KEY.toString();

    final InSession inSession = new InSession();
    expect(request.getSession()).andReturn(session).times(4);
    expect(session.getAttribute(inSessionKey)).andReturn(inSession).times(2);
    
    expect(session.getAttribute(inSessionNullKey)).andReturn(NullObject.INSTANCE).times(2);

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
//        assertSame(request, servletRequest);

        assertSame(inSession, injector.getInstance(InSession.class));
        assertSame(inSession, injector.getInstance(InSession.class));

        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
      }
    };

    replay(request, session);

    filter.doFilter(request, null, filterChain);

    verify(request, session);
    assertTrue(invoked[0]);
  }

  public void testHttpSessionIsSerializable()
      throws IOException, ClassNotFoundException, ServletException {
    final Injector injector = createInjector();

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);
    final HttpSession session = newFakeHttpSession();

    String inSessionKey = IN_SESSION_KEY.toString();
    String inSessionNullKey = IN_SESSION_NULL_KEY.toString();

    expect(request.getSession()).andReturn(session).times(2);

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertNotNull(injector.getInstance(InSession.class));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
      }
    };

    replay(request);

    filter.doFilter(request, null, filterChain);

    verify(request);
    assertTrue(invoked[0]);

    HttpSession deserializedSession = reserialize(session);

    assertTrue(deserializedSession.getAttribute(inSessionKey) instanceof InSession);
    assertEquals(NullObject.INSTANCE, deserializedSession.getAttribute(inSessionNullKey));
  }

  private static class FakeHttpSessionHandler implements InvocationHandler, Serializable {
    final Map<String, Object> attributes = Maps.newHashMap();

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();
      if ("setAttribute".equals(name)) {
        attributes.put((String) args[0], args[1]);
        return null;
      } else if ("getAttribute".equals(name)) {
        return attributes.get(args[0]);
      } else {
        throw new UnsupportedOperationException();
      }
    }
  }

  /**
   * Returns a fake, serializable HttpSession which stores attributes in a HashMap.
   */
  private HttpSession newFakeHttpSession() {
    return (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(),
        new Class[] { HttpSession.class }, new FakeHttpSessionHandler());
  }

  private Injector createInjector() throws CreationException {

    return Guice.createInjector(new AbstractModule() {

      @Override
      protected void configure() {
        install(new ServletModule());
        bind(InSession.class);
        bind(IN_SESSION_NULL_KEY).toProvider(Providers.<InSession>of(null)).in(SessionScoped.class);
        bind(InRequest.class);
        bind(IN_REQUEST_NULL_KEY).toProvider(Providers.<InRequest>of(null)).in(RequestScoped.class);
      }
    });
  }

  @SessionScoped
  static class InSession implements Serializable {}

  @RequestScoped
  static class InRequest {}

  @BindingAnnotation @Retention(RUNTIME) @Target({PARAMETER, METHOD, FIELD})
  @interface Null {}
}
