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

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * A Filter chain impl which basically passes itself to the "current" filter and iterates the chain
 * on {@code doFilter()}. Modeled on something similar in Apache Tomcat.
 *
 * <p>Following this, it attempts to dispatch to guice-servlet's registered servlets using the
 * ManagedServletPipeline.
 *
 * <p>And the end, it proceeds to the web.xml (default) servlet filter chain, if needed.
 *
 * @author Dhanji R. Prasanna
 * @since 1.0
 */
class FilterChainInvocation implements FilterChain {

  private static final ImmutableSet<String> SERVLET_INTERNAL_METHODS =
      ImmutableSet.of(FilterChainInvocation.class.getName() + ".doFilter");

  private final FilterDefinition[] filterDefinitions;
  private final FilterChain proceedingChain;
  private final ManagedServletPipeline servletPipeline;

  //state variable tracks current link in filterchain
  private int index = -1;
  // whether or not we've caught an exception & cleaned up stack traces
  private boolean cleanedStacks = false;

  public FilterChainInvocation(
      FilterDefinition[] filterDefinitions,
      ManagedServletPipeline servletPipeline,
      FilterChain proceedingChain) {

    this.filterDefinitions = filterDefinitions;
    this.servletPipeline = servletPipeline;
    this.proceedingChain = proceedingChain;
  }

  @Override
  public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
      throws IOException, ServletException {
    GuiceFilter.Context previous = GuiceFilter.localContext.get();
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;
    HttpServletRequest originalRequest =
        (previous != null) ? previous.getOriginalRequest() : request;
    GuiceFilter.localContext.set(new GuiceFilter.Context(originalRequest, request, response));
    try {
      Filter filter = findNextFilter(request);
      if (filter != null) {
        // call to the filter, which can either consume the request or
        // recurse back into this method. (in which case we will go to find the next filter,
        // or dispatch to the servlet if no more filters are left)
        filter.doFilter(servletRequest, servletResponse, this);
      } else {
        //we've reached the end of the filterchain, let's try to dispatch to a servlet
        final boolean serviced = servletPipeline.service(servletRequest, servletResponse);

        //dispatch to the normal filter chain only if one of our servlets did not match
        if (!serviced) {
          proceedingChain.doFilter(servletRequest, servletResponse);
        }
      }
    } catch (Throwable t) {
      // Only clean on the first pass through -- one exception deep in a filter
      // will propogate upward & hit this catch clause multiple times.  We don't
      // want to iterate through the stack elements for every filter.
      if (!cleanedStacks) {
        cleanedStacks = true;
        pruneStacktrace(t);
      }
      Throwables.propagateIfInstanceOf(t, ServletException.class);
      Throwables.propagateIfInstanceOf(t, IOException.class);
      throw Throwables.propagate(t);
    } finally {
      GuiceFilter.localContext.set(previous);
    }
  }

  /**
   * Iterates over the remaining filter definitions. Returns the first applicable filter, or null if
   * none apply.
   */
  private Filter findNextFilter(HttpServletRequest request) {
    while (++index < filterDefinitions.length) {
      Filter filter = filterDefinitions[index].getFilterIfMatching(request);
      if (filter != null) {
        return filter;
      }
    }
    return null;
  }

  /**
   * Removes stacktrace elements related to AOP internal mechanics from the throwable's stack trace
   * and any causes it may have.
   */
  private void pruneStacktrace(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      StackTraceElement[] stackTrace = t.getStackTrace();
      List<StackTraceElement> pruned = Lists.newArrayList();
      for (StackTraceElement element : stackTrace) {
        String name = element.getClassName() + "." + element.getMethodName();
        if (!SERVLET_INTERNAL_METHODS.contains(name)) {
          pruned.add(element);
        }
      }
      t.setStackTrace(pruned.toArray(new StackTraceElement[pruned.size()]));
    }
  }
}
