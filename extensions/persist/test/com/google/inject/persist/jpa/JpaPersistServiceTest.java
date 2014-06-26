package com.google.inject.persist.jpa;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.spi.PersistenceProvider;

import org.junit.Before;
import org.junit.Test;

public class JpaPersistServiceTest {

  private static final String PERSISTENCE_UNIT_NAME = "test_persistence_unit_name";
  private static final Properties PERSISTENCE_PROPERTIES = new Properties();

  private final JpaPersistService sut = new JpaPersistService(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES);
  private final PersistenceProvider provider = mock(PersistenceProvider.class);
  private final EntityManagerFactory factory = mock(EntityManagerFactory.class);
  private final EntityManager entityManager = mock(EntityManager.class);

  @Before
  public void setUp() throws Exception {
    when(provider.createEntityManagerFactory(PERSISTENCE_UNIT_NAME, PERSISTENCE_PROPERTIES)).thenReturn(factory);
    when(factory.createEntityManager()).thenReturn(entityManager);
  }

  @Test
  public void givenErrorOnEntityManagerClose_whenEndIsCalled_thenEntityManagerIsRemoved() {
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

  private class SimulatedException extends RuntimeException {
  }
}
