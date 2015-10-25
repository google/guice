package com.google.inject.persist.jpa;

import javax.persistence.EntityManager;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

/**
 * Utility class able to handle easily the start and end of units of work
 * in a nested context. It starts the unit of work if there isn't any in
 * the current thread, and ends it if the unit of work was started by this
 * instance.
 */
public class UnitOfWorkHandler {
  private JpaPersistService emProvider = null;
  private UnitOfWork unitOfWork = null;

  /** Tracks if the unit of work was begun implicitly by this handler. */
  private final ThreadLocal<Integer> didWeStartWork = new ThreadLocal<Integer>();

  @Inject
  public UnitOfWorkHandler(JpaPersistService emProvider, UnitOfWork unitOfWork) {
    this.emProvider = emProvider;
    this.unitOfWork = unitOfWork;
  }

  public void requireUnitOfWork() {
    if (!emProvider.isWorking()) {
      unitOfWork.begin();
      didWeStartWork.set(1);
    } else {
      Integer level = didWeStartWork.get();
      if ((level != null) && (level.intValue() >= 0)) {
        didWeStartWork.set(level + 1);
      }
    }
  }
  
  public void endRequireUnitOfWork() {
    Integer level = didWeStartWork.get();
    if ((level != null) && (level.intValue() > 0)) {
      if (level.intValue() == 1) {
        didWeStartWork.remove();
        unitOfWork.end();
      } else {
        didWeStartWork.set(level - 1);
      }
    }
  }
  
  public EntityManager getEntityManager() {
    return emProvider.get();
  }
}
