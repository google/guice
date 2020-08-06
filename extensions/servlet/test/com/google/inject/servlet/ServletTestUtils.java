// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.inject.servlet;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Utilities for servlet tests.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class ServletTestUtils {

  private ServletTestUtils() {}

  private static class ThrowingInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      throw new UnsupportedOperationException("No methods are supported on this object");
    }
  }

  /** Returns a FilterChain that does nothing. */
  public static FilterChain newNoOpFilterChain() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {}
    };
  }

  /** Returns a fake, HttpServletRequest which stores attributes in a HashMap. */
  public static HttpServletRequest newFakeHttpServletRequest() {
    HttpServletRequest delegate =
        (HttpServletRequest)
            Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[] {HttpServletRequest.class},
                new ThrowingInvocationHandler());

    return new HttpServletRequestWrapper(delegate) {
      final Map<String, Object> attributes = Maps.newHashMap();
      final HttpSession session = newFakeHttpSession();

      @Override
      public String getMethod() {
        return "GET";
      }

      @Override
      public Object getAttribute(String name) {
        return attributes.get(name);
      }

      @Override
      public void setAttribute(String name, Object value) {
        attributes.put(name, value);
      }

      @Override
      public Map<String, String[]> getParameterMap() {
        return ImmutableMap.of();
      }

      @Override
      public String getRequestURI() {
        return "/";
      }

      @Override
      public String getContextPath() {
        return "";
      }

      @Override
      public HttpSession getSession() {
        return session;
      }
    };
  }

  /**
   * Returns a fake, HttpServletResponse which throws an exception if any of its methods are called.
   */
  public static HttpServletResponse newFakeHttpServletResponse() {
    return (HttpServletResponse)
        Proxy.newProxyInstance(
            HttpServletResponse.class.getClassLoader(),
            new Class<?>[] {HttpServletResponse.class},
            new ThrowingInvocationHandler());
  }

  private static class FakeHttpSessionHandler implements InvocationHandler, Serializable {
    final Map<String, Object> attributes = Maps.newHashMap();

    @Override
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

  /** Returns a fake, serializable HttpSession which stores attributes in a HashMap. */
  public static HttpSession newFakeHttpSession() {
    return (HttpSession)
        Proxy.newProxyInstance(
            HttpSession.class.getClassLoader(),
            new Class<?>[] {HttpSession.class},
            new FakeHttpSessionHandler());
  }
}
