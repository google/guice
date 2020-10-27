/*
 * Copyright (C) 2009 Google Inc.
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

import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * This is a left-factoring of all ServletModules installed in the system. In other words, this
 * module contains the bindings common to all ServletModules, and is bound exactly once per
 * injector.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
final class InternalServletModule extends AbstractModule {

  /**
   * Special Provider that tries to obtain an injected servlet context, specific to the current
   * injector, failing which, it falls back to the static singleton instance that is available in
   * the legacy Guice Servlet.
   */
  @Singleton
  static class BackwardsCompatibleServletContextProvider implements Provider<ServletContext> {
    private ServletContext injectedServletContext;

    @Inject
    BackwardsCompatibleServletContextProvider() {}

    // This setter is called by the GuiceServletContextListener
    void set(ServletContext injectedServletContext) {
      this.injectedServletContext = injectedServletContext;
    }

    @Override
    public ServletContext get() {
      if (null != injectedServletContext) {
        return injectedServletContext;
      }

      Logger.getLogger(InternalServletModule.class.getName())
          .warning(
              "You are attempting to use a deprecated API (specifically,"
                  + " attempting to @Inject ServletContext inside an eagerly created"
                  + " singleton. While we allow this for backwards compatibility, be"
                  + " warned that this MAY have unexpected behavior if you have more"
                  + " than one injector (with ServletModule) running in the same JVM."
                  + " Please consult the Guice documentation at"
                  + " https://github.com/google/guice/wiki/Servlets for more"
                  + " information.");
      return GuiceFilter.getServletContext();
    }
  }

  @Override
  protected void configure() {
    bindScope(RequestScoped.class, REQUEST);
    bindScope(SessionScoped.class, SESSION);
    bind(ServletRequest.class).to(HttpServletRequest.class);
    bind(ServletResponse.class).to(HttpServletResponse.class);

    // inject the pipeline into GuiceFilter so it can route requests correctly
    // Unfortunate staticness... =(
    // NOTE(user): This is maintained for legacy purposes.
    requestStaticInjection(GuiceFilter.class);

    bind(ManagedFilterPipeline.class);
    bind(ManagedServletPipeline.class);
    bind(FilterPipeline.class).to(ManagedFilterPipeline.class).asEagerSingleton();

    bind(ServletContext.class).toProvider(BackwardsCompatibleServletContextProvider.class);
    bind(BackwardsCompatibleServletContextProvider.class);
  }

  @Provides
  @Singleton
  @ScopingOnly
  GuiceFilter provideScopingOnlyGuiceFilter() {
    return new GuiceFilter(new DefaultFilterPipeline());
  }

  @Provides
  @RequestScoped
  HttpServletRequest provideHttpServletRequest() {
    return GuiceFilter.getRequest(Key.get(HttpServletRequest.class));
  }

  @Provides
  @RequestScoped
  HttpServletResponse provideHttpServletResponse() {
    return GuiceFilter.getResponse(Key.get(HttpServletResponse.class));
  }

  @Provides
  HttpSession provideHttpSession() {
    return GuiceFilter.getRequest(Key.get(HttpSession.class)).getSession();
  }

  @SuppressWarnings("unchecked") // defined by getParameterMap()
  @Provides
  @RequestScoped
  @RequestParameters
  Map<String, String[]> provideRequestParameters(ServletRequest req) {
    return req.getParameterMap();
  }

  @Override
  public boolean equals(Object o) {
    // Is only ever installed internally, so we don't need to check state.
    return o instanceof InternalServletModule;
  }

  @Override
  public int hashCode() {
    return InternalServletModule.class.hashCode();
  }
}
