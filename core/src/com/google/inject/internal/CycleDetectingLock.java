package com.google.inject.internal;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simplified version of {@link Lock} that is special due to how it handles deadlocks detection.
 *
 * <p>Is an inherent part of {@link SingletonScope}, moved into a upper level class due
 * to its size and complexity.
 *
 * @param <ID> Lock identification provided by the client, is returned unmodified to the client
 *        when lock cycle is detected to identify it. Only toString() needs to be implemented.
 *        Lock references this object internally,
 *        for the purposes of Garbage Collection you should not use heavy IDs.
 *        Lock is referenced by a lock factory as long as it's owned by a thread.
 *
 * @see SingletonScope
 * @see com.google.inject.internal.CycleDetectingLock.CycleDetectingLockFactory
 *
 * @author timofeyb (Timothy Basanov)
 */
interface CycleDetectingLock<ID> {

  /**
   * Takes a lock in a blocking fashion in case no potential deadlocks are detected.
   * If the lock was successfully owned, returns an empty map indicating no detected potential
   * deadlocks.
   *
   * Otherwise, a map indicating threads involved in a potential deadlock are returned.
   * Map is ordered by dependency cycle and lists locks for each thread that are part of
   * the loop in order. Returned map is created atomically.
   *
   * In case no cycle is detected performance is O(threads creating singletons),
   * in case cycle is detected performance is O(singleton locks).
   */
  ListMultimap<Long, ID> lockOrDetectPotentialLocksCycle();

  /**
   * Unlocks previously locked lock.
   */
  void unlock();

  /**
   * Wraps locks so they would never cause a deadlock. On each
   * {@link CycleDetectingLock#lockOrDetectPotentialLocksCycle} we check for dependency cycles
   * within locks created by the same factory. Either we detect a cycle and return it
   * or take it atomically.
   *
   * <p>Important to note that we do not prevent deadlocks in the client code. As an example:
   * Thread A takes lock L and creates singleton class CA depending on the singleton class CB.
   * Meanwhile thread B is creating class CB and is waiting on the lock L. Issue happens
   * due to client code creating interdependent classes and using locks, where
   * no guarantees on the creation order from Guice are provided.
   *
   * <p>Instances of these locks are not intended to be exposed outside of {@link SingletonScope}.
   */
  class CycleDetectingLockFactory<ID> {

    /**
     * Specifies lock that thread is currently waiting on to own it.
     * Used only for purposes of locks cycle detection.
     *
     * Key: thread id
     * Value: lock that is being waited on
     *
     * Element is added inside {@link #lockOrDetectPotentialLocksCycle()} before {@link Lock#lock}
     * is called. Element is removed inside {@link #lockOrDetectPotentialLocksCycle()} after
     * {@link Lock#lock} and synchronously with adding it to {@link #locksOwnedByThread}.
     *
     * Same lock can be added for several threads in case all of them are trying to
     * take it.
     *
     * Guarded by {@code this}.
     */
    private Map<Long, ReentrantCycleDetectingLock> lockThreadIsWaitingOn = Maps.newHashMap();

    /**
     * Lists locks that thread owns.
     * Used only to populate locks in a potential cycle when it is detected.
     *
     * Key: thread id
     * Value: stack of locks that were owned.
     *
     * Element is added inside {@link #lockOrDetectPotentialLocksCycle()} after {@link Lock#lock}
     * is called. Element is removed inside {@link #unlock()} synchronously with
     * {@link Lock#unlock()} call.
     *
     * Same lock can only be present several times for the same thread as locks are
     * reentrant. Lock can not be owned by several different threads as the same time.
     *
     * Guarded by {@code this}.
     */
    private final Multimap<Long, ReentrantCycleDetectingLock> locksOwnedByThread =
        LinkedHashMultimap.create();

    /**
     * Creates new lock within this factory context. We can guarantee that locks created by
     * the same factory would not deadlock.
     *
     * @param newLockId lock id that would be used to report lock cycles if detected
     */
    CycleDetectingLock<ID> create(ID newLockId) {
      return new ReentrantCycleDetectingLock(newLockId, new ReentrantLock());
    }

    /** The implementation for {@link CycleDetectingLock}. */
    class ReentrantCycleDetectingLock implements CycleDetectingLock<ID> {

      /** Underlying lock used for actual waiting when no potential deadlocks are detected. */
      private final Lock lockImplementation;
      /** User id for this lock. */
      private final ID userLockId;
      /**
       * Thread id for the thread that owned this lock. Nullable.
       * Guarded by {@code CycleDetectingLockFactory.this}.
       */
      private Long lockOwnerThreadId = null;
      /**
       * Number of times that thread owned this lock.
       * Guarded by {@code CycleDetectingLockFactory.this}.
       */
      private int lockReentranceCount = 0;

      ReentrantCycleDetectingLock(ID userLockId, Lock lockImplementation) {
        this.userLockId = Preconditions.checkNotNull(userLockId, "userLockId");
        this.lockImplementation = Preconditions.checkNotNull(
            lockImplementation, "lockImplementation");
      }

      @Override public ListMultimap<Long, ID> lockOrDetectPotentialLocksCycle() {
        final long currentThreadId = Thread.currentThread().getId();
        synchronized (CycleDetectingLockFactory.this) {
          checkState();
          ListMultimap<Long, ID> locksInCycle = detectPotentialLocksCycle();
          if (!locksInCycle.isEmpty()) {
            // potential deadlock is found, we don't try to take this lock
            return locksInCycle;
          }

          lockThreadIsWaitingOn.put(currentThreadId, this);
        }

        // this may be blocking, but we don't expect it to cause a deadlock
        lockImplementation.lock();

        synchronized (CycleDetectingLockFactory.this) {
          // current thread is no longer waiting on this lock
          lockThreadIsWaitingOn.remove(currentThreadId);
          checkState();

          // mark it as owned by us
          lockOwnerThreadId = currentThreadId;
          lockReentranceCount++;
          // add this lock to the list of locks owned by a current thread
          locksOwnedByThread.put(currentThreadId, this);
        }
        // no deadlock is found, locking successful
        return ImmutableListMultimap.of();
      }

      @Override public void unlock() {
        final long currentThreadId = Thread.currentThread().getId();
        synchronized (CycleDetectingLockFactory.this) {
          checkState();
          Preconditions.checkState(lockOwnerThreadId != null,
              "Thread is trying to unlock a lock that is not locked");
          Preconditions.checkState(lockOwnerThreadId == currentThreadId,
              "Thread is trying to unlock a lock owned by another thread");

          // releasing underlying lock
          lockImplementation.unlock();

          // be sure to release the lock synchronously with updating internal state
          lockReentranceCount--;
          if (lockReentranceCount == 0) {
            // we no longer own this lock
            lockOwnerThreadId = null;
            Preconditions.checkState(locksOwnedByThread.remove(currentThreadId, this),
                "Internal error: Can not find this lock in locks owned by a current thread");
            if (locksOwnedByThread.get(currentThreadId).isEmpty()) {
              // clearing memory
              locksOwnedByThread.removeAll(currentThreadId);
            }
          }
        }
      }

      /** Check consistency of an internal state. */
      void checkState() throws IllegalStateException {
        final long currentThreadId = Thread.currentThread().getId();
        Preconditions.checkState(!lockThreadIsWaitingOn.containsKey(currentThreadId),
            "Internal error: Thread should not be in a waiting thread on a lock now");
        if (lockOwnerThreadId != null) {
          // check state of a locked lock
          Preconditions.checkState(lockReentranceCount >= 0,
              "Internal error: Lock ownership and reentrance count internal states do not match");
          Preconditions.checkState(locksOwnedByThread.get(lockOwnerThreadId).contains(this),
              "Internal error: Set of locks owned by a current thread and lock "
                  + "ownership status do not match");
        } else {
          // check state of a non locked lock
          Preconditions.checkState(lockReentranceCount == 0,
              "Internal error: Reentrance count of a non locked lock is expect to be zero");
          Preconditions.checkState(!locksOwnedByThread.values().contains(this),
              "Internal error: Non locked lock should not be owned by any thread");
        }
      }

      /**
       * Algorithm to detect a potential lock cycle.
       *
       * For lock's thread owner check which lock is it trying to take.
       * Repeat recursively. When current thread is found a potential cycle is detected.
       *
       * @see CycleDetectingLock#lockOrDetectPotentialLocksCycle()
       */
      private ListMultimap<Long, ID> detectPotentialLocksCycle() {
        final long currentThreadId = Thread.currentThread().getId();
        if (lockOwnerThreadId == null || lockOwnerThreadId == currentThreadId) {
          // if nobody owns this lock, lock cycle is impossible
          // if a current thread owns this lock, we let Guice to handle it
          return ImmutableListMultimap.of();
        }

        ListMultimap<Long, ID> potentialLocksCycle = Multimaps.newListMultimap(
            new LinkedHashMap<Long, Collection<ID>>(),
            new Supplier<List<ID>>() {
              @Override
              public List<ID> get() {
                return Lists.newArrayList();
              }
            });
        // lock that is a part of a potential locks cycle, starts with current lock
        ReentrantCycleDetectingLock lockOwnerWaitingOn = this;
        // try to find a dependency path between lock's owner thread and a current thread
        while (lockOwnerWaitingOn != null && lockOwnerWaitingOn.lockOwnerThreadId != null) {
          Long threadOwnerThreadWaits = lockOwnerWaitingOn.lockOwnerThreadId;
          // in case locks cycle exists lock we're waiting for is part of it
          potentialLocksCycle.putAll(threadOwnerThreadWaits,
              getAllLockIdsAfter(threadOwnerThreadWaits, lockOwnerWaitingOn));

          if (threadOwnerThreadWaits == currentThreadId) {
            // owner thread depends on current thread, cycle detected
            return potentialLocksCycle;
          }
          // going for the next thread we wait on indirectly
          lockOwnerWaitingOn = lockThreadIsWaitingOn.get(threadOwnerThreadWaits);
        }
        // no dependency path from an owner thread to a current thread
        return ImmutableListMultimap.of();
      }

      /** Return locks owned by a thread after a lock specified, inclusive. */
      private List<ID> getAllLockIdsAfter(long threadId, ReentrantCycleDetectingLock lock) {
        List<ID> ids = Lists.newArrayList();
        boolean found = false;
        Collection<ReentrantCycleDetectingLock> ownedLocks = locksOwnedByThread.get(threadId);
        Preconditions.checkNotNull(ownedLocks,
            "Internal error: No locks were found taken by a thread");
        for (ReentrantCycleDetectingLock ownedLock : ownedLocks) {
          if (ownedLock == lock) {
            found = true;
          }
          if (found) {
            ids.add(ownedLock.userLockId);
          }
        }
        Preconditions.checkState(found, "Internal error: We can not find locks that "
            + "created a cycle that we detected");
        return ids;
      }

      @Override public String toString() {
        // copy is made to prevent a data race
        // no synchronization is used, potentially stale data, should be good enough
        Long localLockOwnerThreadId = this.lockOwnerThreadId;
        if (localLockOwnerThreadId != null) {
          return String.format("CycleDetectingLock[%s][locked by %s]",
              userLockId, localLockOwnerThreadId);
        } else {
          return String.format("CycleDetectingLock[%s][unlocked]", userLockId);
        }
      }
    }
  }
}
