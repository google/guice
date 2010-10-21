/*
 * Copyright (C) 2007 Google Inc.
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
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Sets;
import com.google.inject.matcher.Matchers;
import static com.google.inject.name.Names.named;

import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import junit.framework.TestCase;
/*if[AOP]*/
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
/*end[AOP]*/

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingTest extends TestCase {

  static class Dependent {
    @Inject A a;
    @Inject Dependent(A a, B b) {}
    @Inject void injectBob(Bob bob) {}
  }

  public void testExplicitCyclicDependency() {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class);
        bind(B.class);
      }
    }).getInstance(A.class);
  }

  static class A { @Inject B b; }
  static class B { @Inject A a; }

  static class Bob {}

  static class MyModule extends AbstractModule {

    protected void configure() {
      // Linked.
      bind(Object.class).to(Runnable.class).in(Scopes.SINGLETON);

      // Instance.
      bind(Runnable.class).toInstance(new Runnable() {
        public void run() {}
      });

      // Provider instance.
      bind(Foo.class).toProvider(new Provider<Foo>() {
        public Foo get() {
          return new Foo();
        }
      }).in(Scopes.SINGLETON);

      // Provider.
      bind(Foo.class)
          .annotatedWith(named("provider"))
          .toProvider(FooProvider.class);

      // Class.
      bind(Bar.class).in(Scopes.SINGLETON);

      // Constant.
      bindConstant().annotatedWith(named("name")).to("Bob");
    }
  }

  static class Foo {}

  public static class FooProvider implements Provider<Foo> {
    public Foo get() {
      throw new UnsupportedOperationException();
    }
  }

  public static class Bar {}

  public void testBindToUnboundLinkedBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Collection.class).to(List.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.util.List was bound.");
    }
  }

  /**
   * This test ensures that the asEagerSingleton() scoping applies to the key,
   * not to what the key is linked to.
   */
  public void testScopeIsAppliedToKeyNotTarget() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Integer.class).toProvider(Counter.class).asEagerSingleton();
        bind(Number.class).toProvider(Counter.class).asEagerSingleton();
      }
    });

    assertNotSame(injector.getInstance(Integer.class), injector.getInstance(Number.class));
  }

  static class Counter implements Provider<Integer> {
    static AtomicInteger next = new AtomicInteger(1);
    public Integer get() {
      return next.getAndIncrement();
    }
  }

  public void testAnnotatedNoArgConstructor() {
    assertBindingSucceeds(PublicNoArgAnnotated.class);
    assertBindingSucceeds(ProtectedNoArgAnnotated.class);
    assertBindingSucceeds(PackagePrivateNoArgAnnotated.class);
    assertBindingSucceeds(PrivateNoArgAnnotated.class);
  }

  static class PublicNoArgAnnotated {
    @Inject public PublicNoArgAnnotated() { }
  }

  static class ProtectedNoArgAnnotated {
    @Inject protected ProtectedNoArgAnnotated() { }
  }

  static class PackagePrivateNoArgAnnotated {
    @Inject PackagePrivateNoArgAnnotated() { }
  }

  static class PrivateNoArgAnnotated {
    @Inject private PrivateNoArgAnnotated() { }
  }

  public void testUnannotatedNoArgConstructor() throws Exception{
    assertBindingSucceeds(PublicNoArg.class);
    assertBindingSucceeds(ProtectedNoArg.class);
    assertBindingSucceeds(PackagePrivateNoArg.class);
    assertBindingSucceeds(PrivateNoArgInPrivateClass.class);
    assertBindingFails(PrivateNoArg.class);
  }

  static class PublicNoArg {
    public PublicNoArg() { }
  }

  static class ProtectedNoArg {
    protected ProtectedNoArg() { }
  }

  static class PackagePrivateNoArg {
    PackagePrivateNoArg() { }
  }

  private static class PrivateNoArgInPrivateClass {
    PrivateNoArgInPrivateClass() { }
  }

  static class PrivateNoArg {
    private PrivateNoArg() { }
  }

  private void assertBindingSucceeds(final Class<?> clazz) {
    assertNotNull(Guice.createInjector().getInstance(clazz));
  }

  private void assertBindingFails(final Class<?> clazz) throws NoSuchMethodException {
    try {
      Guice.createInjector().getInstance(clazz);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "Could not find a suitable constructor in " + PrivateNoArg.class.getName(),
          "at " + PrivateNoArg.class.getName() + ".class(BindingTest.java:");
    }
  }

  public void testTooManyConstructors() {
    try {
      Guice.createInjector().getInstance(TooManyConstructors.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          TooManyConstructors.class.getName() + " has more than one constructor annotated with " 
              + "@Inject. Classes must have either one (and only one) constructor",
          "at " + TooManyConstructors.class.getName() + ".class(BindingTest.java:");
    }
  }

  static class TooManyConstructors {
    @Inject TooManyConstructors(Injector i) {}
    @Inject TooManyConstructors() {}
  }

  public void testToConstructorBinding() throws NoSuchMethodException {
    final Constructor<D> constructor = D.class.getConstructor(Stage.class);

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Object.class).toConstructor(constructor);
      }
    });

    D d = (D) injector.getInstance(Object.class);
    assertEquals(Stage.DEVELOPMENT, d.stage);
  }

  public void testToConstructorBindingsOnParameterizedTypes() throws NoSuchMethodException {
    final Constructor<C> constructor = C.class.getConstructor(Stage.class, Object.class);
    final Key<Object> s = new Key<Object>(named("s")) {};
    final Key<Object> i = new Key<Object>(named("i")) {};

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(s).toConstructor(constructor, new TypeLiteral<C<Stage>>() {});
        bind(i).toConstructor(constructor, new TypeLiteral<C<Injector>>() {});
      }
    });

    C<Stage> one = (C<Stage>) injector.getInstance(s);
    assertEquals(Stage.DEVELOPMENT, one.stage);
    assertEquals(Stage.DEVELOPMENT, one.t);
    assertEquals(Stage.DEVELOPMENT, one.anotherT);

    C<Injector> two = (C<Injector>) injector.getInstance(i);
    assertEquals(Stage.DEVELOPMENT, two.stage);
    assertEquals(injector, two.t);
    assertEquals(injector, two.anotherT);
  }

  public void testToConstructorBindingsFailsOnRawTypes() throws NoSuchMethodException {
    final Constructor constructor = C.class.getConstructor(Stage.class, Object.class);

    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Object.class).toConstructor(constructor);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) T cannot be used as a key; It is not fully specified.",
          "at " + C.class.getName() + ".<init>(BindingTest.java:",
          "2) T cannot be used as a key; It is not fully specified.",
          "at " + C.class.getName() + ".anotherT(BindingTest.java:");
    }
  }

/*if[AOP]*/
  public void testToConstructorAndMethodInterceptors() throws NoSuchMethodException {
    final Constructor<D> constructor = D.class.getConstructor(Stage.class);
    final AtomicInteger count = new AtomicInteger();
    final MethodInterceptor countingInterceptor = new MethodInterceptor() {
      public Object invoke(MethodInvocation methodInvocation) throws Throwable {
        count.incrementAndGet();
        return methodInvocation.proceed();
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Object.class).toConstructor(constructor);
        bindInterceptor(Matchers.any(), Matchers.any(), countingInterceptor);
      }
    });

    D d = (D) injector.getInstance(Object.class);
    d.hashCode();
    d.hashCode();
    assertEquals(2, count.get());
  }
/*end[AOP]*/

  public void testInaccessibleConstructor() throws NoSuchMethodException {
    final Constructor<E> constructor = E.class.getDeclaredConstructor(Stage.class);

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(E.class).toConstructor(constructor);
      }
    });

    E e = injector.getInstance(E.class);
    assertEquals(Stage.DEVELOPMENT, e.stage);
  }

  public void testToConstructorAndScopes() throws NoSuchMethodException {
    final Constructor<F> constructor = F.class.getConstructor(Stage.class);

    final Key<Object> d = Key.get(Object.class, named("D")); // default scoping
    final Key<Object> s = Key.get(Object.class, named("S")); // singleton
    final Key<Object> n = Key.get(Object.class, named("N")); // "N" instances
    final Key<Object> r = Key.get(Object.class, named("R")); // a regular binding

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(d).toConstructor(constructor);
        bind(s).toConstructor(constructor).in(Singleton.class);
        bind(n).toConstructor(constructor).in(Scopes.NO_SCOPE);
        bind(r).to(F.class);
      }
    });

    assertDistinct(injector, 1, d, d, d, d);
    assertDistinct(injector, 1, s, s, s, s);
    assertDistinct(injector, 4, n, n, n, n);
    assertDistinct(injector, 1, r, r, r, r);
    assertDistinct(injector, 4, d, d, r, r, s, s, n);
  }

  public void assertDistinct(Injector injector, int expectedCount, Key<?>... keys) {
    ImmutableSet.Builder<Object> builder = ImmutableSet.builder();
    for (Key<?> k : keys) {
      builder.add(injector.getInstance(k));
    }
    assertEquals(expectedCount, builder.build().size());
  }

  public void testToConstructorSpiData() throws NoSuchMethodException {
    final Set<TypeLiteral<?>> heardTypes = Sets.newHashSet();

    final Constructor<D> constructor = D.class.getConstructor(Stage.class);
    final TypeListener listener = new TypeListener() {
      public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        if (!heardTypes.add(type)) {
          fail("Heard " + type + " multiple times!");
        }
      }
    };

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Object.class).toConstructor(constructor);
        bind(D.class).toConstructor(constructor);
        bindListener(Matchers.any(), listener);
      }
    });
    
    assertEquals(ImmutableSet.of(TypeLiteral.get(Stage.class), TypeLiteral.get(D.class)),
        heardTypes);
  }

  public void testInterfaceToImplementationConstructor() throws NoSuchMethodException {
    final Constructor<CFoo> constructor = CFoo.class.getDeclaredConstructor();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(IFoo.class).toConstructor(constructor);
      }
    });

    injector.getInstance(IFoo.class);
  }

  public static interface IFoo {}
  public static class CFoo implements IFoo {}

  public void testGetAllBindings() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(D.class).toInstance(new D(Stage.PRODUCTION));
        bind(Object.class).to(D.class);
        getProvider(new Key<C<Stage>>() {});
      }
    });

    Map<Key<?>,Binding<?>> bindings = injector.getAllBindings();
    assertEquals(ImmutableSet.of(Key.get(Injector.class), Key.get(Stage.class), Key.get(D.class),
        Key.get(Logger.class), Key.get(Object.class), new Key<C<Stage>>() {}),
        bindings.keySet());

    // add a JIT binding
    injector.getInstance(F.class);

    Map<Key<?>,Binding<?>> bindings2 = injector.getAllBindings();
    assertEquals(ImmutableSet.of(Key.get(Injector.class), Key.get(Stage.class), Key.get(D.class),
        Key.get(Logger.class), Key.get(Object.class), new Key<C<Stage>>() {}, Key.get(F.class)),
        bindings2.keySet());

    // the original map shouldn't have changed
    assertEquals(ImmutableSet.of(Key.get(Injector.class), Key.get(Stage.class), Key.get(D.class),
        Key.get(Logger.class), Key.get(Object.class), new Key<C<Stage>>() {}),
        bindings.keySet());

    // check the bindings' values
    assertEquals(injector, bindings.get(Key.get(Injector.class)).getProvider().get());
  }

  public void testGetAllServletBindings() throws Exception {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(F.class); // an explicit binding that uses a JIT binding for a constructor
      }
    });
    injector.getAllBindings();
  }

  public static class C<T> {
    private Stage stage;
    private T t;
    @Inject T anotherT;

    public C(Stage stage, T t) {
      this.stage = stage;
      this.t = t;
    }

    @Inject C() {}
  }

  public static class D {
    Stage stage;
    public D(Stage stage) {
      this.stage = stage;
    }
  }

  private static class E {
    Stage stage;
    private E(Stage stage) {
      this.stage = stage;
    }
  }

  @Singleton
  public static class F {
    Stage stage;
    @Inject public F(Stage stage) {
      this.stage = stage;
    }
  }
  
  public void testTurkeyBaconProblemUsingToConstuctor() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @SuppressWarnings("unchecked")
      @Override
      public void configure() {
        bind(Bacon.class).to(UncookedBacon.class);
        bind(Bacon.class).annotatedWith(named("Turkey")).to(TurkeyBacon.class);
        bind(Bacon.class).annotatedWith(named("Cooked")).toConstructor(
            (Constructor)InjectionPoint.forConstructorOf(Bacon.class).getMember());
      }
    });
    Bacon bacon = injector.getInstance(Bacon.class);
    assertEquals(Food.PORK, bacon.getMaterial());
    assertFalse(bacon.isCooked());
    
    Bacon turkeyBacon = injector.getInstance(Key.get(Bacon.class, named("Turkey")));
    assertEquals(Food.TURKEY, turkeyBacon.getMaterial());
    assertTrue(turkeyBacon.isCooked());
    
    Bacon cookedBacon = injector.getInstance(Key.get(Bacon.class, named("Cooked")));
    assertEquals(Food.PORK, cookedBacon.getMaterial());
    assertTrue(cookedBacon.isCooked());    
  }
  
  enum Food { TURKEY, PORK }
  
  private static class Bacon {
    public Food getMaterial() { return Food.PORK; }
    public boolean isCooked() { return true; }
  }

  private static class TurkeyBacon extends Bacon {
    public Food getMaterial() { return Food.TURKEY; }
  }

  private static class UncookedBacon extends Bacon {
    public boolean isCooked() { return false; }
  }
}
