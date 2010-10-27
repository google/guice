package com.google.inject.internal.util;

import com.google.inject.internal.util.FinalizableReferenceQueue;
import com.google.inject.internal.util.FinalizableWeakReference;
import com.google.inject.internal.util.Finalizer;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class FinalizableReferenceQueueTest extends TestCase {

  private FinalizableReferenceQueue frq;

  @Override
  protected void tearDown() throws Exception {
    frq = null;
  }

  public void testFinalizeReferentCalled() {
    MockReference reference = new MockReference(
        frq = new FinalizableReferenceQueue());
    // wait up to 5s
    for (int i = 0; i < 500; i++) {
      if (reference.finalizeReferentCalled) {
        return;
      }
      try {
        System.gc();
        Thread.sleep(10);
      } catch (InterruptedException e) { /* ignore */ }
    }
    fail();
  }

  static class MockReference extends FinalizableWeakReference<Object> {

    volatile boolean finalizeReferentCalled;

    MockReference(FinalizableReferenceQueue frq) {
      super(new Object(), frq);
    }

    public void finalizeReferent() {
      finalizeReferentCalled = true;
    }
  }

  /**
   * Keeps a weak reference to the underlying reference queue. When this
   * reference is cleared, we know that the background thread has stopped
   * and released its strong reference.
   */
  private WeakReference<ReferenceQueue<Object>> queueReference;

  public void testThatFinalizerStops() {
    weaklyReferenceQueue();

    // wait up to 5s
    for (int i = 0; i < 500; i++) {
      if (queueReference.get() == null) {
        return;
      }
      try {
        System.gc();
        Thread.sleep(10);
      } catch (InterruptedException e) { /* ignore */ }
    }
    fail();
  }

  /**
   * If we don't keep a strong reference to the reference object, it won't
   * be enqueued.
   */
  FinalizableWeakReference<Object> reference;

  /**
   * Create the FRQ in a method that goes out of scope so that we're sure
   * it will be reclaimed.
   */
  private void weaklyReferenceQueue() {
    frq = new FinalizableReferenceQueue();
    queueReference = new WeakReference<ReferenceQueue<Object>>(frq.queue);

    /*
     * Queue and clear a reference for good measure. We test later on that
     * the finalizer thread stopped, but we should test that it actually
     * started first.
     */
    reference = new FinalizableWeakReference<Object>(new Object(), frq) {
      public void finalizeReferent() {
        reference = null;
        frq = null;
      }
    };
  }

  public void testDecoupledLoader() {
    FinalizableReferenceQueue.DecoupledLoader decoupledLoader =
        new FinalizableReferenceQueue.DecoupledLoader() {
          @Override
          URLClassLoader newLoader(URL base) {
            return new DecoupledClassLoader(new URL[] { base });
          }
        };

    Class<?> finalizerCopy = decoupledLoader.loadFinalizer();

    assertNotNull(finalizerCopy);
    assertNotSame(Finalizer.class, finalizerCopy);

    assertNotNull(FinalizableReferenceQueue.getStartFinalizer(finalizerCopy));
  }

  static class DecoupledClassLoader extends URLClassLoader {

    public DecoupledClassLoader(URL[] urls) {
      super(urls);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
      // Force Finalizer to load from this class loader, not its parent.
      if (name.equals(Finalizer.class.getName())) {
        Class<?> clazz = findClass(name);
        if (resolve) {
          resolveClass(clazz);
        }
        return clazz;
      }

      return super.loadClass(name, resolve);
    }
  }

  public void testGetFinalizerUrl() {
    assertNotNull(getClass().getResource(Finalizer.class.getSimpleName() + ".class"));
  }
}


