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

import com.google.inject.ImplementedBy;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * An internal dispatcher for guice-servlet registered servlets and filters.
 * By default, we assume a Guice 1.0 style servlet module is in play. In other
 * words, we dispatch directly to the web.xml pipeline after setting up scopes.
 *
 * <p>
 * If on the other hand, {@link ServletModule} is used to register managed
 * servlets and/or filters, then a different pipeline is bound instead. Which,
 * after dispatching to Guice-injected filters and servlets continues to the web.xml
 * pipeline (if necessary).
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@ImplementedBy(DefaultFilterPipeline.class)
interface FilterPipeline {
  void initPipeline(ServletContext context) throws ServletException;
  void destroyPipeline();

  void dispatch(ServletRequest request, ServletResponse response,
      FilterChain defaultFilterChain) throws IOException, ServletException;
}
