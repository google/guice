package com.google.inject.persist.jpa;

import java.util.Map;
import jakarta.persistence.Cache;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Query;
import jakarta.persistence.SynchronizationType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.metamodel.Metamodel;

/** Proxy EntityManager that adds tracking capabilities, keeping a count of created objects. */
class TrackedEntityManagerFactory implements EntityManagerFactory {

  private final EntityManagerFactory delegate;
  private int entityManagerCreatedCount = 0;

  public TrackedEntityManagerFactory(EntityManagerFactory delegate) {
    this.delegate = delegate;
  }

  public boolean hasCreatedSomeEntityManager() {
    return (entityManagerCreatedCount > 0);
  }

  public int getEntityManagerCreatedCount() {
    return entityManagerCreatedCount;
  }

  @Override
  public boolean isOpen() {
    return delegate.isOpen();
  }

  @Override
  public Map<String, Object> getProperties() {
    return delegate.getProperties();
  }

  @Override
  public PersistenceUnitUtil getPersistenceUnitUtil() {
    return delegate.getPersistenceUnitUtil();
  }

  @Override
  public Metamodel getMetamodel() {
    return delegate.getMetamodel();
  }

  @Override
  public CriteriaBuilder getCriteriaBuilder() {
    return delegate.getCriteriaBuilder();
  }

  @Override
  public Cache getCache() {
    return delegate.getCache();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public EntityManager createEntityManager(Map arg0) {
    entityManagerCreatedCount++;
    return delegate.createEntityManager(arg0);
  }

  @Override
  public EntityManager createEntityManager() {
    entityManagerCreatedCount++;
    return delegate.createEntityManager();
  }

  @Override
  public EntityManager createEntityManager(SynchronizationType synchronizationType) {
    entityManagerCreatedCount++;
    return delegate.createEntityManager(synchronizationType);
  }

  @SuppressWarnings("rawtypes")
  @Override
  public EntityManager createEntityManager(SynchronizationType synchronizationType, Map map) {
    entityManagerCreatedCount++;
    return delegate.createEntityManager(synchronizationType, map);
  }

  @Override
  public void close() {
    delegate.close();
  }

  @Override
  public void addNamedQuery(String name, Query query) {
    delegate.addNamedQuery(name, query);
  }

  @Override
  public <T> T unwrap(Class<T> cls) {
    return delegate.unwrap(cls);
  }

  @Override
  public <T> void addNamedEntityGraph(String graphName, EntityGraph<T> entityGraph) {
    delegate.addNamedEntityGraph(graphName, entityGraph);
  }
}
