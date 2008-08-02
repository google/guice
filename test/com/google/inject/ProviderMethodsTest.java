/**
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

import com.google.common.collect.ImmutableSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Types;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.Set;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProviderMethodsTest extends TestCase {

  @SuppressWarnings("unchecked")
  public void testProviderMethods() {
    Injector injector = Guice.createInjector(ProviderMethods.from(ProviderMethodsTest.this));

    Bob bob = injector.getInstance(Bob.class);
    assertEquals("A Bob", bob.getName());

    Bob clone = injector.getInstance(Bob.class);
    assertEquals("A Bob", clone.getName());

    assertNotSame(bob, clone);
    assertSame(bob.getDaughter(), clone.getDaughter());

    Key soleBobKey = Key.get(Bob.class, Sole.class);
    assertSame(
        injector.getInstance(soleBobKey),
        injector.getInstance(soleBobKey)
    );
  }

  interface Bob {
    String getName();
    Dagny getDaughter();
  }

  interface Dagny {
    int getAge();
  }

  @Provides
  Bob provideBob(final Dagny dagny) {
    return new Bob() {
      public String getName() {
        return "A Bob";
      }

      public Dagny getDaughter() {
        return dagny;
      }
    };
  }

  @Provides
  @Singleton
  @Sole
  Bob provideSoleBob(final Dagny dagny) {
    return new Bob() {
      public String getName() {
        return "Only Bob";
      }

      public Dagny getDaughter() {
        return dagny;
      }
    };
  }

  @Provides
  @Singleton
  Dagny provideDagny() {
    return new Dagny() {
      public int getAge() {
        return 1;
      }
    };
  }

  @Retention(RUNTIME)
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  @interface Sole {}



// We'll have to make getProvider() support circular dependencies before this
// will work.
//
//  public void testCircularDependency() {
//    Injector injector = Guice.createInjector(new Module() {
//      public void configure(Binder binder) {
//        binder.install(ProviderMethods.from(ProviderMethodsTest.this));
//      }
//    });
//
//    Foo foo = injector.getInstance(Foo.class);
//    assertEquals(5, foo.getI());
//    assertEquals(10, foo.getBar().getI());
//    assertEquals(5, foo.getBar().getFoo().getI());
//  }
//
//  interface Foo {
//    Bar getBar();
//    int getI();
//  }
//
//  interface Bar {
//    Foo getFoo();
//    int getI();
//  }
//
//  @Provides Foo newFoo(final Bar bar) {
//    return new Foo() {
//
//      public Bar getBar() {
//        return bar;
//      }
//
//      public int getI() {
//        return 5;
//      }
//    };
//  }
//
//  @Provides Bar newBar(final Foo foo) {
//    return new Bar() {
//
//      public Foo getFoo() {
//        return foo;
//      }
//
//      public int getI() {
//        return 10;
//      }
//    };
//  }


  public void testMultipleBindingAnnotations() {
    try {
      Guice.createInjector(ProviderMethods.from(new Object() {
        @Provides @Named("A") @Blue
        public String provideString() {
          return "a";
        }
      }));
      fail();
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(),
          "Error at " + getClass().getName(), ".provideString()",
          "more than one annotation annotated with @BindingAnnotation:", "Named", "Blue");
    }

  }

  @Retention(RUNTIME)
  @BindingAnnotation @interface Blue {}

  public void testGenericProviderMethods() {
    Injector injector = Guice.createInjector(
        ProviderMethods.from(new ProvideTs<String>("A", "B") {}),
        ProviderMethods.from(new ProvideTs<Integer>(1, 2) {}));
    
    assertEquals("A", injector.getInstance(Key.get(String.class, Names.named("First"))));
    assertEquals("B", injector.getInstance(Key.get(String.class, Names.named("Second"))));
    assertEquals(ImmutableSet.of("A", "B"),
        injector.getInstance(Key.get(Types.setOf(String.class))));

    assertEquals(1, injector.getInstance(Key.get(Integer.class, Names.named("First"))).intValue());
    assertEquals(2, injector.getInstance(Key.get(Integer.class, Names.named("Second"))).intValue());
    assertEquals(ImmutableSet.of(1, 2),
        injector.getInstance(Key.get(Types.setOf(Integer.class))));
  }

  abstract class ProvideTs<T> {
    final T first;
    final T second;

    protected ProvideTs(T first, T second) {
      this.first = first;
      this.second = second;
    }

    @Named("First") @Provides T provideFirst() {
      return first;
    }

    @Named("Second") @Provides T provideSecond() {
      return second;
    }

    @Provides Set<T> provideBoth(@Named("First") T first, @Named("Second") T second) {
      return ImmutableSet.of(first, second);
    }
  }
  
  public void testAutomaticProviderMethods() {
    Injector injector = Guice.createInjector((Module) new AbstractModule() {
      protected void configure() { }
      private int next = 1;

      @Provides @Named("count")
      public Integer provideCount() {
        return next++;
      }
    });

    assertEquals(1, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
    assertEquals(2, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
    assertEquals(3, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
  }

  /**
   * If the user installs provider methods for the module manually, that shouldn't cause a double
   * binding of the provider methods' types.
   */
  public void testAutomaticProviderMethodsDoNotCauseDoubleBinding() {
    Module installsSelf = new AbstractModule() {
      protected void configure() {
        install(ProviderMethods.from(this));
        bind(Integer.class).toInstance(5);
      }
      @Provides public String provideString(Integer count) {
        return "A" + count;
      }
    };

    Injector injector = Guice.createInjector(installsSelf);
    assertEquals("A5", injector.getInstance(String.class));
  }
}