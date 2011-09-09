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

import static com.google.inject.Asserts.reserialize;
import static com.google.inject.servlet.ServletScopes.NullObject;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.util.Providers;

import junit.framework.TestCase;

import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Retention;
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
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletTest extends TestCase {
  private static final Key<HttpServletRequest> HTTP_REQ_KEY = Key.get(HttpServletRequest.class);
  private static final Key<HttpServletResponse> HTTP_RESP_KEY = Key.get(HttpServletResponse.class);
  private static final Key<Map<String, String[]>> REQ_PARAMS_KEY
      = new Key<Map<String, String[]>>(RequestParameters.class) {};

  private static final Key<InRequest> IN_REQUEST_KEY = Key.get(InRequest.class);
  private static final Key<InRequest> IN_REQUEST_NULL_KEY = Key.get(InRequest.class, Null.class);
  private static final Key<InSession> IN_SESSION_KEY = Key.get(InSession.class);
  private static final Key<InSession> IN_SESSION_NULL_KEY = Key.get(InSession.class, Null.class);

  @Override
  public void setUp() {
    //we need to clear the reference to the pipeline every test =(
    GuiceFilter.reset();
  }

  public void testRequestAndResponseBindings() throws Exception {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();
    final HttpServletResponse response = newFakeHttpServletResponse();
    final Map<String, String[]> params = Maps.newHashMap();

    final boolean[] invoked = new boolean[1];
    GuiceFilter filter = new GuiceFilter();
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertSame(request, servletRequest);
        assertSame(request, injector.getInstance(ServletRequest.class));
        assertSame(request, injector.getInstance(HTTP_REQ_KEY));

        assertSame(response, servletResponse);
        assertSame(response, injector.getInstance(ServletResponse.class));
        assertSame(response, injector.getInstance(HTTP_RESP_KEY));

        assertSame(servletRequest.getParameterMap(), injector.getInstance(REQ_PARAMS_KEY));
      }
    };
    filter.doFilter(request, response, filterChain);

    assertTrue(invoked[0]);
  }

  public void testNewRequestObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();

    GuiceFilter filter = new GuiceFilter();
    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertNotNull(injector.getInstance(InRequest.class));
        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
      }
    };

    filter.doFilter(request, null, filterChain);

    assertTrue(invoked[0]);
  }

  public void testExistingRequestObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();

    GuiceFilter filter = new GuiceFilter();
    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;

        InRequest inRequest = injector.getInstance(InRequest.class);
        assertSame(inRequest, injector.getInstance(InRequest.class));

        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
        assertNull(injector.getInstance(IN_REQUEST_NULL_KEY));
      }
    };

    filter.doFilter(request, null, filterChain);

    assertTrue(invoked[0]);
  }

  public void testNewSessionObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();

    GuiceFilter filter = new GuiceFilter();
    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertNotNull(injector.getInstance(InSession.class));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
      }
    };

    filter.doFilter(request, null, filterChain);

    assertTrue(invoked[0]);
  }

  public void testExistingSessionObject()
      throws CreationException, IOException, ServletException {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();

    GuiceFilter filter = new GuiceFilter();
    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;

        InSession inSession = injector.getInstance(InSession.class);
        assertSame(inSession, injector.getInstance(InSession.class));

        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
      }
    };

    filter.doFilter(request, null, filterChain);

    assertTrue(invoked[0]);
  }

  public void testHttpSessionIsSerializable()
      throws IOException, ClassNotFoundException, ServletException {
    final Injector injector = createInjector();
    final HttpServletRequest request = newFakeHttpServletRequest();
    final HttpSession session = request.getSession();

    GuiceFilter filter = new GuiceFilter();
    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertNotNull(injector.getInstance(InSession.class));
        assertNull(injector.getInstance(IN_SESSION_NULL_KEY));
      }
    };

    filter.doFilter(request, null, filterChain);

    assertTrue(invoked[0]);

    HttpSession deserializedSession = reserialize(session);

    String inSessionKey = IN_SESSION_KEY.toString();
    String inSessionNullKey = IN_SESSION_NULL_KEY.toString();
    assertTrue(deserializedSession.getAttribute(inSessionKey) instanceof InSession);
    assertEquals(NullObject.INSTANCE, deserializedSession.getAttribute(inSessionNullKey));
  }

  private static class ThrowingInvocationHandler implements InvocationHandler {
    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      throw new UnsupportedOperationException("No methods are supported on this object");
    }
  }
  
  /**
   * Returns a fake, HttpServletRequest which stores attributes in a HashMap.
   */
  private HttpServletRequest newFakeHttpServletRequest() {
    HttpServletRequest delegate = (HttpServletRequest) Proxy.newProxyInstance(
        HttpServletRequest.class.getClassLoader(),
        new Class[] { HttpServletRequest.class }, new ThrowingInvocationHandler());
    
    return new HttpServletRequestWrapper(delegate) {
      final Map<String, Object> attributes = Maps.newHashMap(); 
      final HttpSession session = newFakeHttpSession();

      @Override public Object getAttribute(String name) {
        return attributes.get(name);
      }
      
      @Override public void setAttribute(String name, Object value) {
        attributes.put(name, value);
      }
      
      @Override public Map getParameterMap() {
        return ImmutableMap.of();
      }
      
      @Override public String getRequestURI() {
        return "/";
      }
      
      @Override public String getContextPath() {
        return "";
      }
      
      @Override public HttpSession getSession() {
        return session;
      }
    };
  }
  
  /**
   * Returns a fake, HttpServletResponse which throws an exception if any of its
   * methods are called.
   */
  private HttpServletResponse newFakeHttpServletResponse() {
    return (HttpServletResponse) Proxy.newProxyInstance(
        HttpServletResponse.class.getClassLoader(),
        new Class[] { HttpServletResponse.class }, new ThrowingInvocationHandler());
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
