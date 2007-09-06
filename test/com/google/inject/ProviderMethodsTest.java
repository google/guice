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

import junit.framework.TestCase;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProviderMethodsTest extends TestCase {

  public void testProviderMethods() {
    Injector injector = Guice.createInjector(new Module() {
      public void configure(Binder binder) {
        binder.install(ProviderMethods.from(ProviderMethodsTest.this));
      }
    });

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
}