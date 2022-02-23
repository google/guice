/*
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.jpa;

import static org.junit.Assert.assertEquals;

import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.google.inject.persist.utils.SuiteAndTestResource;
import java.util.Properties;
import org.junit.Rule;
import org.junit.Test;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */

public abstract class BaseEntityManagerFactoryProvisionTest {

  @Rule
  public PersistenceInjectorResource injector = new PersistenceInjectorResource(
      SuiteAndTestResource.Lifecycle.TEST,
      "testUnit",
      module -> module.properties(getCustomProps()));

  protected Properties getCustomProps() {
    return null;
  }

  @Test
  public void testServiceCreatesUnitOfWorkAsSingleton() {
    assertEquals(
        "SINGLETON VIOLATION " + UnitOfWork.class.getName(),
        injector.getInstance(UnitOfWork.class),
        injector.getInstance(UnitOfWork.class));
  }
}
