/**
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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertNotSerializable;
import java.io.IOException;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class InjectorTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface Other {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface S {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface I {}

  public void testToStringDoesNotInfinitelyRecurse() {
    Injector injector = Guice.createInjector(Stage.TOOL);
    injector.toString();
    injector.getBinding(Injector.class).toString();
  }

  public void testProviderMethods() throws CreationException {
    final SampleSingleton singleton = new SampleSingleton();
    final SampleSingleton other = new SampleSingleton();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(SampleSingleton.class).toInstance(singleton);
        bind(SampleSingleton.class)
            .annotatedWith(Other.class)
            .toInstance(other);
      }
    });

    assertSame(singleton,
        injector.getInstance(Key.get(SampleSingleton.class)));
    assertSame(singleton, injector.getInstance(SampleSingleton.class));

    assertSame(other,
        injector.getInstance(Key.get(SampleSingleton.class, Other.class)));
  }

  static class SampleSingleton {}

  public void testInjection() throws CreationException {
    Injector injector = createFooInjector();
    Foo foo = injector.getInstance(Foo.class);

    assertEquals("test", foo.s);
    assertEquals("test", foo.bar.getTee().getS());
    assertSame(foo.bar, foo.copy);
    assertEquals(5, foo.i);
    assertEquals(5, foo.bar.getI());

    // Test circular dependency.
    assertSame(foo.bar, foo.bar.getTee().getBar());
  }

  private Injector createFooInjector() throws CreationException {
    return Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Bar.class).to(BarImpl.class);
        bind(Tee.class).to(TeeImpl.class);
        bindConstant().annotatedWith(S.class).to("test");
        bindConstant().annotatedWith(I.class).to(5);
      }
    });
  }

  public void testGetInstance() throws CreationException {
    Injector injector = createFooInjector();

    Bar bar = injector.getInstance(Key.get(Bar.class));
    assertEquals("test", bar.getTee().getS());
    assertEquals(5, bar.getI());
  }

  public void testIntAndIntegerAreInterchangeable()
      throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(I.class).to(5);
      }
    });

    IntegerWrapper iw = injector.getInstance(IntegerWrapper.class);
    assertEquals(5, (int) iw.i);
  }

  public void testInjectorApiIsNotSerializable() throws IOException {
    Injector injector = Guice.createInjector();
    assertNotSerializable(injector);
    assertNotSerializable(injector.getProvider(String.class));
    assertNotSerializable(injector.getBinding(String.class));
    for (Binding<?> binding : injector.getBindings().values()) {
      assertNotSerializable(binding);
    }
  }

  static class IntegerWrapper {
    @Inject @I Integer i;
  }

  static class Foo {

    @Inject Bar bar;
    @Inject Bar copy;

    @Inject @S String s;

    int i;

    @Inject
    void setI(@I int i) {
      this.i = i;
    }
  }

  interface Bar {

    Tee getTee();
    int getI();
  }

  @Singleton
  static class BarImpl implements Bar {

    @Inject @I int i;

    Tee tee;

    @Inject
    void initialize(Tee tee) {
      this.tee = tee;
    }

    public Tee getTee() {
      return tee;
    }

    public int getI() {
      return i;
    }
  }

  interface Tee {

    String getS();
    Bar getBar();
  }

  static class TeeImpl implements Tee {

    final String s;
    @Inject Bar bar;

    @Inject
    TeeImpl(@S String s) {
      this.s = s;
    }

    public String getS() {
      return s;
    }

    public Bar getBar() {
      return bar;
    }
  }

  public void testInjectStatics() throws CreationException {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(S.class).to("test");
        bindConstant().annotatedWith(I.class).to(5);
        requestStaticInjection(Static.class);
      }
    });

    assertEquals("test", Static.s);
    assertEquals(5, Static.i);
  }

  static class Static {

    @Inject @I static int i;

    static String s;

    @Inject static void setS(@S String s) {
      Static.s = s;
    }
  }

  public void testPrivateInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("foo");
        bind(int.class).toInstance(5);
      }
    });

    Private p = injector.getInstance(Private.class);
    assertEquals("foo", p.fromConstructor);
    assertEquals(5, p.fromMethod);
  }

  static class Private {
    String fromConstructor;
    int fromMethod;

    @Inject
    private Private(String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @Inject
    private void setInt(int i) {
      this.fromMethod = i;
    }
  }

  public void testProtectedInjection() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("foo");
        bind(int.class).toInstance(5);
      }
    });

    Protected p = injector.getInstance(Protected.class);
    assertEquals("foo", p.fromConstructor);
    assertEquals(5, p.fromMethod);
  }

  static class Protected {
    String fromConstructor;
    int fromMethod;

    @Inject
    protected Protected(String fromConstructor) {
      this.fromConstructor = fromConstructor;
    }

    @Inject
    protected void setInt(int i) {
      this.fromMethod = i;
    }
  }

  public void testInstanceInjectionHappensAfterFactoriesAreSetUp() {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Object.class).toInstance(new Object() {
          @Inject Runnable r;
        });

        bind(Runnable.class).to(MyRunnable.class);
      }
    });
  }

  public void testSubtypeNotProvided() {
    try {
      Guice.createInjector().getInstance(Money.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          Tree.class.getName() + " doesn't provide instances of " + Money.class.getName(),
          "while locating ", Tree.class.getName(),
          "while locating ", Money.class.getName());
    }
  }

  public void testNotASubtype() {
    try {
      Guice.createInjector().getInstance(PineTree.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          Tree.class.getName() + " doesn't extend " + PineTree.class.getName(),
          "while locating ", PineTree.class.getName());
    }
  }

  public void testRecursiveImplementationType() {
    try {
      Guice.createInjector().getInstance(SeaHorse.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "@ImplementedBy points to the same class it annotates.",
          "while locating ", SeaHorse.class.getName());
    }
  }

  public void testRecursiveProviderType() {
    try {
      Guice.createInjector().getInstance(Chicken.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "@ProvidedBy points to the same class it annotates",
          "while locating ", Chicken.class.getName());
    }
  }

  static class MyRunnable implements Runnable {
   public void run() {}
  }

  @ProvidedBy(Tree.class)
  static class Money {}

  static class Tree implements Provider<Object> {
    public Object get() {
      return "Money doesn't grow on trees";
    }
  }

  @ImplementedBy(Tree.class)
  static class PineTree extends Tree {}

  @ImplementedBy(SeaHorse.class)
  static class SeaHorse {}

  @ProvidedBy(Chicken.class)
  static class Chicken implements Provider<Chicken> {
    public Chicken get() {
      return this;
    }
  }

  public void testJitBindingFromAnotherThreadDuringInjection() {
    final ExecutorService executorService = Executors.newSingleThreadExecutor();
    final AtomicReference<JustInTime> got = new AtomicReference<JustInTime>();

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        requestInjection(new Object() {
          @Inject void initialize(final Injector injector)
              throws ExecutionException, InterruptedException {
            Future<JustInTime> future = executorService.submit(new Callable<JustInTime>() {
              public JustInTime call() throws Exception {
                return injector.getInstance(JustInTime.class);
              }
            });
            got.set(future.get());
          }
        });
      }
    });

    assertNotNull(got.get());
  }

  static class JustInTime {}
}
