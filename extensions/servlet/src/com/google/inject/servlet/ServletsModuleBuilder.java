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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Sets;
import com.google.inject.internal.UniqueAnnotations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private final List<ServletInstanceBindingEntry> servletInstanceEntries = Lists.newArrayList();

  //invoked on injector config
  @Override
  protected void configure() {
    // Create bindings for servlet instances
    for (ServletInstanceBindingEntry entry : servletInstanceEntries) {
      bind(entry.key).toInstance(entry.servlet);
    }

    // Ensure that servlets are not bound twice to the same pattern.
    Set<String> servletUris = Sets.newHashSet();
    for (ServletDefinition servletDefinition : servletDefinitions) {
      if (servletUris.contains(servletDefinition.getPattern())) {
        addError("More than one servlet was mapped to the same URI pattern: "
            + servletDefinition.getPattern());
      }
      else {
        bind(Key.get(ServletDefinition.class, UniqueAnnotations.create())).toProvider(servletDefinition);
        servletUris.add(servletDefinition.getPattern());
      }
    }
  }

  //the first level of the EDSL--
  public ServletModule.ServletKeyBindingBuilder serve(List<String> urlPatterns) {
    return new ServletKeyBindingBuilderImpl(urlPatterns, UriPatternType.SERVLET);
  }

  public ServletModule.ServletKeyBindingBuilder serveRegex(List<String> regexes) {
    return new ServletKeyBindingBuilderImpl(regexes, UriPatternType.REGEX);
  }

  private static class ServletInstanceBindingEntry {
    final Key<HttpServlet> key;
    final HttpServlet servlet;

    ServletInstanceBindingEntry(Key<HttpServlet> key, HttpServlet servlet) {
      this.key = key;
      this.servlet = servlet;
    }
  }

  //non-static inner class so it can access state of enclosing module class
  class ServletKeyBindingBuilderImpl implements ServletModule.ServletKeyBindingBuilder {
    private final List<String> uriPatterns;
    private final UriPatternType uriPatternType;

    private ServletKeyBindingBuilderImpl(List<String> uriPatterns, UriPatternType uriPatternType) {
      this.uriPatterns = uriPatterns;
      this.uriPatternType = uriPatternType;
    }

    public void with(Class<? extends HttpServlet> servletKey) {
      with(Key.get(servletKey));
    }

    public void with(Key<? extends HttpServlet> servletKey) {
      with(servletKey, new HashMap<String, String>());
    }

    public void with(HttpServlet servlet) {
      with(servlet, new HashMap<String, String>());
    }

    public void with(Class<? extends HttpServlet> servletKey,
        Map<String, String> initParams) {
      with(Key.get(servletKey), initParams);
    }

    public void with(Key<? extends HttpServlet> servletKey,
        Map<String, String> initParams) {
      with(servletKey, initParams, null);
    }
    
    private void with(Key<? extends HttpServlet> servletKey,
        Map<String, String> initParams,
        HttpServlet servletInstance) {
      for (String pattern : uriPatterns) {
        servletDefinitions.add(
            new ServletDefinition(pattern, servletKey, UriPatternType.get(uriPatternType, pattern),
                initParams, servletInstance));
      }
    }

    public void with(HttpServlet servlet,
        Map<String, String> initParams) {
      Key<HttpServlet> servletKey = Key.get(HttpServlet.class, UniqueAnnotations.create());
      servletInstanceEntries.add(new ServletInstanceBindingEntry(servletKey, servlet));
      with(servletKey, initParams, servlet);
    }
  }
}
