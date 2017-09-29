/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject;

import static com.google.inject.Asserts.getClassPathUrls;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
public class EagerSingletonTest extends TestCase {

  @Override
  public void setUp() {
    A.instanceCount = 0;
    B.instanceCount = 0;
    C.instanceCount = 0;
  }

  public void testJustInTimeEagerSingletons() {
    Guice.createInjector(
        Stage.PRODUCTION,
        new AbstractModule() {
          @Override
          protected void configure() {
            // create a just-in-time binding for A
            getProvider(A.class);

            // create a just-in-time binding for C
            requestInjection(
                new Object() {
                  @Inject
                  void inject(Injector injector) {
                    injector.getInstance(C.class);
                  }
                });
          }
        });

    assertEquals(1, A.instanceCount);
    assertEquals(
        "Singletons discovered when creating singletons should not be built eagerly",
        0,
        B.instanceCount);
    assertEquals(1, C.instanceCount);
  }

  public void testJustInTimeSingletonsAreNotEager() {
    Injector injector = Guice.createInjector(Stage.PRODUCTION);
    injector.getProvider(B.class);
    assertEquals(0, B.instanceCount);
  }

  public void testChildEagerSingletons() {
    Injector parent = Guice.createInjector(Stage.PRODUCTION);
    parent.createChildInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(D.class).to(C.class);
          }
        });

    assertEquals(1, C.instanceCount);
  }

  // there used to be a bug that caused a concurrent modification exception if jit bindings were
  // loaded during eager singleton creation due to failur to apply the lock when iterating over
  // all bindings.

  public void testJustInTimeEagerSingletons_multipleThreads() throws Exception {
    // in order to make the data race more likely we need a lot of jit bindings.  The easiest thing
    // is just to 'copy' out class for B a bunch of times.
    final List<Class<?>> jitBindings = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      jitBindings.add(copyClass(B.class));
    }
    Guice.createInjector(
        Stage.PRODUCTION,
        new AbstractModule() {
          @Override
          protected void configure() {
            // create a just-in-time binding for A
            getProvider(A.class);

            // create a just-in-time binding for C
            requestInjection(
                new Object() {
                  @Inject
                  void inject(final Injector injector) throws Exception {
                    final CountDownLatch latch = new CountDownLatch(1);
                    new Thread() {
                      @Override
                      public void run() {
                        latch.countDown();
                        for (Class<?> jitBinding : jitBindings) {
                          // this causes the binding to be created
                          injector.getProvider(jitBinding);
                        }
                      }
                    }.start();
                    latch.await(); // make sure the thread is running before returning
                  }
                });
          }
        });

    assertEquals(1, A.instanceCount);
    // our thread runs in parallel with eager singleton loading so some there should be some number
    // N such that 0<=N <jitBindings.size() and all classes in jitBindings with an index < N will
    // have been initialized.  Assert that!
    int prev = -1;
    int index = 0;
    for (Class<?> jitBinding : jitBindings) {
      int instanceCount = (Integer) jitBinding.getDeclaredField("instanceCount").get(null);
      if (instanceCount != 0 && instanceCount != 1) {
        fail("Should only have created zero or one instances, got " + instanceCount);
      }
      if (prev == -1) {
        prev = instanceCount;
      } else if (prev != instanceCount) {
        if (prev != 1 && instanceCount != 0) {
          fail("initialized later JIT bindings before earlier ones at index " + index);
        }
        prev = instanceCount;
      }
      index++;
    }
  }

  /** Creates a copy of a class in a child classloader. */
  private static Class<?> copyClass(final Class<?> cls) {
    // To create a copy of a class we create a new child class loader with the same data as our
    // parent and override loadClass to always load a new copy of cls.
    try {
      return new URLClassLoader(getClassPathUrls(), EagerSingletonTest.class.getClassLoader()) {
        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
          // This means for every class besides cls we delegate to our parent (so things like
          // @Singleton and Object are well defined), but for this one class we load a new one in
          // this loader.
          if (name.equals(cls.getName())) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
              return super.findClass(name);
            }
            return c;
          }
          return super.loadClass(name);
        }
      }.loadClass(cls.getName());
    } catch (ClassNotFoundException cnfe) {
      throw new AssertionError(cnfe);
    }
  }

  @Singleton
  static class A {
    static int instanceCount = 0;
    int instanceId = instanceCount++;

    @Inject
    A(Injector injector) {
      injector.getProvider(B.class);
    }
  }

  @Singleton
  public static class B {
    public static int instanceCount = 0;
    int instanceId = instanceCount++;
  }

  @Singleton
  static class C implements D {
    static int instanceCount = 0;
    int instanceId = instanceCount++;
  }

  private static interface D {}
}
