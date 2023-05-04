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

import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServlet;

/**
 * Builds the guice module that binds configured servlets, with their wrapper ServletDefinitions. Is
 * part of the binding EDSL. Very similar to {@link com.google.inject.servlet.FiltersModuleBuilder}.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
class ServletsModuleBuilder {

  private final Set<String> servletUris = Sets.newHashSet();
  private final Binder binder;

  public ServletsModuleBuilder(Binder binder) {
    this.binder = binder;
  }

  //the first level of the EDSL--
  public ServletModule.ServletKeyBindingBuilder serve(List<String> urlPatterns) {
    return new ServletKeyBindingBuilderImpl(parsePatterns(UriPatternType.SERVLET, urlPatterns));
  }

  public ServletModule.ServletKeyBindingBuilder serveRegex(List<String> regexes) {
    return new ServletKeyBindingBuilderImpl(parsePatterns(UriPatternType.REGEX, regexes));
  }

  private List<UriPatternMatcher> parsePatterns(UriPatternType type, List<String> patterns) {
    List<UriPatternMatcher> patternMatchers = new ArrayList<>();
    for (String pattern : patterns) {
      if (!servletUris.add(pattern)) {
        binder
            .skipSources(ServletModule.class, ServletsModuleBuilder.class)
            .addError("More than one servlet was mapped to the same URI pattern: " + pattern);
      } else {
        UriPatternMatcher matcher = null;
        try {
          matcher = UriPatternType.get(type, pattern);
        } catch (IllegalArgumentException iae) {
          binder
              .skipSources(ServletModule.class, ServletsModuleBuilder.class)
              .addError("%s", iae.getMessage());
        }
        if (matcher != null) {
          patternMatchers.add(matcher);
        }
      }
    }
    return patternMatchers;
  }

  //non-static inner class so it can access state of enclosing module class
  class ServletKeyBindingBuilderImpl implements ServletModule.ServletKeyBindingBuilder {
    private final List<UriPatternMatcher> uriPatterns;

    private ServletKeyBindingBuilderImpl(List<UriPatternMatcher> uriPatterns) {
      this.uriPatterns = uriPatterns;
    }

    @Override
    public void with(Class<? extends HttpServlet> servletKey) {
      with(Key.get(servletKey));
    }

    @Override
    public void with(Key<? extends HttpServlet> servletKey) {
      with(servletKey, new HashMap<String, String>());
    }

    @Override
    public void with(HttpServlet servlet) {
      with(servlet, new HashMap<String, String>());
    }

    @Override
    public void with(Class<? extends HttpServlet> servletKey, Map<String, String> initParams) {
      with(Key.get(servletKey), initParams);
    }

    @Override
    public void with(Key<? extends HttpServlet> servletKey, Map<String, String> initParams) {
      with(servletKey, initParams, null);
    }

    private void with(
        Key<? extends HttpServlet> servletKey,
        Map<String, String> initParams,
        HttpServlet servletInstance) {
      for (UriPatternMatcher pattern : uriPatterns) {
        binder
            .bind(Key.get(ServletDefinition.class, UniqueAnnotations.create()))
            .toProvider(new ServletDefinition(servletKey, pattern, initParams, servletInstance));
      }
    }

    @Override
    public void with(HttpServlet servlet, Map<String, String> initParams) {
      Key<HttpServlet> servletKey = Key.get(HttpServlet.class, UniqueAnnotations.create());
      binder.bind(servletKey).toInstance(servlet);
      with(servletKey, initParams, servlet);
    }
  }
}
