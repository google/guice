/*
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.errorprone.annotations.Keep;
import com.google.inject.spi.Dependency;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import javax.annotation.Nullable;

/**
 * Internal context. Used to coordinate injections and support circular dependencies.
 *
 * <p>This is only used within thie package, but needs to be public so it can be used in generated
 * method signatures for classes that might be loaded in child classloaders.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class InternalContext implements AutoCloseable {

  /** A factory for generating unique circular factory ids. */
  static final class CircularFactoryIdFactory {
    private static final VarHandle ID_HANDLE;

    static {
      try {
        ID_HANDLE =
            MethodHandles.lookup().findVarHandle(CircularFactoryIdFactory.class, "id", int.class);
      } catch (ReflectiveOperationException e) {
        throw new LinkageError("Failed to find id field", e);
      }
    }

    static final int INVALID_ID = 0;

    // The first factory id is 1 so that we never produce a circular factory id of 0.
    // The tables below use 0 to represent an empty slot which is convenient since arrays are always
    // zero initialized.
    @Keep private int id = 1;

    /** Returns the next circular factory id. */
    int next() {
      // Using an increment of 1 will produce an overflow after 2^32 - 1 calls to next() which is
      // maximal for this type, however creates a simple way to reason about hash collisions.
      // First we should note that the tables below use robin hood hashing and integer keys so
      // collisions are relatively cheap.
      // Our tables also use a trivial power of 2 size open addressed hash table, so sequentially
      // allocated factories will never collide (since they differ in the lowest bits), instead we
      // only expect collisions when the factories are some power of 2 > 4 apart. The order of
      // factory allocation is difficult to reason about given the complexity of injector
      // construction but we should generally expect that factories with dependency relationships
      // (implying that they will both be constructing at the same time), will be allocated close to
      // each other and thus are unlikely to collide.
      while (true) {
        // NOTE: we could use `getAndIncrement` but that would only allow us to throw an exception
        // on the first time we overflow, this approach ensures that overflow completely breaks the
        // factory.
        // Use opaque reads since we don't expect much contention and don't need full barriers
        // for correctness.
        int next = (int) ID_HANDLE.getOpaque(this);
        if (next == 0) {
          // If this ever happens we could switch to using `long` values, but realistically we would
          // likely run out of memory somewhere else before that.
          throw new IllegalStateException("Circular factory id overflow");
        }
        if (ID_HANDLE.weakCompareAndSetPlain(this, next, next + 1)) {
          return next;
        }
      }
    }
  }

  static InternalContext create(boolean disableCircularProxies, Object[] toClear) {
    return disableCircularProxies
        ? new WithoutProxySupport(toClear)
        : new WithProxySupport(toClear);
  }

  // enough space for 12 values before we need to resize the table
  private static final int INITIAL_TABLE_SIZE = 16;

  /** Keeps track of the type that is currently being requested for injection. */
  private Dependency<?> dependency;

  /**
   * The number of times {@link #enter()} has been called + 1 for initial construction. This value
   * is decremented when {@link #close()} is called.
   */
  private int enterCount;

  /**
   * A single element array to clear when the {@link #enterCount} hits {@code 0}.
   *
   * <p>This is the value stored in the {@code InjectorImpl.localContext} thread local.
   */
  private final Object[] toClear;

  protected InternalContext(Object[] toClear) {
    this.toClear = toClear;
    this.enterCount = 1;
  }

  /** Should only be called by InjectorImpl.enterContext(). */
  void enter() {
    enterCount++;
  }

  /** Should be called any any method that received an instance via InjectorImpl.enterContext(). */
  @Override
  public void close() {
    int newCount = --enterCount;
    if (newCount < 0) {
      throw new IllegalStateException("Called close() too many times");
    }
    if (newCount == 0) {
      toClear[0] = null;
    }
  }

  /**
   * Returns true if circular proxies are enabled.
   *
   * <p>This is only used by {@link SingletonScope} to determine if it should throw an exception or
   * return a proxy when a circular dependency is detected. All other users get this behavior
   * implicitly via the lifecycle methods in this class.
   */
  abstract boolean areCircularProxiesEnabled();

  /**
   * Starts construction in the factory to satisfy the given dependency.
   *
   * <p>Following this call the user should call {@link #finishConstruction} or {@link
   * #finishConstructionAndSetReference} to complete the construction.
   *
   * @return {@code null} if construction was started, or a proxy if a circular dependency was
   *     detected and created
   * @throws InternalProvisionException if a circular dependency was detected and circular proxies
   *     are disabled
   */
  @Nullable
  abstract <T> T tryStartConstruction(int circularFactoryId, Dependency<T> forDependency)
      throws InternalProvisionException;

  /**
   * Marks construction complete for the given {@code circularFactoryId}, with the given result.
   *
   * <p>The {@code result} is used to satisfy any circular proxies that may have been created.
   *
   * <p>This is a terminal operation, the construction is considered complete and no state is
   * retained.
   */
  abstract void finishConstruction(int circularFactoryId, @Nullable Object result);

  /**
   * Finishes construction and sets the current reference to the given result, this is used for
   * constructor injection where we proceed in 2 phases, constructor and then members.
   *
   * <p>The {@code result} is used to satisfy any circular proxies that may have been created and
   * also any cycles detected during members injection.
   *
   * <p>This is a non-terminal operation, Following this call the user should call {@link
   * #clearCurrentReference} to clear the current reference and finalize the construction.
   */
  abstract void finishConstructionAndSetReference(int circularFactoryId, Object result);

  /**
   * Completes construction and clears the current reference for constructor injection.
   *
   * <p>This is a terminal operation, the construction is considered complete and no state is
   * retained.
   */
  abstract void clearCurrentReference(int circularFactoryId);

  /**
   * Returns the current dependency being injected.
   *
   * <p>This is only used by scope implementations to detect circular dependencies and to enforce
   * nullability
   */
  Dependency<?> getDependency() {
    return dependency;
  }

  /**
   * Used to set the current dependency.
   *
   * <p>The currentDependency field is only used by InternalFactoryToProviderAdapter to propagate
   * information to singleton scope. See comments in that class about alternatives.
   */
  void setDependency(Dependency<?> dependency) {
    this.dependency = dependency;
  }

  @VisibleForTesting
  static final class WithoutProxySupport extends InternalContext {
    // Power of 2 size open addressed hash set using robin hood hashing, storing the circular
    // factory ids.
    private int[] table = new int[INITIAL_TABLE_SIZE];
    // The number of elements in the table, used to determine when to resize the table.
    private int tableSize;

    WithoutProxySupport(Object[] toClear) {
      super(toClear);
    }

    @Override
    boolean areCircularProxiesEnabled() {
      return false;
    }

    @Override
    <T> T tryStartConstruction(int circularFactoryId, Dependency<T> forDependency)
        throws InternalProvisionException {
      insert(circularFactoryId, forDependency);
      return null;
    }

    @Override
    void finishConstruction(int circularFactoryId, @Nullable Object result) {
      remove(circularFactoryId);
    }

    @Override
    void finishConstructionAndSetReference(int circularFactoryId, Object result) {
      // Do nothing.  Leave the factory in the provisioning state since when circular proxies are
      // disabled, we don't need to track the current reference.
      // We could assert that the factory is in the provisioning state, but that would require
      // checking the value of the table entry, which is expensive.
    }

    @Override
    void clearCurrentReference(int circularFactoryId) {
      remove(circularFactoryId);
    }

    /**
     * Returns the index of where the given key should be inserted, or the index where it already is
     * if present. To tell the difference, check the value in the construction contexts array.
     */
    void insert(int key, Dependency<?> forDependency) throws InternalProvisionException {
      if (key == 0) {
        throw new IllegalArgumentException("Invalid key: " + key);
      }
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          table[index] = key;
          int newSize = ++this.tableSize;
          if (newSize + (newSize >> 1) > len) {
            expandTable();
          }
          return;
        }
        if (c == key) {
          throw InternalProvisionException.circularDependenciesDisabled(
              forDependency.getKey().getTypeLiteral().getRawType());
        }
        // See how far is the current key from its ideal slot in the table.
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          // Steal from the rich!
          table[index] = key;
          // Re-insert the key since we have stolen its slot.
          insert(c, null);
          return;
        }
        // Move to the next slot.
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    // A testing util to get the index of a key.
    @VisibleForTesting
    int get(int key) {
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          return -1;
        }
        if (c == key) {
          return index;
        }
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          return -1;
        }
        // Move to the next slot.
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    /**
     * Removes the given key from the table.
     *
     * <p>Returns the value associated with the key, throws if the key was not found.
     */
    void remove(int key) {
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          throw new IllegalStateException("table corrupted, key" + key + " not found");
        }
        if (c == key) {
          table[index] = 0;
          // NOTE: We don't currently resize when removing items.
          this.tableSize--;
          // At this point we have potentially violated the robin hood invariant so we need to shift
          // following items back.
          // We shift each following item back by 1 unless it is empty or it is has a distance of
          // zero.
          int nextIndex = nextIndex(index, len);
          int nextC = table[nextIndex];
          while (true) {
            if (nextC == 0) {
              break; // empty slot
            }
            if (hash(nextC, len) == nextIndex) {
              break; // this item is in its ideal slot, so we cannot shift it
            }
            table[index] = nextC;
            table[nextIndex] = 0;
            index = nextIndex;
            nextIndex = nextIndex(nextIndex, len);
            nextC = table[nextIndex];
          }
          return;
        }
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          // This means that we have transitioned into another bucket chain.
          throw new IllegalStateException("table corrupted, key " + key + " not found");
        }
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    private void expandTable() throws InternalProvisionException {
      int[] oldTable = this.table;
      int oldLength = oldTable.length;
      int newLength = oldLength * 2;
      if (oldLength >= newLength) { // overflow
        throw new IllegalArgumentException("Table size too large to expand: " + oldLength);
      }
      this.table = new int[newLength];
      this.tableSize = 0;
      for (int k : oldTable) {
        if (k != 0) {
          insert(k, null);
        }
      }
    }
  }

  @VisibleForTesting
  static final class WithProxySupport extends InternalContext {

    // Open addressed hash table, power of 2 size using robin hood hashing.
    // Keys are the circular factory ids
    // Values are in the constructionContexts table stored in parallel
    private int[] table = new int[INITIAL_TABLE_SIZE];

    // The list of construction contexts, parallel to table
    // If the value is `null` but there is a key in the table then we are in the 'constructing'
    // state
    // If the value is not `null` then either it is a `ProxyDelegates` instance or a 'current
    // reference' which might be any other type.
    private Object[] constructionContexts = new Object[INITIAL_TABLE_SIZE];

    // The number of elements in the table, used to determine when to resize the table.
    private int tableSize;

    WithProxySupport(Object[] toClear) {
      super(toClear);
    }

    @Override
    boolean areCircularProxiesEnabled() {
      return true;
    }

    @Override
    <T> T tryStartConstruction(int circularFactoryId, Dependency<T> forDependency)
        throws InternalProvisionException {
      @SuppressWarnings("unchecked")
      T t = (T) insert(circularFactoryId, forDependency, null);
      return t;
    }

    @Override
    void finishConstruction(int circularFactoryId, @Nullable Object result) {
      Object context = remove(circularFactoryId);
      setProxyDelegates(context, result);
    }

    @Override
    void finishConstructionAndSetReference(int circularFactoryId, Object result) {
      int index = get(circularFactoryId);
      Object[] constructionContexts = this.constructionContexts;
      Object context = constructionContexts[index];
      setProxyDelegates(context, result);
      constructionContexts[index] = result;
    }

    @Override
    void clearCurrentReference(int circularFactoryId) {
      remove(circularFactoryId);
    }

    private void setProxyDelegates(Object context, Object result) {
      if (context instanceof DelegatingInvocationHandler) {
        ((DelegatingInvocationHandler) context).setDelegate(result);
      }
    }

    /**
     * Returns the index of where the given key should be inserted, or the index where it already is
     * if present. To tell the difference, check the value in the construction contexts array.
     */
    @Nullable
    Object insert(int key, @Nullable Dependency<?> forDependency, @Nullable Object existing)
        throws InternalProvisionException {
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          table[index] = key;
          this.constructionContexts[index] = existing;
          int newSize = ++this.tableSize;
          if (newSize + (newSize >> 1) > len) {
            expandTable();
          }
          return null;
        }
        if (c == key) {
          Object current = this.constructionContexts[index];
          // This must be a value set by setCurrentReference, just return it.
          if (current != null && !(current instanceof DelegatingInvocationHandler)) {
            return current;
          }
          // now we need to check if we can proxy the class
          Class<?> raw = forDependency.getKey().getTypeLiteral().getRawType();
          if (!raw.isInterface()) {
            throw InternalProvisionException.cannotProxyClass(raw);
          }
          if (current == null) {
            current = new DelegatingInvocationHandler();
            this.constructionContexts[index] = current;
          }
          return BytecodeGen.newCircularProxy(raw, (DelegatingInvocationHandler) current);
        }
        // See how far is the current key from its ideal slot in the table.
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          // Steal from the rich!
          table[index] = key;
          Object[] constructionContexts = this.constructionContexts;
          Object prev = constructionContexts[index];
          constructionContexts[index] = existing;
          // Re-insert the key since we have stolen its slot.
          insert(c, null, prev);
          return null;
        }
        // Move to the next slot.
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    /** Returns the index of where the given key is stored, or -1 if not found. */
    @VisibleForTesting
    int get(int key) {
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          return -1;
        }
        if (c == key) {
          return index;
        }
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          // This means that we have transitioned into another bucket chain.
          return -1;
        }
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    Object remove(int key) {
      int[] table = this.table;
      int len = table.length;
      int index = hash(key, len);
      int c = table[index];
      int distance = 0;
      while (true) {
        if (c == 0) {
          throw new IllegalStateException("table corrupted, key not found");
        }
        if (c == key) {
          table[index] = 0;
          // NOTE: We don't currently resize when removing items.
          this.tableSize--;
          Object[] constructionContexts = this.constructionContexts;
          Object context = constructionContexts[index];
          constructionContexts[index] = null;
          // At this point we have potentially violated the robin hood invariant so we need to shift
          // following items back.
          // We shift each following item back by 1 unless it is empty or it is has a distance of
          // zero.
          int nextIndex = nextIndex(index, len);
          int nextC = table[nextIndex];
          while (true) {
            if (nextC == 0) {
              break; // empty slot
            }
            if (hash(nextC, len) == nextIndex) {
              break; // this item is in its ideal slot, so we cannot shift it
            }
            table[index] = nextC;
            constructionContexts[index] = constructionContexts[nextIndex];
            table[nextIndex] = 0;
            constructionContexts[nextIndex] = null;
            index = nextIndex;
            nextIndex = nextIndex(nextIndex, len);
            nextC = table[nextIndex];
          }
          return context;
        }
        int cDistance = distance(c, index, len);
        if (cDistance < distance) {
          // This means that we have transitioned into another bucket chain.
          throw new IllegalStateException("table corrupted, key not found");
        }
        index = nextIndex(index, len);
        distance++;
        c = table[index];
      }
    }

    private void expandTable() throws InternalProvisionException {
      int[] oldTable = this.table;
      int oldLength = oldTable.length;
      int newLength = oldLength * 2;
      if (oldLength >= newLength) { // overflow
        throw new IllegalArgumentException("Table size too large to expand: " + oldLength);
      }
      Object[] oldConstructionContexts = this.constructionContexts;
      this.table = new int[newLength];
      this.constructionContexts = new Object[newLength];
      this.tableSize = 0;
      for (int j = 0; j < oldLength; j += 1) {
        int key = oldTable[j];
        if (key != 0) {
          insert(key, null, oldConstructionContexts[j]);
        }
      }
    }
  }

  /**
   * Returns the probe sequence length of the given key assuming it was found in the table at
   * `index`.
   */
  private static int distance(int key, int index, int len) {
    int idealIndex = hash(key, len);
    int naiveDistance = index - idealIndex;
    if (naiveDistance < 0) {
      // Handle the case where we have wrapped around the table.
      return naiveDistance + len;
    }
    return naiveDistance;
  }

  private static int nextIndex(int index, int len) {
    // This is a circular buffer, so we need to wrap around if we are at the end.
    // This pattern is used instead of a mask since after inlining it makes it easier for the VM to
    // eliminate bounds checks on the array.
    return index + 1 < len ? index + 1 : 0;
  }

  private static int hash(int key, int len) {
    return key & (len - 1);
  }
}
