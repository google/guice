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
  private final List<FilterInstanceBindingEntry> filterInstanceEntries = Lists.newArrayList();

  //invoked on injector config
  @Override
  protected void configure() {
    // Create bindings for filter instances
    for (FilterInstanceBindingEntry entry : filterInstanceEntries) {
      bind(entry.key).toInstance(entry.filter);
    }

    // Bind these filter definitions to a unique random key. Doesn't matter what it is,
    // coz it's never used.
    for(FilterDefinition fd : filterDefinitions) {
      bind(FilterDefinition.class).annotatedWith(UniqueAnnotations.create()).toProvider(fd);
    }
  }

  public ServletModule.FilterKeyBindingBuilder filter(List<String> patterns) {
    return new FilterKeyBindingBuilderImpl(patterns, UriPatternType.SERVLET);
  }

  public ServletModule.FilterKeyBindingBuilder filterRegex(List<String> regexes) {
    return new FilterKeyBindingBuilderImpl(regexes, UriPatternType.REGEX);
  }

  private static class FilterInstanceBindingEntry {
    final Key<Filter> key;
    final Filter filter;

    FilterInstanceBindingEntry(Key<Filter> key, Filter filter) {
      this.key = key;
      this.filter = filter;
    }
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

    public void through(Filter filter) {
      through(filter, new HashMap<String, String>());
    }

    public void through(Class<? extends Filter> filterKey,
        Map<String, String> initParams) {
      
      // Careful you don't accidentally make this method recursive, thank you IntelliJ IDEA!
      through(Key.get(filterKey), initParams);
    }

    public void through(Key<? extends Filter> filterKey,
        Map<String, String> initParams) {
      through(filterKey, initParams, null);
    }
    
    private void through(Key<? extends Filter> filterKey,
        Map<String, String> initParams,
        Filter filterInstance) {
      for (String pattern : uriPatterns) {
        filterDefinitions.add(
            new FilterDefinition(pattern, filterKey, UriPatternType.get(uriPatternType, pattern),
                initParams, filterInstance));
      }
    }

    public void through(Filter filter,
        Map<String, String> initParams) {
      Key<Filter> filterKey = Key.get(Filter.class, UniqueAnnotations.create());
      filterInstanceEntries.add(new FilterInstanceBindingEntry(filterKey, filter));
      through(filterKey, initParams, filter);
    }
  }
}
