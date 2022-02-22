/*
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceProviderResolver;
import javax.persistence.spi.PersistenceProviderResolverHolder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JpaPersistServiceTest {

  private static final String PERSISTENCE_UNIT_NAME = "test_persistence_unit_name";
  private static final Properties PERSISTENCE_PROPERTIES = new Properties();

  private final JpaPersistService service =
      new JpaPersistService(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES);

  private static PersistenceProviderResolver defaultResolver;
  private static final PersistenceProviderResolver resolver = mock(PersistenceProviderResolver.class);
  private final PersistenceProvider provider = mock(PersistenceProvider.class);
  private final EntityManagerFactory factory = mock(EntityManagerFactory.class);
  private final EntityManager entityManager = mock(EntityManager.class);

  private boolean factoryOpen = true;

  @BeforeClass
  public static void setupClass() {
    defaultResolver = PersistenceProviderResolverHolder.getPersistenceProviderResolver();
    PersistenceProviderResolverHolder.setPersistenceProviderResolver(resolver);
  }

  @Before
  public void setUp() throws Exception {
    when(resolver.getPersistenceProviders()).thenReturn(Collections.singletonList(provider));
    when(provider.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES)).thenReturn(factory);
    when(factory.createEntityManager()).thenReturn(entityManager);
    when(factory.isOpen()).thenAnswer(invocation -> factoryOpen);
    doAnswer(invocationOnMock -> { factoryOpen = false; return null; }).when(factory).close();
  }

  @Test
  public void givenNotYetStarted_whenCallToStop_thenError() {
    // when
    ThrowingRunnable action = service::stop;

    // then
    assertThrows("Persistence service never started.", IllegalStateException.class, action);
  }

  @Test
  public void givenAlreadyStopped_whenCallToStop_thenError() {
    // given
    service.start();
    service.stop();

    // when
    ThrowingRunnable action = service::stop;

    // then
    assertThrows("Persistence service never started.", IllegalStateException.class, action);
  }

  @Test
  public void givenFactoryAlreadyClosed_whenCallToStop_thenError() {
    // given
    factory.close(); // todo(krsi) perhaps a non-closeable EMF proxy should be returned from provider to prevent this?

    // when
    ThrowingRunnable action = service::stop;

    // then
    assertThrows("Persistence service was already shut down.", IllegalStateException.class, action);
  }

  @Test
  public void givenAlreadyStarted_whenAnotherCallToStart_thenError() {
    // given
    service.start();

    // when
    ThrowingRunnable action = service::start;

    // then
    assertThrows("Persistence service was already initialized.", IllegalStateException.class, action);
  }

  @Test
  public void givenStarted_whenFactoryRequested_thenProvidesNonNullFactory() {
    // given
    service.start();

    // when
    EntityManagerFactory actual = service.get();

    // then
    assertEquals(actual, factory);
  }

  @Test
  public void givenNotYetStarted_whenFactoryRequested_thenError() {
    // when
    ThrowingRunnable action = service::get;

    // then
    assertThrows("Persistence service not initialized.", IllegalStateException.class, action);
  }

  @Test
  public void givenStarted_whenStopped_thenFactoryIsClosed() {
    // given
    service.start();

    // when
    service.stop();

    // then
    assertFalse(factory.isOpen());
  }

  @AfterClass
  public static void tearDownClass() {
    PersistenceProviderResolverHolder.setPersistenceProviderResolver(defaultResolver);
  }
}
