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

import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServlet;

/**
 * Builds the guice module that binds configured servlets, with their
 * wrapper ServletDefinitions. Is part of the binding EDSL. Very similar to
 * {@link com.google.inject.servlet.FiltersModuleBuilder}.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
class ServletsModuleBuilder extends AbstractModule {
  private final List<ServletDefinition> servletDefinitions = Lists.newArrayList();

  //invoked on injector config
  @Override
  protected void configure() {
    // Bind these servlet definitions to a singleton pipeline
    bind(ManagedServletPipeline.class).toInstance(new ManagedServletPipeline(servletDefinitions));
  }

  //the first level of the EDSL--
  public ServletModule.ServletKeyBindingBuilder serve(String urlPattern) {
    return new ServletKeyBindingBuilderImpl(urlPattern, UriPatternType.SERVLET);
  }

  public ServletModule.ServletKeyBindingBuilder serveRegex(String regex) {
    return new ServletKeyBindingBuilderImpl(regex, UriPatternType.REGEX);
  }

  //non-static inner class so it can access state of enclosing module class
  class ServletKeyBindingBuilderImpl implements ServletModule.ServletKeyBindingBuilder {
    private final String uriPattern;
    private final UriPatternType uriPatternType;

    private ServletKeyBindingBuilderImpl(String uriPattern, UriPatternType uriPatternType) {
      this.uriPattern = uriPattern;
      this.uriPatternType = uriPatternType;
    }

    public void with(Class<? extends HttpServlet> servletKey) {
      with(Key.get(servletKey));
    }

    public void with(Key<? extends HttpServlet> servletKey) {
      with(servletKey, new HashMap<String, String>());
    }

    public void with(Class<? extends HttpServlet> servletKey,
        Map<String, String> contextParams) {
      with(Key.get(servletKey), contextParams);
    }

    public void with(Key<? extends HttpServlet> servletKey,
        Map<String, String> contextParams) {
      servletDefinitions.add(
          new ServletDefinition(uriPattern, servletKey, UriPatternType.get(uriPatternType),
              contextParams));
    }
  }
}