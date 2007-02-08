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
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;

/**
 * Apply this filter to all requests where you plan to use servlet scopes.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class GuiceFilter implements Filter {

  static ThreadLocal<HttpServletRequest> localRequest =
      new ThreadLocal<HttpServletRequest>();

  public void doFilter(ServletRequest servletRequest,
      ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    HttpServletRequest previous = localRequest.get();
    try {
      localRequest.set((HttpServletRequest) servletRequest);
      filterChain.doFilter(servletRequest, servletResponse);
    } finally {
      localRequest.set(previous);
    }
  }

  static HttpServletRequest getRequest() {
    HttpServletRequest request = localRequest.get();
    if (request == null) {
      throw new RuntimeException("Please apply " + GuiceFilter.class.getName()
        + " to any request which uses servlet scopes.");
    }
    return request;
  }

  public void init(FilterConfig filterConfig) throws ServletException {}
  public void destroy() {}
}
