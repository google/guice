/**
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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.Map;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

/**
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @author Nik Hodgkinson (11xor6@gmail.com)
 */

public class CustomPropsProviderEntityManagerFactoryProvisionTest extends TestCase {
  private Injector injector;

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit").properties(TestPropertiesProvider.class));
  }

  @Override
  public final void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testSessionCreateOnInjection() {

    assertEquals("SINGLETON VIOLATION " + UnitOfWork.class.getName(),
        injector.getInstance(UnitOfWork.class),
        injector.getInstance(UnitOfWork.class));

    //startup persistence
    injector.getInstance(PersistService.class).start();

    //were properties injected?
    Map<?, ?> properties = injector.getInstance(Key.get(new TypeLiteral<Map<?, ?>>() {}, Jpa.class));
    assertTrue(properties.containsKey("foo") && properties.get("foo").equals("bar"));

    //obtain em
    assertTrue(injector.getInstance(EntityManager.class).isOpen());
  }

  private static class TestPropertiesProvider implements Provider<Map<?, ?>> {
    Map<Object, Object> properties = Collections.<Object, Object>singletonMap("foo", "bar");

    @Override
    public Map<?, ?> get() {
      return properties;
    }
  }
}
