/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.example;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import static com.google.inject.example.JndiProvider.fromJndi;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;

class JndiProviderClient {

  public static void main(String[] args) throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
// Bind Context to the default InitialContext.
bind(Context.class).to(InitialContext.class);

// Bind to DataSource from JNDI.
bind(DataSource.class)
    .toProvider(fromJndi(DataSource.class, "..."));
      }
    });
  }
}
