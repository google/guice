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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * A wrapping dispatcher for servlets, in much the same way as {@link ManagedFilterPipeline} is for
 * filters.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class ManagedServletPipeline {
  private final ServletDefinition[] servletDefinitions;
  private static final TypeLiteral<ServletDefinition> SERVLET_DEFS =
      TypeLiteral.get(ServletDefinition.class);

  @Inject
  public ManagedServletPipeline(Injector injector) {
    this.servletDefinitions = collectServletDefinitions(injector);
  }

  boolean hasServletsMapped() {
    return servletDefinitions.length > 0;
  }

  /**
   * Introspects the injector and collects all instances of bound {@code List<ServletDefinition>}
   * into a master list.
   *
   * <p>We have a guarantee that {@link com.google.inject.Injector#getBindings()} returns a map that
   * preserves insertion order in entry-set iterators.
   */
  private ServletDefinition[] collectServletDefinitions(Injector injector) {
    List<ServletDefinition> servletDefinitions = Lists.newArrayList();
    for (Binding<ServletDefinition> entry : injector.findBindingsByType(SERVLET_DEFS)) {
      servletDefinitions.add(entry.getProvider().get());
    }

    // Copy to a fixed size array for speed.
    return servletDefinitions.toArray(new ServletDefinition[servletDefinitions.size()]);
  }

  public void init(ServletContext servletContext, Injector injector) throws ServletException {
    Set<HttpServlet> initializedSoFar = Sets.newIdentityHashSet();

    for (ServletDefinition servletDefinition : servletDefinitions) {
      servletDefinition.init(servletContext, injector, initializedSoFar);
    }
  }

  public boolean service(ServletRequest request, ServletResponse response)
      throws IOException, ServletException {

    //stop at the first matching servlet and service
    for (ServletDefinition servletDefinition : servletDefinitions) {
      if (servletDefinition.service(request, response)) {
        return true;
      }
    }

    //there was no match...
    return false;
  }

  public void destroy() {
    Set<HttpServlet> destroyedSoFar = Sets.newIdentityHashSet();
    for (ServletDefinition servletDefinition : servletDefinitions) {
      servletDefinition.destroy(destroyedSoFar);
    }
  }

  /**
   * @return Returns a request dispatcher wrapped with a servlet mapped to the given path or null if
   *     no mapping was found.
   */
  RequestDispatcher getRequestDispatcher(String path) {
    final String newRequestUri = path;

    // TODO(user): check servlet spec to see if the following is legal or not.
    // Need to strip query string if requested...

    for (final ServletDefinition servletDefinition : servletDefinitions) {
      if (servletDefinition.shouldServe(path)) {
        return new RequestDispatcher() {
          @Override
          public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {
            Preconditions.checkState(
                !servletResponse.isCommitted(),
                "Response has been committed--you can only call forward before"
                    + " committing the response (hint: don't flush buffers)");

            // clear buffer before forwarding
            servletResponse.resetBuffer();

            ServletRequest requestToProcess;
            if (servletRequest instanceof HttpServletRequest) {
              requestToProcess = wrapRequest((HttpServletRequest) servletRequest, newRequestUri);
            } else {
              // This should never happen, but instead of throwing an exception
              // we will allow a happy case pass thru for maximum tolerance to
              // legacy (and internal) code.
              requestToProcess = servletRequest;
            }

            // now dispatch to the servlet
            doServiceImpl(servletDefinition, requestToProcess, servletResponse);
          }

          @Override
          public void include(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {
            // route to the target servlet
            doServiceImpl(servletDefinition, servletRequest, servletResponse);
          }

          private void doServiceImpl(
              ServletDefinition servletDefinition,
              ServletRequest servletRequest,
              ServletResponse servletResponse)
              throws ServletException, IOException {
            servletRequest.setAttribute(REQUEST_DISPATCHER_REQUEST, Boolean.TRUE);

            try {
              servletDefinition.doService(servletRequest, servletResponse);
            } finally {
              servletRequest.removeAttribute(REQUEST_DISPATCHER_REQUEST);
            }
          }
        };
      }
    }

    //otherwise, can't process
    return null;
  }

  // visible for testing
  static HttpServletRequest wrapRequest(HttpServletRequest request, String newUri) {
    return new RequestDispatcherRequestWrapper(request, newUri);
  }

  /**
   * A Marker constant attribute that when present in the request indicates to Guice servlet that
   * this request has been generated by a request dispatcher rather than the servlet pipeline. In
   * accordance with section 8.4.2 of the Servlet 2.4 specification.
   */
  public static final String REQUEST_DISPATCHER_REQUEST = "javax.servlet.forward.servlet_path";

  private static class RequestDispatcherRequestWrapper extends HttpServletRequestWrapper {
    private final String newRequestUri;

    public RequestDispatcherRequestWrapper(
        HttpServletRequest servletRequest, String newRequestUri) {
      super(servletRequest);
      this.newRequestUri = newRequestUri;
    }

    @Override
    public String getRequestURI() {
      return newRequestUri;
    }

    @Override
    public StringBuffer getRequestURL() {
      StringBuffer url = new StringBuffer();
      String scheme = getScheme();
      int port = getServerPort();

      url.append(scheme);
      url.append("://");
      url.append(getServerName());
      // port might be -1 in some cases (see java.net.URL.getPort)
      if (port > 0
          && (("http".equals(scheme) && (port != 80))
              || ("https".equals(scheme) && (port != 443)))) {
        url.append(':');
        url.append(port);
      }
      url.append(getRequestURI());

      return (url);
    }
  }
}
