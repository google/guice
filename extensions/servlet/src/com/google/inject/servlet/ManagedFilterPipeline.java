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
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.List;

import javax.servlet.ServletContext;

/**
 * Managed implementation of a central routing/dispatch class which handles lifecycle of managed
 * filters, and delegates to a managed servlet pipeline.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class ManagedFilterPipeline extends AbstractFilterPipeline {
  private final FilterDefinition[] filterDefinitions;

  private static final TypeLiteral<FilterDefinition> FILTER_DEFS =
      TypeLiteral.get(FilterDefinition.class);

  @Inject
  public ManagedFilterPipeline(Injector injector, ManagedServletPipeline servletPipeline,
      Provider<ServletContext> servletContext) {
    super(injector, servletPipeline, servletContext);

    this.filterDefinitions = collectFilterDefinitions(injector);
  }

  @Override
  protected boolean hasFiltersMapped() {
    return filterDefinitions.length > 0;
  }

  @Override
  protected FilterDefinition[] filterDefinitions() {
    return filterDefinitions;
  }

  /**
   * Introspects the injector and collects all instances of bound {@code List<FilterDefinition>}
   * into a master list.
   * 
   * We have a guarantee that {@link com.google.inject.Injector#getBindings()} returns a map
   * that preserves insertion order in entry-set iterators.
   */
  private FilterDefinition[] collectFilterDefinitions(Injector injector) {
    List<FilterDefinition> filterDefinitions = Lists.newArrayList();
    for (Binding<FilterDefinition> entry : injector.findBindingsByType(FILTER_DEFS)) {
      filterDefinitions.add(entry.getProvider().get());
    }
    
    // Copy to a fixed-size array for speed of iteration.
    return filterDefinitions.toArray(new FilterDefinition[filterDefinitions.size()]);
  }
}
