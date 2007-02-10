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

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Apply this filter to all requests where you plan to use servlet scopes.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class GuiceFilter implements Filter {

  static ThreadLocal<Context> localContext = new ThreadLocal<Context>();

  public void doFilter(ServletRequest servletRequest,
      ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    Context previous = localContext.get();
    try {
      localContext.set(new Context((HttpServletRequest) servletRequest,
          (HttpServletResponse) servletResponse));
      filterChain.doFilter(servletRequest, servletResponse);
    } finally {
      localContext.set(previous);
    }
  }

  static HttpServletRequest getRequest() {
    return getContext().getRequest();
  }

  static HttpServletResponse getResponse() {
    return getContext().getResponse();
  }

  static Context getContext() {
    Context context = localContext.get();
    if (context == null) {
      throw new RuntimeException("Please apply " + GuiceFilter.class.getName()
          + " to any request which uses servlet scopes.");
    }
    return context;
  }

  static class Context {

    final HttpServletRequest request;
    final HttpServletResponse response;

    Context(HttpServletRequest request, HttpServletResponse response) {
      this.request = request;
      this.response = response;
    }

    HttpServletRequest getRequest() {
      return request;
    }

    HttpServletResponse getResponse() {
      return response;
    }
  }

  public void init(FilterConfig filterConfig) throws ServletException {}

  public void destroy() {}
}
