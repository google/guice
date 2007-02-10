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

package com.google.inject.struts2;

import com.google.inject.Container;
import com.google.inject.ContainerBuilder;
import com.google.inject.ContainerCreationException;
import com.google.inject.Module;
import com.google.inject.servlet.ServletModule;

import com.opensymphony.xwork2.ObjectFactory;
import com.opensymphony.xwork2.inject.Inject;

import java.util.Map;

public class GuiceObjectFactory extends ObjectFactory {

  Container container;

  @Inject
  public GuiceObjectFactory(
      @Inject("guice.module") String moduleName,
      @Inject("struts.devMode") String developmentMode) {
    ContainerBuilder builder = new ContainerBuilder();

    // Configure default servlet bindings.
    builder.install(new ServletModule());

    try {
      // Instantiate user's module and install it.
      @SuppressWarnings({"unchecked"})
      Class<? extends Module> moduleClass =
          (Class<? extends Module>) Class.forName(moduleName);

      Module module = moduleClass.newInstance();

      module.configure(builder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    System.err.println("devMode: " + developmentMode);

    try {
      container = builder.create(false);
    } catch (ContainerCreationException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }

  public Object buildBean(String clazz, Map map) throws Exception {
    return container.getCreator(Class.forName(clazz)).get();
  }

  public boolean isNoArgConstructorRequired() {
    return false;
  }
}
