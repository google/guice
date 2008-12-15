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
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Register your own subclass of this as a servlet context listener if you wish
 * to have injectable servlets that extend {@link InjectedHttpServlet}.
 *
 * <p>
 * As of Guice 2.0, {@code InjectedHttpServlet} is deprecated, however you can still
 * use (your subclasses of) {@code GuiceServletContextListener} class as a logical
 * place to create and configure your injector. Though it is not required that you
 * do so, any more. Creating an injector anywhere, with {@link Servlets#configure}
 * will work.
 * 
 * @author Kevin Bourrillion (kevinb@google.com)
 */
public abstract class GuiceServletContextListener
    implements ServletContextListener {

  static final String INJECTOR_NAME = Injector.class.getName();

  public void contextInitialized(ServletContextEvent servletContextEvent) {
    ServletContext servletContext = servletContextEvent.getServletContext();
    servletContext.setAttribute(INJECTOR_NAME, getInjector());
  }

  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    ServletContext servletContext = servletContextEvent.getServletContext();
    servletContext.removeAttribute(INJECTOR_NAME);
  }

  /**
   * Override this method to create (or otherwise obtain a reference to) your
   * injector.
   */
  protected abstract Injector getInjector();
}
