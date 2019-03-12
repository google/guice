package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory;
import com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory.ReentrantCycleDetectingLock;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import junit.framework.TestCase;

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
   * <p>This should succeed, even though T1 was locked on T2 and T2 is locked on T1 when T2 locks A.
   * Incorrect implementation detects a cycle waiting on S3.
   */

  public void testSingletonThreadsRuntimeCircularDependency() throws Exception {
    final CyclicBarrier signal1 = new CyclicBarrier(2);
    final CyclicBarrier signal2 = new CyclicBarrier(2);
    final CyclicBarrier signal3 = new CyclicBarrier(2);
    final CycleDetectingLockFactory<String> lockFactory = new CycleDetectingLockFactory<>();
    final CycleDetectingLock<String> lockA =
        new ReentrantCycleDetectingLock<String>(
            lockFactory,
            "A",
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
        new ReentrantCycleDetectingLock<String>(
            lockFactory,
            "B",
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
                () -> {
                  Thread.currentThread().setName("T1");
                  signal1.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                  assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                  lockB.unlock();
                  lockA.unlock();
                  return null;
                });
    Future<Void> secondThreadResult =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  Thread.currentThread().setName("T2");
                  assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                  signal1.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  signal2.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  lockB.unlock();
                  assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                  lockA.unlock();
                  return null;
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
   * <p>This should succeed even though from the point of view of each individual factory there are
   * no deadlocks to detect.
   */

  public void testCycleDetectingLockFactoriesDoNotDeadlock() throws Exception {
    final CycleDetectingLockFactory<String> factoryA = new CycleDetectingLockFactory<>();
    final CycleDetectingLock<String> lockA = factoryA.create("A");
    final CycleDetectingLockFactory<String> factoryB = new CycleDetectingLockFactory<>();
    final CycleDetectingLock<String> lockB = factoryB.create("B");
    final CyclicBarrier eachThreadAcquiredFirstLock = new CyclicBarrier(2);
    Future<Boolean> threadA =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  Thread.currentThread().setName("A");
                  assertTrue(lockA.lockOrDetectPotentialLocksCycle().isEmpty());
                  eachThreadAcquiredFirstLock.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  boolean isEmpty = lockB.lockOrDetectPotentialLocksCycle().isEmpty();
                  if (isEmpty) {
                    lockB.unlock();
                  }
                  lockA.unlock();
                  return isEmpty;
                });
    Future<Boolean> threadB =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  Thread.currentThread().setName("B");
                  assertTrue(lockB.lockOrDetectPotentialLocksCycle().isEmpty());
                  eachThreadAcquiredFirstLock.await(DEADLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                  boolean isEmpty = lockA.lockOrDetectPotentialLocksCycle().isEmpty();
                  if (isEmpty) {
                    lockA.unlock();
                  }
                  lockB.unlock();
                  return isEmpty;
                });

    boolean deadlockADetected = threadA.get(DEADLOCK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);
    boolean deadlockBDetected = threadB.get(DEADLOCK_TIMEOUT_SECONDS * 2, TimeUnit.SECONDS);

    assertTrue("Deadlock should get detected", deadlockADetected || deadlockBDetected);
    assertTrue("One deadlock should get detected", deadlockADetected != deadlockBDetected);
  }

  /**
   * Verifies that factories deadlocks report the correct cycles.
   *
   * <pre>
   *   Thread 1: takes locks a, b
   *   Thread 2: takes locks b, c
   *   Thread 3: takes locks c, a
   * </pre>
   *
   * <p>In order to ensure a deadlock, each thread will wait on a barrier right after grabbing the
   * first lock.
   */

  public void testCycleReporting() throws Exception {
    final CycleDetectingLockFactory<String> factory = new CycleDetectingLockFactory<>();
    final CycleDetectingLock<String> lockA = factory.create("a");
    final CycleDetectingLock<String> lockB = factory.create("b");
    final CycleDetectingLock<String> lockC = factory.create("c");
    final CyclicBarrier barrier = new CyclicBarrier(3);
    ImmutableList<Future<ListMultimap<Thread, String>>> futures =
        ImmutableList.of(
            grabLocksInThread(lockA, lockB, barrier),
            grabLocksInThread(lockB, lockC, barrier),
            grabLocksInThread(lockC, lockA, barrier));

    // At least one of the threads will report a lock cycle, it is possible that they all will, but
    // there is no guarantee, so we just scan for the first thread that reported a cycle
    ListMultimap<Thread, String> cycle = null;
    for (Future<ListMultimap<Thread, String>> future : futures) {
      ListMultimap<Thread, String> value =
          future.get(DEADLOCK_TIMEOUT_SECONDS * 3, TimeUnit.SECONDS);
      if (!value.isEmpty()) {
        cycle = value;
        break;
      }
    }
    // We don't really care about the keys in the multimap, but we want to make sure that all locks
    // were reported in the right order.
    assertEquals(6, cycle.size());
    Collection<List<String>> edges = Multimaps.asMap(cycle).values();
    assertTrue(edges.contains(ImmutableList.of("a", "b")));
    assertTrue(edges.contains(ImmutableList.of("b", "c")));
    assertTrue(edges.contains(ImmutableList.of("c", "a")));
  }

  private static <T> Future<ListMultimap<Thread, T>> grabLocksInThread(
      final CycleDetectingLock<T> lock1,
      final CycleDetectingLock<T> lock2,
      final CyclicBarrier barrier) {
    FutureTask<ListMultimap<Thread, T>> future =
        new FutureTask<ListMultimap<Thread, T>>(
            () -> {
              assertTrue(lock1.lockOrDetectPotentialLocksCycle().isEmpty());
              barrier.await();
              ListMultimap<Thread, T> cycle = lock2.lockOrDetectPotentialLocksCycle();
              if (cycle == null) {
                lock2.unlock();
              }
              lock1.unlock();
              return cycle;
            });
    Thread thread = new Thread(future);
    thread.start();
    return future;
  }
}
