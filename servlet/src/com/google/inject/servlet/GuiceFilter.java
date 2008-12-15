/**
 * Copyright (C) 2006-2008 Google Inc.
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

import com.google.inject.Inject;
import com.google.inject.OutOfScopeException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>
 * Apply this filter in web.xml above all other filters (typically), to all requests where you plan
 *  to use servlet scopes. This is also needed in order to dispatch requests to injectable filters
 *  and servlets:
 *  <pre>
 *  &lt;filter&gt;
 *    &lt;filter-name&gt;guiceFilter&lt;/filter-name&gt;
 *    &lt;filter-class&gt;<b>com.google.inject.servlet.GuiceFilter</b>&lt;/filter-class&gt;
 *  &lt;/filter&gt;
 *
 *  &lt;filter-mapping&gt;
 *    &lt;filter-name&gt;guiceFilter&lt;/filter-name&gt;
 *    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
 *  &lt;/filter-mapping&gt;
 *  </pre>
 *
 * This filter should appear above every filter that makes use of Guice injection or servlet
 * scopes functionality. Ideally, you want to register ONLY this filter in web.xml and register
 * any other filters using {@link Servlets#configure()}. But this is not strictly necessary.
 *
 * <p>
 * You will generally want to place sitemesh and similar (purely decorative) filters above
 * {@code GuiceFilter} in web.xml.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class GuiceFilter implements Filter {
  static final ThreadLocal<Context> localContext = new ThreadLocal<Context>();
  static volatile WeakReference<FilterPipeline> pipeline =
      new WeakReference<FilterPipeline>(null);

  static volatile WeakReference<ServletContext> servletContext =
      new WeakReference<ServletContext>(null);

  //VisibleForTesting
  @Inject
  static void setPipeline(FilterPipeline pipeline) {

    //multiple injectors with ServletModules!
    if (null != GuiceFilter.pipeline.get()) {
      throw new RuntimeException(
                  "Multiple injectors detected. Please install only one"
                      + " ServletModule in your web application. While you may "
                      + "have more than one injector, you should only configure"
                      + " guice-servlet in one of them. (Hint: look for legacy "
                      + "ServetModules or multiple calls to Servlets.configure())."

          );
    }

    //we obtain the pipeline using a special key, so we can identify if the
    //servlet module was installed properly.
    GuiceFilter.pipeline = new WeakReference<FilterPipeline>(pipeline);
  }

  //VisibleForTesting (only)
  public static void clearPipeline() {
    pipeline = new WeakReference<FilterPipeline>(null);
  }

  public void doFilter(ServletRequest servletRequest,
      ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {

    Context previous = localContext.get();
    FilterPipeline filterPipeline = pipeline.get();

    //not even a default pipeline was available--bad!
    if (null == filterPipeline)
      throw new ServletException("No Guice Injector was present. You should also "
                               + "setup the servlet module by using Servlets.configure()."
                               + " An injector must be present for GuiceFilter to work"
                               + " and for servlet support in your web application.");

    try {
      localContext.set(new Context((HttpServletRequest) servletRequest,
          (HttpServletResponse) servletResponse));

      //dispatch across the servlet pipeline, ensuring web.xml's filterchain is also honored
      filterPipeline.dispatch(servletRequest, servletResponse, filterChain);

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

  public static ServletContext getServletContext() {
    return servletContext.get();
  }

  static Context getContext() {
    Context context = localContext.get();
    if (context == null) {
      throw new OutOfScopeException("Cannot access scoped object. Either we"
          + " are not currently inside an HTTP Servlet request, or you may"
          + " have forgotten to apply " + GuiceFilter.class.getName()
          + " as a servlet filter for this request.");
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

  public void init(FilterConfig filterConfig) throws ServletException {
    final ServletContext servletContext = filterConfig.getServletContext();

    //store servlet context in a weakreference, for injection
    GuiceFilter.servletContext = new WeakReference<ServletContext>(servletContext);
    
    FilterPipeline filterPipeline = GuiceFilter.pipeline.get();

    //we must allow for the possibility that the injector is created *after*
    // GuiceFilter is initialized, to preserve backwards compatibility with Guice 1.0.
    if (null != filterPipeline)
      filterPipeline.initPipeline(servletContext);
  }

  public void destroy() {

    try {
      //destroy all registered filters & servlets in that order
      pipeline.get().destroyPipeline();

    } finally {
      pipeline.clear();
      servletContext.clear();
    }
  }
}
