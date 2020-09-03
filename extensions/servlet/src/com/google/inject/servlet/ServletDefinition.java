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

import com.google.common.collect.Iterators;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * An internal representation of a servlet definition mapped to a particular URI pattern. Also
 * performs the request dispatch to that servlet. How nice and OO =)
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class ServletDefinition implements ProviderWithExtensionVisitor<ServletDefinition> {
  private final Key<? extends HttpServlet> servletKey;
  private final UriPatternMatcher patternMatcher;
  private final Map<String, String> initParams;
  // set only if this was bound using a servlet instance.
  private final HttpServlet servletInstance;

  //always set in init, our servlet is always presumed to be a singleton
  private final AtomicReference<HttpServlet> httpServlet = new AtomicReference<>();

  public ServletDefinition(
      Key<? extends HttpServlet> servletKey,
      UriPatternMatcher patternMatcher,
      Map<String, String> initParams,
      HttpServlet servletInstance) {
    this.servletKey = servletKey;
    this.patternMatcher = patternMatcher;
    this.initParams = Collections.unmodifiableMap(new HashMap<String, String>(initParams));
    this.servletInstance = servletInstance;
  }

  @Override
  public ServletDefinition get() {
    return this;
  }

  @Override
  public <B, V> V acceptExtensionVisitor(
      BindingTargetVisitor<B, V> visitor, ProviderInstanceBinding<? extends B> binding) {
    if (visitor instanceof ServletModuleTargetVisitor) {
      if (servletInstance != null) {
        return ((ServletModuleTargetVisitor<B, V>) visitor)
            .visit(new InstanceServletBindingImpl(initParams, servletInstance, patternMatcher));
      } else {
        return ((ServletModuleTargetVisitor<B, V>) visitor)
            .visit(new LinkedServletBindingImpl(initParams, servletKey, patternMatcher));
      }
    } else {
      return visitor.visit(binding);
    }
  }

  boolean shouldServe(String uri) {
    return uri != null && patternMatcher.matches(uri);
  }

  public void init(
      final ServletContext servletContext, Injector injector, Set<HttpServlet> initializedSoFar)
      throws ServletException {

    // This absolutely must be a singleton, and so is only initialized once.
    if (!Scopes.isSingleton(injector.getBinding(servletKey))) {
      throw new ServletException(
          "Servlets must be bound as singletons. "
              + servletKey
              + " was not bound in singleton scope.");
    }

    HttpServlet httpServlet = injector.getInstance(servletKey);
    this.httpServlet.set(httpServlet);

    // Only fire init() if we have not appeared before in the filter chain.
    if (initializedSoFar.contains(httpServlet)) {
      return;
    }

    // initialize our servlet with the configured context params and servlet context
    httpServlet.init(
        new ServletConfig() {
          @Override
          public String getServletName() {
            return servletKey.toString();
          }

          @Override
          public ServletContext getServletContext() {
            return servletContext;
          }

          @Override
          public String getInitParameter(String s) {
            return initParams.get(s);
          }

          @Override
          public Enumeration<?> getInitParameterNames() {
            return Iterators.asEnumeration(initParams.keySet().iterator());
          }
        });

    // Mark as initialized.
    initializedSoFar.add(httpServlet);
  }

  public void destroy(Set<HttpServlet> destroyedSoFar) {
    HttpServlet reference = httpServlet.get();

    // Do nothing if this Servlet was invalid (usually due to not being scoped
    // properly). According to Servlet Spec: it is "out of service", and does not
    // need to be destroyed.
    // Also prevent duplicate destroys to the same singleton that may appear
    // more than once on the filter chain.
    if (null == reference || destroyedSoFar.contains(reference)) {
      return;
    }

    try {
      reference.destroy();
    } finally {
      destroyedSoFar.add(reference);
    }
  }

  /**
   * Wrapper around the service chain to ensure a servlet is servicing what it must and provides it
   * with a wrapped request.
   *
   * @return Returns true if this servlet triggered for the given request. Or false if guice-servlet
   *     should continue dispatching down the servlet pipeline.
   * @throws IOException If thrown by underlying servlet
   * @throws ServletException If thrown by underlying servlet
   */
  public boolean service(ServletRequest servletRequest, ServletResponse servletResponse)
      throws IOException, ServletException {

    final HttpServletRequest request = (HttpServletRequest) servletRequest;
    final String path = ServletUtils.getContextRelativePath(request);

    final boolean serve = shouldServe(path);

    //invocations of the chain end at the first matched servlet
    if (serve) {
      doService(servletRequest, servletResponse);
    }

    //return false if no servlet matched (so we can proceed down to the web.xml servlets)
    return serve;
  }

  /**
   * Utility that delegates to the actual service method of the servlet wrapped with a contextual
   * request (i.e. with correctly computed path info).
   *
   * <p>We need to suppress deprecation coz we use HttpServletRequestWrapper, which implements
   * deprecated API for backwards compatibility.
   */
  void doService(final ServletRequest servletRequest, ServletResponse servletResponse)
      throws ServletException, IOException {

    HttpServletRequest request =
        new HttpServletRequestWrapper((HttpServletRequest) servletRequest) {
          private boolean pathComputed;
          private String path;

          private boolean pathInfoComputed;
          private String pathInfo;

          @Override
          public String getPathInfo() {
            if (!isPathInfoComputed()) {
              String servletPath = getServletPath();
              int servletPathLength = servletPath.length();
              String requestUri = getRequestURI();
              pathInfo = requestUri.substring(getContextPath().length()).replaceAll("[/]{2,}", "/");
              // See: https://github.com/google/guice/issues/372
              if (pathInfo.startsWith(servletPath)) {
                pathInfo = pathInfo.substring(servletPathLength);
                // Corner case: when servlet path & request path match exactly (without trailing '/'),
                // then pathinfo is null.
                if (pathInfo.isEmpty() && servletPathLength > 0) {
                  pathInfo = null;
                } else {
                  try {
                    pathInfo = new URI(pathInfo).getPath();
                  } catch (URISyntaxException e) {
                    // ugh, just leave it alone then
                  }
                }
              } else {
                pathInfo = null; // we know nothing additional about the URI.
              }
              pathInfoComputed = true;
            }

            return pathInfo;
          }

          // NOTE(user): These two are a bit of a hack to help ensure that request dispatcher-sent
          // requests don't use the same path info that was memoized for the original request.
          // NOTE(user): I don't think this is possible, since the dispatcher-sent request would
          // perform its own wrapping.
          private boolean isPathInfoComputed() {
            return pathInfoComputed
                && servletRequest.getAttribute(REQUEST_DISPATCHER_REQUEST) == null;
          }

          private boolean isPathComputed() {
            return pathComputed && servletRequest.getAttribute(REQUEST_DISPATCHER_REQUEST) == null;
          }

          @Override
          public String getServletPath() {
            return computePath();
          }

          @Override
          public String getPathTranslated() {
            final String info = getPathInfo();

            return (null == info) ? null : getRealPath(info);
          }

          // Memoizer pattern.
          private String computePath() {
            if (!isPathComputed()) {
              String servletPath = super.getServletPath();
              path = patternMatcher.extractPath(servletPath);
              pathComputed = true;

              if (null == path) {
                path = servletPath;
              }
            }

            return path;
          }
        };

    doServiceImpl(request, (HttpServletResponse) servletResponse);
  }

  private void doServiceImpl(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    GuiceFilter.Context previous = GuiceFilter.localContext.get();
    HttpServletRequest originalRequest =
        (previous != null) ? previous.getOriginalRequest() : request;
    GuiceFilter.localContext.set(new GuiceFilter.Context(originalRequest, request, response));
    try {
      httpServlet.get().service(request, response);
    } finally {
      GuiceFilter.localContext.set(previous);
    }
  }

  String getKey() {
    return servletKey.toString();
  }
}
