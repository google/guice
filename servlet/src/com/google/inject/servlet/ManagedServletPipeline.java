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

import com.google.inject.Injector;
import com.google.inject.Singleton;

import javax.servlet.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A wrapping dispatcher for servlets, in much the same way as {@link ManagedFilterPipeline} is for
 * filters.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @see ManagedFilterPipeline
 */
@Singleton class ManagedServletPipeline {
  private final List<ServletDefinition> servletDefinitions;

  public ManagedServletPipeline(List<ServletDefinition> servletDefinitions) {
    this.servletDefinitions = Collections.unmodifiableList(servletDefinitions);
  }

  public void init(ServletContext servletContext, Injector injector) throws ServletException {
    for (ServletDefinition servletDefinition : servletDefinitions) {
      servletDefinition.init(servletContext, injector);
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
    for (ServletDefinition servletDefinition : servletDefinitions) {
      servletDefinition.destroy();
    }
  }

  public RequestDispatcher getRequestDispatcher(String path) {
    for (final ServletDefinition servletDefinition : servletDefinitions) {
      if (servletDefinition.shouldServe(path)) {
        return new RequestDispatcher() {

          public void forward(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {

            if (servletResponse.isCommitted()) {
              throw new IllegalStateException("Response has been committed--you can "
                  + "only call forward before committing the response (hint: don't "
                  + "flush buffers)");
            }

            //clear buffer before forwarding
            servletResponse.resetBuffer();

            //now dispatch to the servlet
            servletDefinition.doService(servletRequest, servletResponse);
          }

          public void include(ServletRequest servletRequest, ServletResponse servletResponse)
              throws ServletException, IOException {

            //route to the target servlet
            servletDefinition.doService(servletRequest, servletResponse);
          }
        };
      }
    }

    //otherwise, can't process
    return null;
  }
}
