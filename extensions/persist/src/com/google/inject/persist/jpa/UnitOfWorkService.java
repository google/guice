package com.google.inject.persist.jpa;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.UnitOfWork;

/**
 * Implementation of the UnitOfWork. It starts the unit of work if there isn't any in the current
 * thread, and ends it if the unit of work was started by this instance.
 * 
 * @author Jarno Jantunen (jarno.jantunen@gmail.com)
 */
@Singleton
public class UnitOfWorkService implements UnitOfWork {

  private JpaPersistService emProvider = null;

  /** Tracks if the unit of work was begun implicitly by this handler. */
  private final ThreadLocal<Integer> didWeStartWork = new ThreadLocal<Integer>();

  @Inject
  public UnitOfWorkService(JpaPersistService emProvider) {
    this.emProvider = emProvider;
  }

  @Override
  public void begin() {
    doBegin(false);
  }

  @Override
  public void end() {
    if (getLevel() > 1) {
      System.out.println("Warn: Nested UnitOfWorks exists, when calling end.");
    }
    didWeStartWork.remove();
    emProvider.end();
  }


  void requireUnitOfWork() {
    doBegin(true);
  }

  void endRequireUnitOfWork() {
    Integer level = getLevel();
    if (level <= 1) {
      didWeStartWork.remove();
      emProvider.end();
    } else {
      didWeStartWork.set(level - 1);
    }
  }

  private void doBegin(boolean nestedCall) {
    int level = getLevel();
    if (level == 0) {
      emProvider.beginNew();
      didWeStartWork.set(1);
    } else {
      didWeStartWork.set(level + 1);
      if (!nestedCall) {
        System.out.println("Warn: Nested UnitOfWorks exists, when calling begin.");
      }
    }
  }

  private int getLevel() {
    Integer level = didWeStartWork.get();
    if ((level != null) && (level.intValue() > 0)) {
      return level;
    }
    return 0;
  }

  // private boolean isWorking() {
  // return getLevel() == 0 ? false : true;
  // }
}
