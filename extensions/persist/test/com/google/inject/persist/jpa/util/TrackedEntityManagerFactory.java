package com.google.inject.persist.jpa.util;

import java.util.Map;

import javax.persistence.Cache;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;

/**
 * Proxy EntityManager that adds tracking capabilities, keeping a count of
 * created objects.
 */
public class TrackedEntityManagerFactory implements EntityManagerFactory {
  
  private EntityManagerFactory originalEMF;
  private int entityManagerCreatedCount = 0;

  public TrackedEntityManagerFactory(EntityManagerFactory originalEMF) {
    this.originalEMF = originalEMF;
  }
  
  public boolean hasCreatedSomeEntityManager() {
    return (entityManagerCreatedCount > 0);
  }
  
  public int getEntityManagerCreatedCount() {
    return entityManagerCreatedCount;
  }

  @Override
  public boolean isOpen() {
    return originalEMF.isOpen();
  }
  
  @Override
  public Map<String, Object> getProperties() {
    return originalEMF.getProperties();
  }
  
  @Override
  public PersistenceUnitUtil getPersistenceUnitUtil() {
    return originalEMF.getPersistenceUnitUtil();
  }
  
  @Override
  public Metamodel getMetamodel() {
    return originalEMF.getMetamodel();
  }
  
  @Override
  public CriteriaBuilder getCriteriaBuilder() {
    return originalEMF.getCriteriaBuilder();
  }
  
  @Override
  public Cache getCache() {
    return originalEMF.getCache();
  }
  
  @Override
  public EntityManager createEntityManager(Map arg0) {
    entityManagerCreatedCount++;
    return originalEMF.createEntityManager(arg0);
  }
  
  @Override
  public EntityManager createEntityManager() {
    entityManagerCreatedCount++;
    return originalEMF.createEntityManager();
  }
  
  @Override
  public void close() {
    originalEMF.close();
  }
}
