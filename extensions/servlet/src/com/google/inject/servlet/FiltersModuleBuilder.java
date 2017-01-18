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

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.internal.UniqueAnnotations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;

/**
 * Builds the guice module that binds configured filters, with their wrapper FilterDefinitions. Is
 * part of the binding EDSL. All Filters and Servlets are always bound as singletons.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class FiltersModuleBuilder {

  private final Binder binder;

  public FiltersModuleBuilder(Binder binder) {
    this.binder = binder;
  }

  public ServletModule.FilterKeyBindingBuilder filter(List<String> patterns) {
    return new FilterKeyBindingBuilderImpl(parsePatterns(UriPatternType.SERVLET, patterns));
  }

  public ServletModule.FilterKeyBindingBuilder filterRegex(List<String> regexes) {
    return new FilterKeyBindingBuilderImpl(parsePatterns(UriPatternType.REGEX, regexes));
  }

  private List<UriPatternMatcher> parsePatterns(UriPatternType type, List<String> patterns) {
    List<UriPatternMatcher> patternMatchers = new ArrayList<UriPatternMatcher>();
    for (String pattern : patterns) {
      UriPatternMatcher matcher = null;
      try {
        matcher = UriPatternType.get(type, pattern);
      } catch (IllegalArgumentException iae) {
        binder
            .skipSources(ServletModule.class, FiltersModuleBuilder.class)
            .addError("%s", iae.getMessage());
      }
      if (matcher != null) {
        patternMatchers.add(matcher);
      }
    }
    return patternMatchers;
  }

  //non-static inner class so it can access state of enclosing module class
  class FilterKeyBindingBuilderImpl implements ServletModule.FilterKeyBindingBuilder {
    private final List<UriPatternMatcher> uriPatterns;

    private FilterKeyBindingBuilderImpl(List<UriPatternMatcher> uriPatterns) {
      this.uriPatterns = uriPatterns;
    }

    @Override
    public void through(Class<? extends Filter> filterKey) {
      through(Key.get(filterKey));
    }

    @Override
    public void through(Key<? extends Filter> filterKey) {
      through(filterKey, new HashMap<String, String>());
    }

    @Override
    public void through(Filter filter) {
      through(filter, new HashMap<String, String>());
    }

    @Override
    public void through(Class<? extends Filter> filterKey, Map<String, String> initParams) {

      // Careful you don't accidentally make this method recursive, thank you IntelliJ IDEA!
      through(Key.get(filterKey), initParams);
    }

    @Override
    public void through(Key<? extends Filter> filterKey, Map<String, String> initParams) {
      through(filterKey, initParams, null);
    }

    private void through(
        Key<? extends Filter> filterKey, Map<String, String> initParams, Filter filterInstance) {
      for (UriPatternMatcher pattern : uriPatterns) {
        binder
            .bind(FilterDefinition.class)
            .annotatedWith(UniqueAnnotations.create())
            .toProvider(new FilterDefinition(filterKey, pattern, initParams, filterInstance));
      }
    }

    @Override
    public void through(Filter filter, Map<String, String> initParams) {
      Key<Filter> filterKey = Key.get(Filter.class, UniqueAnnotations.create());
      binder.bind(filterKey).toInstance(filter);
      through(filterKey, initParams, filter);
    }
  }
}
