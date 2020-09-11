package com.google.inject.persist.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import junit.framework.TestCase;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.persist.RequiresUnitOfWork;

public class RequiresUnitOfWorkTest extends TestCase {
  private Injector injector;

  public void setUp() {
    injector = Guice.createInjector(new JpaPersistModule("testUnit"));

    //startup persistence and end the unit of work, so that no one is running
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    persistService.start();
    persistService.end();
  }

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
    DataAccessObject dataObject = injector.getInstance(DataAccessObject.class);

    // Run operation with no exception
    persistService.begin();
    dataObject.runOperationInUnitOfWork();
    
    // Unit of work has not been closed
    assertTrue(persistService.isWorking());
    persistService.end();
  }

  public void testRequiresWithNestedUnitsOfWork() {
    JpaPersistService persistService = injector.getInstance(JpaPersistService.class);
    NestDataObject dataObject = injector.getInstance(NestDataObject.class);

    // Run operation with no exception
    dataObject.runOperationInUnitOfWork();
    
    // Unit of work has not been closed
    assertFalse(persistService.isWorking());
  }


  public static class DataAccessObject {
    @Inject Provider<EntityManager> emProvider;
    
    @RequiresUnitOfWork
    public void runOperationInUnitOfWork() {
      emProvider.get()
          .createQuery("from JpaTestEntity", JpaTestEntity.class)
          .setMaxResults(1).getResultList();
    }
  }

  public static class NestDataObject {
    @Inject Provider<EntityManager> emProvider;
    @Inject DataAccessObject dataObject;
    
    @RequiresUnitOfWork
    public void runOperationInUnitOfWork() {
      emProvider.get()
          .createQuery("from JpaTestEntity", JpaTestEntity.class)
          .setMaxResults(1).getResultList();
      dataObject.runOperationInUnitOfWork();
      emProvider.get()
          .createQuery("from JpaTestEntity", JpaTestEntity.class)
          .setMaxResults(1).getResultList();
    }
  }
}
