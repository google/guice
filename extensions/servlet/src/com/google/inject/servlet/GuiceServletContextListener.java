/*
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
import java.lang.ref.WeakReference;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * As of Guice 2.0 you can still use (your subclasses of) {@code GuiceServletContextListener} class
 * as a logical place to create and configure your injector. This will ensure the injector is
 * created when the web application is deployed.
 *
 * @author Kevin Bourrillion (kevinb@google.com)
 * @since 2.0
 */
public abstract class GuiceServletContextListener implements ServletContextListener {

  static final String INJECTOR_NAME = Injector.class.getName();

  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    final ServletContext servletContext = servletContextEvent.getServletContext();

    // Set the Servletcontext early for those people who are using this class.
    // NOTE(user): This use of the servletContext is deprecated.
    GuiceFilter.servletContext = new WeakReference<>(servletContext);

    Injector injector = getInjector();
    injector
        .getInstance(InternalServletModule.BackwardsCompatibleServletContextProvider.class)
        .set(servletContext);
    servletContext.setAttribute(INJECTOR_NAME, injector);
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {
    ServletContext servletContext = servletContextEvent.getServletContext();
    servletContext.removeAttribute(INJECTOR_NAME);
  }

  /** Override this method to create (or otherwise obtain a reference to) your injector. */
  protected abstract Injector getInjector();
}
