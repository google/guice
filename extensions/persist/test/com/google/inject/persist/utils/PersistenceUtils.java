package com.google.inject.persist.utils;

import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import org.hibernate.SessionFactory;

public class PersistenceUtils {

  public static long successfulTransactionCount(EntityManagerFactory emf) {
    return ((SessionFactory) emf).getStatistics().getSuccessfulTransactionCount();
  }

  public static long successfulTransactionCount(Injector injector) {
    return successfulTransactionCount(injector.getInstance(EntityManagerFactory.class));
  }

  public static long sessionOpenCount(EntityManagerFactory emf) {
    return ((SessionFactory) emf).getStatistics().getSessionOpenCount();
  }

  public static long sessionOpenCount(Injector injector) {
    return sessionOpenCount(injector.getInstance(EntityManagerFactory.class));
  }

  public static void withinTransaction(Injector injector, Consumer<EntityManager> action) {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);
    EntityManager entityManager = null;
    try {
      unitOfWork.begin();
      entityManager = injector.getInstance(EntityManager.class);
      entityManager.getTransaction().begin();
      action.accept(entityManager);
    } finally {
      if (entityManager != null) {
        EntityTransaction transaction = entityManager.getTransaction();
        if (transaction.isActive()) {
          if (transaction.getRollbackOnly()) {
            transaction.rollback();
          } else {
            transaction.commit();
          }
        }
      }
      unitOfWork.end();
    }
  }

  public static void withinUnitOfWork(Injector injector, Consumer<EntityManager> action) {
    UnitOfWork unitOfWork = injector.getInstance(UnitOfWork.class);
    EntityManager entityManager;
    try {
      unitOfWork.begin();
      entityManager = injector.getInstance(EntityManager.class);
      action.accept(entityManager);
    } finally {
      unitOfWork.end();
    }
  }

  public static Queries query(PersistenceUnitResource persistenceUnit) {
    return query(persistenceUnit.getSession());
  }

  public static Queries query(EntityManager session) {
    return new Queries() {
      @Override
      public <T> List<T> forList(Class<T> resultType, String ql, Map<String, Object> params) {
        session.getTransaction().begin();
        try {
          TypedQuery<T> query = session.createQuery(ql, resultType);
          params.forEach(query::setParameter);
          return query.getResultList();
        } finally {
          session.getTransaction().commit();
        }
      }
    };
  }


  public interface Queries {
    <T> List<T> forList(Class<T> resultType, String ql, Map<String, Object> params);
  }
}
