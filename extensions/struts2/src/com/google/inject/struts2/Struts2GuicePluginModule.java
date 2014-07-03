/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.struts2;

import com.google.inject.AbstractModule;

/**
 * Initializes the Struts 2 Guice Plugin.
 * Must be added to the injector returned by
 *     {@link GuiceServletContextListener.getInjector()}.
 *
 * @author benmccann.com
 */
public class Struts2GuicePluginModule extends AbstractModule {

  @Override
  protected void configure() {
    requestStaticInjection(Struts2Factory.class); 
  }

}
