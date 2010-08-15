/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist;

import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * Apply this filter to enable the HTTP Request unit of work and to have
 * guice-persist manage the lifecycle of all active (module-installed)
 * {@link PersistenceService} instances. The filter automatically starts and
 * stops all registered {@link PersistenceService} instances upon {@link
 * javax.servlet.Filter#init(javax.servlet.FilterConfig)} and {@link
 * javax.servlet.Filter#destroy()}.
 *
 * <p> To be able to use {@link com.google.inject.persist.UnitOfWork#REQUEST},
 * register this filter <b>once</b> in your Guice {@code ServletModule}. It is
 * important that you register this filter before any other filter.
 *
 * <p>
 * Example configuration:
 * <pre>{@code
 *  public class MyModule extends ServletModule {
 *   @literal @Override
 *    public void configureServlets() {
 *      filter("/*").through(PersistenceFilter.class);
 *
 *      serve("/index.html").with(MyHtmlServlet.class);
 *      // Etc.
 *    }
 *  }
 * }</pre>
 * <p>
 * This filter will make sure to initialize and shutdown the underlying
 * persistence engine on filter {@code init()} and {@code destroy()}
 * respectively.
 * <p>
 * Even though all mutable state is package local, this filter is thread safe.
 * This allows you to create injectors concurrently and deploy multiple
 * guice-persist applications within the same JVM or servlet container.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
public final class PersistenceFilter implements Filter {

  public void init(FilterConfig filterConfig) throws ServletException {
  }

  public void destroy() {
  }

  public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse,
      final FilterChain filterChain) throws IOException, ServletException {
    filterChain.doFilter(servletRequest, servletResponse);
  }
}
