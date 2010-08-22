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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Central routing/dispatch class handles lifecycle of managed filters, and delegates to the servlet
 * pipeline.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class ManagedFilterPipeline implements FilterPipeline{
  private final FilterDefinition[] filterDefinitions;
  private final ManagedServletPipeline servletPipeline;
  private final Provider<ServletContext> servletContext;

  //Unfortunately, we need the injector itself in order to create filters + servlets
  private final Injector injector;

  //Guards a DCL, so needs to be volatile
  private volatile boolean initialized = false;
  private static final TypeLiteral<FilterDefinition> FILTER_DEFS =
      TypeLiteral.get(FilterDefinition.class);

  @Inject
  public ManagedFilterPipeline(Injector injector, ManagedServletPipeline servletPipeline,
      Provider<ServletContext> servletContext) {
    this.injector = injector;
    this.servletPipeline = servletPipeline;
    this.servletContext = servletContext;

    this.filterDefinitions = collectFilterDefinitions(injector);
  }

  /**
   * Introspects the injector and collects all instances of bound {@code List<FilterDefinition>}
   * into a master list.
   * 
   * We have a guarantee that {@link com.google.inject.Injector#getBindings()} returns a map
   * that preserves insertion order in entry-set iterators.
   */
  private FilterDefinition[] collectFilterDefinitions(Injector injector) {
    List<FilterDefinition> filterDefinitions = Lists.newArrayList();
    for (Binding<FilterDefinition> entry : injector.findBindingsByType(FILTER_DEFS)) {
      filterDefinitions.add(entry.getProvider().get());
    }

    // Convert to a fixed size array for speed.
    return filterDefinitions.toArray(new FilterDefinition[filterDefinitions.size()]);
  }

  public synchronized void initPipeline(ServletContext servletContext)
      throws ServletException {

    //double-checked lock, prevents duplicate initialization
    if (initialized)
      return;

    // Used to prevent duplicate initialization.
    Set<Filter> initializedSoFar = Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap());

    for (FilterDefinition filterDefinition : filterDefinitions) {
      filterDefinition.init(servletContext, injector, initializedSoFar);
    }

    //next, initialize servlets...
    servletPipeline.init(servletContext, injector);

    //everything was ok...
    initialized = true;
  }

  public void dispatch(ServletRequest request, ServletResponse response,
      FilterChain proceedingFilterChain) throws IOException, ServletException {

    //lazy init of filter pipeline (OK by the servlet specification). This is needed
    //in order for us not to force users to create a GuiceServletContextListener subclass.
    if (!initialized) {
      initPipeline(servletContext.get());
    }

    //obtain the servlet pipeline to dispatch against
    new FilterChainInvocation(filterDefinitions, servletPipeline, proceedingFilterChain)
        .doFilter(withDispatcher(request, servletPipeline), response);

  }

  /**
   * Used to create an proxy that dispatches either to the guice-servlet pipeline or the regular
   * pipeline based on uri-path match. This proxy also provides minimal forwarding support.
   *
   * We cannot forward from a web.xml Servlet/JSP to a guice-servlet (because the filter pipeline
   * is not called again). However, we can wrap requests with our own dispatcher to forward the
   * *other* way. web.xml Servlets/JSPs can forward to themselves as per normal.
   *
   * This is not a problem cuz we intend for people to migrate from web.xml to guice-servlet,
   * incrementally, but not the other way around (which, we should actively discourage).
   */
  @SuppressWarnings({ "JavaDoc", "deprecation" })
  private ServletRequest withDispatcher(ServletRequest servletRequest,
      final ManagedServletPipeline servletPipeline) {

    HttpServletRequest request = (HttpServletRequest) servletRequest;

    // don't wrap the request if there are no servlets mapped. This prevents us from inserting our
    // wrapper unless it's actually going to be used. This is necessary for compatibility for apps
    // that downcast their HttpServletRequests to a concrete implementation.
    if (!servletPipeline.hasServletsMapped()) {
      return servletRequest;
    }

    //noinspection OverlyComplexAnonymousInnerClass
    return new HttpServletRequestWrapper(request) {

      @Override
      public RequestDispatcher getRequestDispatcher(String path) {
        final RequestDispatcher dispatcher = servletPipeline.getRequestDispatcher(path);

        return (null != dispatcher) ? dispatcher : super.getRequestDispatcher(path);
      }
    };
  }

  public void destroyPipeline() {
    //destroy servlets first
    servletPipeline.destroy();

    //go down chain and destroy all our filters
    Set<Filter> destroyedSoFar = Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap());
    for (FilterDefinition filterDefinition : filterDefinitions) {
      filterDefinition.destroy(destroyedSoFar);
    }
  }
}
