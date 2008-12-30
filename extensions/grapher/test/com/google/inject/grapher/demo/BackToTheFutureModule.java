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
package com.google.inject.grapher.demo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Names;

/**
 * Module that adds a variety of different kinds of {@link Bindings} to be used
 * to generate a comprehensive sample graph.
 * 
 * @see InjectorGrapherDemo
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class BackToTheFutureModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(DeLorian.class);

    bind(EnergySource.class).annotatedWith(Nuclear.class).to(Plutonium.class);
    bind(EnergySource.class).annotatedWith(Renewable.class).to(Lightning.class);

    bind(Plutonium.class).toProvider(PlutoniumProvider.class);
    bind(PinballParts.class).annotatedWith(Used.class).toInstance(new PinballParts());

    bind(Person.class).annotatedWith(Driver.class).to(MartyMcFly.class).in(Singleton.class);
    bind(Person.class).annotatedWith(Inventor.class).to(DocBrown.class).in(Singleton.class);
    
    bindConstant().annotatedWith(Names.named("year")).to("1955");
  }
  
  @Provides
  public FluxCapacitor provideFluxCapacitor(EnergySource energySource) {
    return null;
  }
}