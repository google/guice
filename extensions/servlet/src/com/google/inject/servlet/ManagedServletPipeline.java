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
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.List;

/**
 * A wrapping dispatcher for servlets, in much the same way as {@link ManagedFilterPipeline} is for
 * filters.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@Singleton
class ManagedServletPipeline extends AbstractServletPipeline {
  private final ServletDefinition[] servletDefinitions;
  private static final TypeLiteral<ServletDefinition> SERVLET_DEFS =
      TypeLiteral.get(ServletDefinition.class);

  @Inject
  public ManagedServletPipeline(Injector injector) {
    this.servletDefinitions = collectServletDefinitions(injector);
  }

  @Override
  protected boolean hasServletsMapped() {
    return servletDefinitions.length > 0;
  }

  @Override
  protected ServletDefinition[] servletDefinitions() {
    return servletDefinitions;
  }

  /**
   * Introspects the injector and collects all instances of bound {@code List<ServletDefinition>}
   * into a master list.
   *
   * We have a guarantee that {@link com.google.inject.Injector#getBindings()} returns a map
   * that preserves insertion order in entry-set iterators.
   */
  private ServletDefinition[] collectServletDefinitions(Injector injector) {
    List<ServletDefinition> servletDefinitions = Lists.newArrayList();
    for (Binding<ServletDefinition> entry : injector.findBindingsByType(SERVLET_DEFS)) {
        servletDefinitions.add(entry.getProvider().get());
    }

    // Copy to a fixed size array for speed.
    return servletDefinitions.toArray(new ServletDefinition[servletDefinitions.size()]);
  }
}
