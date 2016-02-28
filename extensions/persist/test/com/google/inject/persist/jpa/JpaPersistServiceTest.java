/**
 * Copyright (C) 2014 Google, Inc.
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

import junit.framework.TestCase;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;

public class JpaPersistServiceTest extends TestCase {

  private static final String PERSISTENCE_UNIT_NAME = "test_persistence_unit_name";
  private static final Properties PERSISTENCE_PROPERTIES = new Properties();
  private static final String SYSTEM_PROPERTY_PROHIBIT_IMPLICIT_EM_INJECTION = "com.google.inject.persist.jpa.prohibit_implicit_em_injection";

  private final JpaPersistService sut = new JpaPersistService(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES);
  private final PersistenceProvider provider = mock(PersistenceProvider.class);
  private final EntityManagerFactory factory = mock(EntityManagerFactory.class);
  private final EntityManager entityManager = mock(EntityManager.class);

  @Override
  public void setUp() throws Exception {
    when(provider.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES)).thenReturn(factory);
    when(factory.createEntityManager()).thenReturn(entityManager);
  }

  public void test_givenErrorOnEntityManagerClose_whenEndIsCalled_thenEntityManagerIsRemoved() {
    sut.start(factory);
    sut.begin();

    // arrange an exception on sut.end(), which invokes entityManager.close()
    doThrow(SimulatedException.class).when(entityManager).close();
    try {
      sut.end();
      fail("Exception expected");
    }
    catch (SimulatedException expected) {
      assertThat(sut.isWorking(), is(false));
    }
  }

  public void test_givenImplicitEntityManagerInjectionProhibited_whenEntityManagerAboutToInject_thenExceptionIsThrown() {
    System.setProperty(SYSTEM_PROPERTY_PROHIBIT_IMPLICIT_EM_INJECTION, "true");
    sut.start(factory);

    try {
      sut.get();
      fail("Exception expected");
    }
    catch (IllegalStateException expected) {
      // nop
    }
  }

  public void test_givenImplicitEntityManagerInjectionNotProhibited_whenEntityManagerAboutToInject_thenNothingHappens() {
    sut.start(factory);

    assertNotNull(sut.get());
  }

  @Override
  public void tearDown() throws Exception {
    System.clearProperty(SYSTEM_PROPERTY_PROHIBIT_IMPLICIT_EM_INJECTION);
  }

  private class SimulatedException extends RuntimeException {
  }
}
