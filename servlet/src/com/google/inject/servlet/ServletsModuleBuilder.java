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
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Lists;
import com.google.inject.internal.Sets;
import static com.google.inject.name.Names.named;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

    // Ensure that servlets are not bound twice to the same pattern.
    Set<String> servletUris = Sets.newHashSet();
    for (ServletDefinition servletDefinition : servletDefinitions) {
      if (servletUris.contains(servletDefinition.getPattern())) {
        addError("More than one servlet was mapped to the same URI pattern: "
            + servletDefinition.getPattern());
      }
      else {
        servletUris.add(servletDefinition.getPattern());
      }
    }

    // Bind these servlet definitions to a unique random key. Doesn't matter what it is,
    // coz it's never used.
    bind(Key.get(new TypeLiteral<List<ServletDefinition>>() {},
        named(UUID.randomUUID().toString()))).toInstance(servletDefinitions);
  }

  //the first level of the EDSL--
  public ServletModule.ServletKeyBindingBuilder serve(List<String> urlPatterns) {
    return new ServletKeyBindingBuilderImpl(urlPatterns, UriPatternType.SERVLET);
  }

  public ServletModule.ServletKeyBindingBuilder serveRegex(List<String> regexes) {
    return new ServletKeyBindingBuilderImpl(regexes, UriPatternType.REGEX);
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

    public void with(Class<? extends HttpServlet> servletKey,
        Map<String, String> contextParams) {
      with(Key.get(servletKey), contextParams);
    }

    public void with(Key<? extends HttpServlet> servletKey,
        Map<String, String> contextParams) {

      for (String pattern : uriPatterns) {
        servletDefinitions.add(
            new ServletDefinition(pattern, servletKey, UriPatternType.get(uriPatternType, pattern),
                contextParams));
      }
    }
  }
}