package com.google.inject.internal;

import com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory;

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
    CycleDetectingLockFactory<String> lockFactory = new CycleDetectingLockFactory<String>();
    final CycleDetectingLock<String> lockA =
        lockFactory.new ReentrantCycleDetectingLock("A", new ReentrantLock() {
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
        lockFactory.new ReentrantCycleDetectingLock("B", new ReentrantLock() {
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
    Future<Void> firstThreadResult = Executors.newSingleThreadExecutor().submit(
        new Callable<Void>() {
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
    Future<Void> secondThreadResult = Executors.newSingleThreadExecutor().submit(
        new Callable<Void>() {
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

    firstThreadResult.get();
    secondThreadResult.get();
  }
}
