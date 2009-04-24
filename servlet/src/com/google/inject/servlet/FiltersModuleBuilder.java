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
import com.google.inject.internal.UniqueAnnotations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;

/**
 * Builds the guice module that binds configured filters, with their
 * wrapper FilterDefinitions. Is part of the binding EDSL. All Filters
 * and Servlets are always bound as singletons.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class FiltersModuleBuilder extends AbstractModule {
  private final List<FilterDefinition> filterDefinitions = Lists.newArrayList();

  //invoked on injector config
  @Override
  protected void configure() {
    // Bind these filter definitions to a unique random key. Doesn't matter what it is,
    // coz it's never used.
    bind(Key.get(new TypeLiteral<List<FilterDefinition>>() {}, UniqueAnnotations.create()))
        .toInstance(filterDefinitions);
  }

  public ServletModule.FilterKeyBindingBuilder filter(List<String> patterns) {
    return new FilterKeyBindingBuilderImpl(patterns, UriPatternType.SERVLET);
  }

  public ServletModule.FilterKeyBindingBuilder filterRegex(List<String> regexes) {
    return new FilterKeyBindingBuilderImpl(regexes, UriPatternType.REGEX);
  }

  //non-static inner class so it can access state of enclosing module class
  class FilterKeyBindingBuilderImpl implements ServletModule.FilterKeyBindingBuilder {
    private final List<String> uriPatterns;
    private final UriPatternType uriPatternType;

    private FilterKeyBindingBuilderImpl(List<String> uriPatterns, UriPatternType uriPatternType) {
      this.uriPatterns = uriPatterns;
      this.uriPatternType = uriPatternType;
    }

    public void through(Class<? extends Filter> filterKey) {
      through(Key.get(filterKey));
    }

    public void through(Key<? extends Filter> filterKey) {
      through(filterKey, new HashMap<String, String>());
    }

    public void through(Class<? extends Filter> filterKey,
        Map<String, String> contextParams) {
      
      // Careful you don't accidentally make this method recursive, thank you IntelliJ IDEA!
      through(Key.get(filterKey), contextParams);
    }

    public void through(Key<? extends Filter> filterKey,
        Map<String, String> contextParams) {

      for (String pattern : uriPatterns) {
        filterDefinitions.add(
            new FilterDefinition(pattern, filterKey, UriPatternType.get(uriPatternType, pattern),
                contextParams));
      }
    }
  }
}
