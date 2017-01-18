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

package com.google.inject.grapher.graphviz;

import com.google.inject.AbstractModule;
import com.google.inject.grapher.NameFactory;
import com.google.inject.grapher.ShortNameFactory;

/**
 * Module that provides classes needed by {@link GraphvizGrapher}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class GraphvizModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(NameFactory.class).annotatedWith(Graphviz.class).to(ShortNameFactory.class);
    bind(PortIdFactory.class).annotatedWith(Graphviz.class).to(PortIdFactoryImpl.class);
  }
}
