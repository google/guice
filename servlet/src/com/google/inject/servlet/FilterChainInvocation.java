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

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * A Filter chain impl which basically passes itself to the "current" filter and iterates the chain
 * on {@code doFilter()}. Modeled on something similar in Apache Tomcat.
 *
 * Following this, it attempts to dispatch to guice-servlet's registered servlets using the
 * ManagedServletPipeline.
 *
 * And the end, it proceeds to the web.xml (default) servlet filter chain, if needed.
 *
 * @author Dhanji R. Prasanna
 * @since 1.0
 */
class FilterChainInvocation implements FilterChain {
  private final FilterDefinition[] filterDefinitions;
  private final FilterChain proceedingChain;
  private final ManagedServletPipeline servletPipeline;

  //state variable tracks current link in filterchain
  private int index = -1;

  public FilterChainInvocation(FilterDefinition[] filterDefinitions,
      ManagedServletPipeline servletPipeline, FilterChain proceedingChain) {

    this.filterDefinitions = filterDefinitions;
    this.servletPipeline = servletPipeline;
    this.proceedingChain = proceedingChain;
  }

  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
      throws IOException, ServletException {
    index++;

    //dispatch down the chain while there are more filters
    if (index < filterDefinitions.length) {
      filterDefinitions[index].doFilter(servletRequest, servletResponse, this);
    } else {

      //we've reached the end of the filterchain, let's try to dispatch to a servlet
      final boolean serviced = servletPipeline.service(servletRequest, servletResponse);

      //dispatch to the normal filter chain only if one of our servlets did not match
      if (!serviced) {
        proceedingChain.doFilter(servletRequest, servletResponse);
      }
    }
  }
}
