/**
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

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Maps;
import com.google.inject.internal.Preconditions;
import com.google.inject.internal.Sets;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

/**
 * A wrapping dispatcher for servlets, in much the same way as {@link ManagedFilterPipeline} is for
 * filters.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class ManagedServletPipeline {
  private final List<ServletDefinition> servletDefinitions;
  private static final TypeLiteral<List<ServletDefinition>> SERVLET_DEFS =
      new TypeLiteral<List<ServletDefinition>>() {};

  @Inject
  public ManagedServletPipeline(Injector injector) {
    this.servletDefinitions = Collections.unmodifiableList(collectServletDefinitions(injector));
  }

  /**
   * Introspects the injector and collects all instances of bound {@code List<ServletDefinition>}
   * into a master list.
   *
   * We have a guarantee that {@link com.google.inject.Injector#getBindings()} returns a map
   * that preserves insertion order in entry-set iterators.
   */
  private List<ServletDefinition> collectServletDefinitions(Injector injector) {
    List<ServletDefinition> servletDefinitions = Lists.newArrayList();
    for (Binding<?> entry : injector.findBindingsByType(SERVLET_DEFS)) {

        @SuppressWarnings("unchecked") //guarded by findBindingsByType()
        Key<List<ServletDefinition>> defsKey = (Key<List<ServletDefinition>>) entry.getKey();
        servletDefinitions.addAll(injector.getInstance(defsKey));
    }

    return servletDefinitions;
  }

  public void init(ServletContext servletContext, Injector injector) throws ServletException {
    Set<HttpServlet> initializedSoFar
        = Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap());

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
    Set<HttpServlet> destroyedSoFar
        = Sets.newSetFromMap(Maps.<HttpServlet, Boolean>newIdentityHashMap());
    for (ServletDefinition servletDefinition : servletDefinitions) {
      servletDefinition.destroy(destroyedSoFar);
    }
  }

  /**
   * @return Returns a request dispatcher wrapped with a servlet mapped to
   * the given path or null if no mapping was found.
   */
  RequestDispatcher getRequestDispatcher(String path) {
    for (final ServletDefinition servletDefinition : servletDefinitions) {
      if (servletDefinition.shouldServe(path)) {
        return new RequestDispatcher() {

          public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {

            Preconditions.checkState(!servletResponse.isCommitted(),
                "Response has been committed--you can only call forward before"
                + " committing the response (hint: don't flush buffers)");

            // clear buffer before forwarding
            servletResponse.resetBuffer();

            // now dispatch to the servlet
            servletDefinition.doService(servletRequest, servletResponse);
          }

          public void include(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {

            // route to the target servlet
            servletDefinition.doService(servletRequest, servletResponse);
          }
        };
      }
    }

    //otherwise, can't process
    return null;
  }
}
