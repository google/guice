/**
 * Copyright (C) 2007 Google Inc.
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
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;

/**
 * Base class to be extended by all servlets that desire field and method
 * injection to be performed during {@link javax.servlet.Servlet#init}.
 * Constructor injection is not possible. To use this you must also
 * register your own subclass of {@link GuiceServletContextListener} as a
 * servlet context listener.
 *
 * @author Kevin Bourrillion (kevinb@google.com)
 */
public abstract class InjectedHttpServlet extends HttpServlet {

  @Override public void init(ServletConfig config) throws ServletException {
    super.init(config);
    ServletContext servletContext = config.getServletContext();
    Injector injector = (Injector)
        servletContext.getAttribute(GuiceServletContextListener.INJECTOR_NAME);
    if (injector == null) {
      throw new UnavailableException(
          "Guice Injector not found (did you forget to register a "
          + GuiceServletContextListener.class.getSimpleName() + "?)");
    }
    injector.injectMembers(this);
  }
}
