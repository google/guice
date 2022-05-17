/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.inject.servlet.jee;

import java.io.IOException;
import jakarta.inject.Inject;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * This default pipeline simply dispatches to web.xml's servlet pipeline.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @see ManagedFilterPipeline See Also ManagedFilterPipeline.
 */
class DefaultFilterPipeline implements FilterPipeline {
  @Inject
  DefaultFilterPipeline() {}

  @Override
  public void initPipeline(ServletContext context) {}

  @Override
  public void destroyPipeline() {}

  @Override
  public void dispatch(
      ServletRequest request, ServletResponse response, FilterChain proceedingFilterChain)
      throws IOException, ServletException {

    proceedingFilterChain.doFilter(request, response);
  }
}
