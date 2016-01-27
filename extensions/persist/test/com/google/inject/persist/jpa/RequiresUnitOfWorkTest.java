package com.google.inject.persist.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.RequiresUnitOfWork;
import com.google.inject.persist.UnitOfWork;

import junit.framework.TestCase;

public class RequiresUnitOfWorkTest extends TestCase {
  private Injector injector;

  @Override
  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    // startup persistence and end the unit of work, so that no one is running
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    persistService.start();
    persistService.end();
  }

  @Override
  public void tearDown() {
    injector.getInstance(EntityManagerFactory.class).close();
  }

  public void testRequiresWithNoUnitOfWork() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    DataAccessObject dataObject = injector.getInstance(DataAccessObject.class);

    // Run operation with no exception
    dataObject.runOperationInUnitOfWork();

    // Unit of work has been closed
    assertFalse(persistService.isWorking());
  }

  public void testRequiresWithUnitOfWork() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);
    DataAccessObject dataObject = injector.getInstance(DataAccessObject.class);

    // Run operation with no exception
    unitOfWork.begin();

    dataObject.runOperationInUnitOfWork();

    // Unit of work has not been closed
    assertTrue(persistService.isWorking());
    unitOfWork.end();
    // end explicitly closes unit of work
    assertFalse(persistService.isWorking());
  }

  public void testRequiresWithNestedUnitsOfWork() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    NestDataObject dataObject = injector.getInstance(NestDataObject.class);

    // Run operation with no exception
    dataObject.runOperationInUnitOfWork();

    // Unit of work has not been closed
    assertFalse(persistService.isWorking());
  }


  public void testUnitOfWorkWithEntityManagers() {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);

    unitOfWork.begin();
    EntityManager em1 = injector.getInstance(EntityManager.class);
    unitOfWork.end();

    EntityManager em2 = injector.getInstance(EntityManager.class);

    unitOfWork.begin();
    EntityManager em3 = injector.getInstance(EntityManager.class);
    unitOfWork.end();


    assertNotSame("Em was kept open across unitOfWork", em1, em2);
    assertEquals("Em was not kept open across calls", em2, em3);

    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    assertFalse(persistService.isWorking());
  }


  public void testUnitOfWorkStartingAndEnding() {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);
    unitOfWork.begin();
    unitOfWork.begin(); // do not penalize users multiple calls to begin
    unitOfWork.end();
    unitOfWork.end();

    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    assertFalse(persistService.isWorking());
  }


  public static class DataAccessObject {
    @Inject
    Provider<EntityManager> emProvider;

    @RequiresUnitOfWork
    public void runOperationInUnitOfWork() {
      emProvider.get().createQuery("from JpaTestEntity", JpaTestEntity.class).setMaxResults(1)
          .getResultList();
    }
  }

  public static class NestDataObject {
    @Inject
    Provider<EntityManager> emProvider;
    @Inject
    DataAccessObject dataObject;

    @RequiresUnitOfWork
    public void runOperationInUnitOfWork() {
      emProvider.get().createQuery("from JpaTestEntity", JpaTestEntity.class).setMaxResults(1)
          .getResultList();
      dataObject.runOperationInUnitOfWork();
      emProvider.get().createQuery("from JpaTestEntity", JpaTestEntity.class).setMaxResults(1)
          .getResultList();
    }
  }
}
