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
import com.google.inject.Module;
import com.google.inject.servlet.Servlets.FilterBindingBuilder;
import com.google.inject.servlet.Servlets.ServletBindingBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.Filter;

/**
 * Builds the guice module that binds configured filters, with their wrapper FilterDefinitions. Is
 * part of the binding EDSL.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @see com.google.inject.servlet.Servlets#configure
 */
class FiltersModuleBuilder extends AbstractModule implements FilterBindingBuilder {
  private List<FilterDefinition> filterDefinitions = Lists.newArrayList();

  //invoked on injector config
  @Override
  protected void configure() {
    //bind these filter definitions to a singleton dispatcher pipeline (overrides the
    // DefaultFilterPipeline)
    bind(FilterPipeline.class).toInstance(new ManagedFilterPipeline(filterDefinitions));
  }

  //the first level of the EDSL--
  public FilterKeyBindingBuilder filter(String urlPattern) {
    return new FilterKeyBindingBuilderImpl(urlPattern, UriPatternType.SERVLET);
  }

  public FilterKeyBindingBuilder filterRegex(String regex) {
    return new FilterKeyBindingBuilderImpl(regex, UriPatternType.REGEX);
  }

  public ServletBindingBuilder servlets() {
    return new ServletsModuleBuilder(this);
  }

  //shortcut method if there are no servlets to configure
  public Module buildModule() {
    return new ServletsModuleBuilder(this);
  }

  //non-static inner class so it can access state of enclosing module class
  private class FilterKeyBindingBuilderImpl implements FilterKeyBindingBuilder {
    private final String uriPattern;
    private final UriPatternType uriPatternType;

    private FilterKeyBindingBuilderImpl(String uriPattern, UriPatternType uriPatternType) {
      this.uriPattern = uriPattern;
      this.uriPatternType = uriPatternType;
    }

    public FilterBindingBuilder through(Class<? extends Filter> filterKey) {
      return through(Key.get(filterKey));
    }

    public FilterBindingBuilder through(Key<? extends Filter> filterKey) {
      return through(filterKey, new HashMap<String, String>());
    }

    public FilterBindingBuilder through(Class<? extends Filter> filterKey,
        Map<String, String> contextParams) {
      //careful you don't accidentally make this method recursive!! thank you IntelliJ IDEA!
      return through(Key.get(filterKey), contextParams);
    }

    public FilterBindingBuilder through(Key<? extends Filter> filterKey,
        Map<String, String> contextParams) {
      filterDefinitions.add(
          new FilterDefinition(uriPattern, filterKey, UriPatternType.get(uriPatternType),
              contextParams));

      return FiltersModuleBuilder.this;
    }
  }
}
