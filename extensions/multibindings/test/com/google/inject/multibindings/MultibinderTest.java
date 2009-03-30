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

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Sets;
import com.google.inject.name.Names;
import static com.google.inject.name.Names.named;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.util.Providers;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

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
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "1) Set injection failed due to duplicated element \"A\"");
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
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(), 
          "1) Set injection failed due to null element");
    }
  }

  public void testSourceLinesInMultibindings() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          Multibinder.newSetBinder(binder(), Integer.class).addBinding();
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.lang.Integer", 
          "at " + getClass().getName());
    }
  }

  /**
   * We just want to make sure that multibinder's binding depends on each of its values. We don't
   * really care about the underlying structure of those bindings, which are implementation details.
   */
  public void testMultibinderDependencies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().to(Key.get(String.class, Names.named("b")));

        bindConstant().annotatedWith(Names.named("b")).to("B");
      }
    });

    Binding<Set<String>> binding = injector.getBinding(new Key<Set<String>>() {});
    HasDependencies withDependencies = (HasDependencies) binding;
    Set<String> elements = Sets.newHashSet();
    for (Dependency<?> dependency : withDependencies.getDependencies()) {
      elements.add((String) injector.getInstance(dependency.getKey()));
    }
    assertEquals(ImmutableSet.of("A", "B"), elements);
  }

  /**
   * Our implementation maintains order, but doesn't guarantee it in the API spec.
   * TODO: specify the iteration order?
   */
  public void testBindOrderEqualsIterationOrder() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("leonardo");
            multibinder.addBinding().toInstance("donatello");
            install(new AbstractModule() {
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class)
                    .addBinding().toInstance("michaelangelo");
              }
            });
          }
        },
        new AbstractModule() {
          protected void configure() {
            Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("raphael");
          }
        });

    List<String> inOrder = ImmutableList.copyOf(injector.getInstance(Key.get(setOfString)));
    assertEquals(ImmutableList.of("leonardo", "donatello", "michaelangelo", "raphael"), inOrder);
  }

  @Retention(RUNTIME) @BindingAnnotation
  @interface Abc {}

  @Retention(RUNTIME) @BindingAnnotation
  @interface De {}

  private <T> Set<T> setOf(T... elements) {
    Set<T> result = Sets.newHashSet();
    result.addAll(Arrays.asList(elements));
    return result;
  }
}
