package com.google.inject.internal;

import com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory;
import com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory.ReentrantCycleDetectingLock;

import junit.framework.TestCase;

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class CycleDetectingLockTest extends TestCase {

  static final long DEADLOCK_TIMEOUT_SECONDS = 1;

  /**
   * Verifies that graph of threads' dependencies is not static and is calculated in runtime using
   * information about specific locks.
   *
   * <pre>
   *   T1: Waits on S1
   *   T2: Locks B, sends S1, waits on S2
   *   T1: Locks A, start locking B, sends S2, waits on S3
   *   T2: Unlocks B, start locking A, sends S3, finishes locking A, unlocks A
   *   T1: Finishes locking B, unlocks B, unlocks A
   * </pre>
   *
   * <p>This should succeed, even though T1 was locked on T2 and T2 is locked on T1 when T2 locks
   * A. Incorrect implementation detects a cycle waiting on S3.
   */

  public void testSingletonThreadsRuntimeCircularDependency() throws Exception {
    final CyclicBarrier signal1 = new CyclicBarrier(2);
    final CyclicBarrier signal2 = new CyclicBarrier(2);
    final CyclicBarrier signal3 = new CyclicBarrier(2);
    final CycleDetectingLockFactory<String> lockFactory = new CycleDetectingLockFactory<String>();
    final CycleDetectingLock<String> lockA =
        new ReentrantCycleDetectingLock<String>(lockFactory, "A",
            new ReentrantLock() {
              @Override
              public void lock() {
                if (Thread.currentThread().getName().equals("T2")) {
                  try {
                    signal3.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                } else {
                  assertEquals("T1", Thread.currentThread().getName());
                }
                super.lock();
              }
            });
    final CycleDetectingLock<String> lockB =
        new ReentrantCycleDetectingLock<String>(lockFactory, "B",
            new ReentrantLock() {
              @Override
              public void lock() {
                if (Thread.currentThread().getName().equals("T1")) {
                  try {
                    signal2.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    signal3.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                } else {
                  assertEquals("T2", Thread.currentThread().getName());
                }
                super.lock();
              }
            });
    Future<Void> firstThreadResult =
        Executors.newSingleThreadExecutor()
            .submit(
                new Callable<Void>() {
                  @Override
                  public Void call() throws Exception {
                    Thread.currentThread().setName("T1");
                    signal1.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                    assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                    lockB.unlock();
                    lockA.unlock();
                    return null;
                  }
                });
    Future<Void> secondThreadResult =
        Executors.newSingleThreadExecutor()
            .submit(
                new Callable<Void>() {
                  @Override
                  public Void call() throws Exception {
                    Thread.currentThread().setName("T2");
                    assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                    signal1.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    signal2.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    lockB.unlock();
                    assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                    lockA.unlock();
                    return null;
                  }
                });

    firstThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
    secondThreadResult.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
  }

  /**
   * Verifies that factories do not deadlock each other.
   *
   * <pre>
   *   Thread A: lock a lock A (factory A)
   *   Thread B: lock a lock B (factory B)
   *   Thread A: lock a lock B (factory B)
   *   Thread B: lock a lock A (factory A)
   * </pre>
   *
   * <p>This should succeed even though from the point of view of each individual factory
   * there are no deadlocks to detect.
   */

  public void testCycleDetectingLockFactoriesDoNotDeadlock() throws Exception {
    final CycleDetectingLockFactory<String> factoryA = new CycleDetectingLockFactory<String>();
    final CycleDetectingLock<String> lockA = factoryA.create("A");
    final CycleDetectingLockFactory<String> factoryB = new CycleDetectingLockFactory<String>();
    final CycleDetectingLock<String> lockB = factoryB.create("B");
    final CyclicBarrier eachThreadAcquiredFirstLock = new CyclicBarrier(2);
    Future<Boolean> threadA =
        Executors.newSingleThreadExecutor()
            .submit(
                new Callable<Boolean>() {
                  @Override
                  public Boolean call() throws Exception {
                    Thread.currentThread().setName("A");
                    assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                    eachThreadAcquiredFirstLock.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    boolean isEmpty = lockB.lockOrDetectPotentialLocksCycle().isEmpty();
                    if (isEmpty) {
                      lockB.unlock();
                    }
                    lockA.unlock();
                    return isEmpty;
                  }
                });
    Future<Boolean> threadB =
        Executors.newSingleThreadExecutor()
            .submit(
                new Callable<Boolean>() {
                  @Override
                  public Boolean call() throws Exception {
                    Thread.currentThread().setName("B");
                    assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                    eachThreadAcquiredFirstLock.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    boolean isEmpty = lockA.lockOrDetectPotentialLocksCycle().isEmpty();
                    if (isEmpty) {
                      lockA.unlock();
                    }
                    lockB.unlock();
                    return isEmpty;
                  }
                });

    boolean deadlockADetected = threadA.get(DEADLOCK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);
    boolean deadlockBDetected = threadB.get(DEADLOCK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);

    assertTrue("Deadlock should get detected", deadlockADetected || deadlockBDetected);
    assertTrue("One deadlock should get detected", deadlockADetected != deadlockBDetected);
  }
}
