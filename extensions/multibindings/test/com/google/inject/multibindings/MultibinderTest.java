/**
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

package com.google.inject.multibindings;

import com.google.inject.*;
import com.google.inject.name.Names;
import static com.google.inject.name.Names.named;
import com.google.inject.util.Providers;
import junit.framework.TestCase;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MultibinderTest extends TestCase {

  final TypeLiteral<Set<String>> setOfString = new TypeLiteral<Set<String>>() {};
  final TypeLiteral<Set<Integer>> setOfInteger = new TypeLiteral<Set<Integer>>() {};

  public void testMultibinderAggregatesMultipleModules() {
    Module abc = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");
        multibinder.addBinding().toInstance("C");
      }
    };
    Module de = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("D");
        multibinder.addBinding().toInstance("E");
      }
    };

    Injector injector = Guice.createInjector(abc, de);
    Set<String> abcde = injector.getInstance(Key.get(setOfString));

    assertEquals(setOf("A", "B", "C", "D", "E"), abcde);
  }

  public void testMultibinderAggregationForAnnotationInstance() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder
            = Multibinder.newSetBinder(binder(), String.class, Names.named("abc"));
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");

        multibinder = Multibinder.newSetBinder(binder(), String.class, Names.named("abc"));
        multibinder.addBinding().toInstance("C");
      }
    });

    Set<String> abcde = injector.getInstance(Key.get(setOfString, Names.named("abc")));
    assertEquals(setOf("A", "B", "C"), abcde);
  }

  public void testMultibinderAggregationForAnnotationType() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder
            = Multibinder.newSetBinder(binder(), String.class, Abc.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");

        multibinder = Multibinder.newSetBinder(binder(), String.class, Abc.class);
        multibinder.addBinding().toInstance("C");
      }
    });

    Set<String> abcde = injector.getInstance(Key.get(setOfString, Abc.class));
    assertEquals(setOf("A", "B", "C"), abcde);
  }

  public void testMultibinderWithMultipleAnnotationValueSets() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder<String> abcMultibinder
            = Multibinder.newSetBinder(binder(), String.class, named("abc"));
        abcMultibinder.addBinding().toInstance("A");
        abcMultibinder.addBinding().toInstance("B");
        abcMultibinder.addBinding().toInstance("C");

        Multibinder<String> deMultibinder
            = Multibinder.newSetBinder(binder(), String.class, named("de"));
        deMultibinder.addBinding().toInstance("D");
        deMultibinder.addBinding().toInstance("E");
      }
    });

    Set<String> abc = injector.getInstance(Key.get(setOfString, named("abc")));
    Set<String> de = injector.getInstance(Key.get(setOfString, named("de")));
    assertEquals(setOf("A", "B", "C"), abc);
    assertEquals(setOf("D", "E"), de);
  }

  public void testMultibinderWithMultipleAnnotationTypeSets() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder<String> abcMultibinder
            = Multibinder.newSetBinder(binder(), String.class, Abc.class);
        abcMultibinder.addBinding().toInstance("A");
        abcMultibinder.addBinding().toInstance("B");
        abcMultibinder.addBinding().toInstance("C");

        Multibinder<String> deMultibinder
            = Multibinder.newSetBinder(binder(), String.class, De.class);
        deMultibinder.addBinding().toInstance("D");
        deMultibinder.addBinding().toInstance("E");
      }
    });

    Set<String> abc = injector.getInstance(Key.get(setOfString, Abc.class));
    Set<String> de = injector.getInstance(Key.get(setOfString, De.class));
    assertEquals(setOf("A", "B", "C"), abc);
    assertEquals(setOf("D", "E"), de);
  }

  public void testMultibinderWithMultipleSetTypes() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), String.class)
            .addBinding().toInstance("A");
        Multibinder.newSetBinder(binder(), Integer.class)
            .addBinding().toInstance(1);
      }
    });

    assertEquals(setOf("A"), injector.getInstance(Key.get(setOfString)));
    assertEquals(setOf(1), injector.getInstance(Key.get(setOfInteger)));
  }

  public void testMultibinderWithEmptySet() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), String.class);
      }
    });

    Set<String> set = injector.getInstance(Key.get(setOfString));
    assertEquals(Collections.emptySet(), set);
  }

  public void testMultibinderSetIsUnmodifiable() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), String.class)
            .addBinding().toInstance("A");
      }
    });

    Set<String> set = injector.getInstance(Key.get(setOfString));
    try {
      set.clear();
      fail();
    } catch(UnsupportedOperationException expected) {
    }
  }

  public void testMultibinderSetIsLazy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), Integer.class)
            .addBinding().toProvider(new Provider<Integer>() {
          int nextValue = 1;
          public Integer get() {
            return nextValue++;
          }
        });
      }
    });

    assertEquals(setOf(1), injector.getInstance(Key.get(setOfInteger)));
    assertEquals(setOf(2), injector.getInstance(Key.get(setOfInteger)));
    assertEquals(setOf(3), injector.getInstance(Key.get(setOfInteger)));
  }

  public void testMultibinderSetForbidsDuplicateElements() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        final Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("A");
      }
    });

    try {
      injector.getInstance(Key.get(setOfString));
      fail();
    } catch(IllegalStateException expected) {
      assertEquals("Set injection failed due to duplicated element \"A\"",
          expected.getMessage());
    }
  }

  public void testMultibinderSetForbidsNullElements() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), String.class)
            .addBinding().toProvider(Providers.<String>of(null));
      }
    });

    try {
      injector.getInstance(Key.get(setOfString));
      fail();
    } catch(IllegalStateException expected) {
      assertEquals("Set injection failed due to null element",
          expected.getMessage());
    }
  }

  @Retention(RUNTIME) @BindingAnnotation
  @interface Abc {}

  @Retention(RUNTIME) @BindingAnnotation
  @interface De {}

  private <T> Set<T> setOf(T... elements) {
    Set<T> result = new HashSet<T>();
    result.addAll(Arrays.asList(elements));
    return result;
  }
}
