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
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Maps;
import com.google.inject.name.Names;
import static com.google.inject.name.Names.named;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.util.Providers;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * @author dpb@google.com (David P. Baker)
 */
public class MapBinderTest extends TestCase {

  final TypeLiteral<Map<String, String>> mapOfString = new TypeLiteral<Map<String, String>>() {};
  final TypeLiteral<Map<String, Integer>> mapOfInteger = new TypeLiteral<Map<String, Integer>>() {};

  public void testMapBinderAggregatesMultipleModules() {
    Module abc = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");
        multibinder.addBinding("c").toInstance("C");
      }
    };
    Module de = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("d").toInstance("D");
        multibinder.addBinding("e").toInstance("E");
      }
    };

    Injector injector = Guice.createInjector(abc, de);
    Map<String, String> abcde = injector.getInstance(Key.get(mapOfString));

    assertEquals(mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E"), abcde);
  }

  public void testMapBinderAggregationForAnnotationInstance() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Names.named("abc"));
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");

        multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Names.named("abc"));
        multibinder.addBinding("c").toInstance("C");
      }
    });

    Map<String, String> abc = injector.getInstance(Key.get(mapOfString, Names.named("abc")));
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
  }

  public void testMapBinderAggregationForAnnotationType() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");

        multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("c").toInstance("C");
      }
    });

    Map<String, String> abc = injector.getInstance(Key.get(mapOfString, Abc.class));
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
  }

  public void testMapBinderWithMultipleAnnotationValueSets() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> abcMapBinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, named("abc"));
        abcMapBinder.addBinding("a").toInstance("A");
        abcMapBinder.addBinding("b").toInstance("B");
        abcMapBinder.addBinding("c").toInstance("C");

        MapBinder<String, String> deMapBinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, named("de"));
        deMapBinder.addBinding("d").toInstance("D");
        deMapBinder.addBinding("e").toInstance("E");
      }
    });

    Map<String, String> abc = injector.getInstance(Key.get(mapOfString, named("abc")));
    Map<String, String> de = injector.getInstance(Key.get(mapOfString, named("de")));
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertEquals(mapOf("d", "D", "e", "E"), de);
  }

  public void testMapBinderWithMultipleAnnotationTypeSets() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> abcMapBinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        abcMapBinder.addBinding("a").toInstance("A");
        abcMapBinder.addBinding("b").toInstance("B");
        abcMapBinder.addBinding("c").toInstance("C");

        MapBinder<String, String> deMapBinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, De.class);
        deMapBinder.addBinding("d").toInstance("D");
        deMapBinder.addBinding("e").toInstance("E");
      }
    });

    Map<String, String> abc = injector.getInstance(Key.get(mapOfString, Abc.class));
    Map<String, String> de = injector.getInstance(Key.get(mapOfString, De.class));
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertEquals(mapOf("d", "D", "e", "E"), de);
  }

  public void testMapBinderWithMultipleTypes() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class)
            .addBinding("a").toInstance("A");
        MapBinder.newMapBinder(binder(), String.class, Integer.class)
            .addBinding("1").toInstance(1);
      }
    });

    assertEquals(mapOf("a", "A"), injector.getInstance(Key.get(mapOfString)));
    assertEquals(mapOf("1", 1), injector.getInstance(Key.get(mapOfInteger)));
  }

  public void testMapBinderWithEmptyMap() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class);
      }
    });

    Map<String, String> map = injector.getInstance(Key.get(mapOfString));
    assertEquals(Collections.emptyMap(), map);
  }

  public void testMapBinderMapIsUnmodifiable() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class)
            .addBinding("a").toInstance("A");
      }
    });

    Map<String, String> map = injector.getInstance(Key.get(mapOfString));
    try {
      map.clear();
      fail();
    } catch(UnsupportedOperationException expected) {
    }
  }

  public void testMapBinderMapIsLazy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, Integer.class)
            .addBinding("num").toProvider(new Provider<Integer>() {
          int nextValue = 1;
          public Integer get() {
            return nextValue++;
          }
        });
      }
    });

    assertEquals(mapOf("num", 1), injector.getInstance(Key.get(mapOfInteger)));
    assertEquals(mapOf("num", 2), injector.getInstance(Key.get(mapOfInteger)));
    assertEquals(mapOf("num", 3), injector.getInstance(Key.get(mapOfInteger)));
  }

  public void testMapBinderMapForbidsDuplicateKeys() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          MapBinder<String, String> multibinder = MapBinder.newMapBinder(
              binder(), String.class, String.class);
          multibinder.addBinding("a").toInstance("A");
          multibinder.addBinding("a").toInstance("B");
        }
      });
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(),
          "Map injection failed due to duplicated key \"a\"");
    }
  }

  public void testMapBinderMapForbidsNullKeys() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          MapBinder.newMapBinder(binder(), String.class, String.class).addBinding(null);
        }
      });
      fail();
    } catch (CreationException expected) {}
  }

  public void testMapBinderMapForbidsNullValues() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class)
            .addBinding("null").toProvider(Providers.<String>of(null));
      }
    });

    try {
      injector.getInstance(Key.get(mapOfString));
      fail();
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "1) Map injection failed due to null value for key \"null\"");
    }
  }

  public void testMapBinderProviderIsScoped() {
    final Provider<Integer> counter = new Provider<Integer>() {
      int next = 1;
      public Integer get() {
        return next++;
      }
    };

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, Integer.class)
            .addBinding("one").toProvider(counter).asEagerSingleton();
      }
    });

    assertEquals(1, (int) injector.getInstance(Key.get(mapOfInteger)).get("one"));
    assertEquals(1, (int) injector.getInstance(Key.get(mapOfInteger)).get("one"));
  }

  public void testSourceLinesInMapBindings() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          MapBinder.newMapBinder(binder(), String.class, Integer.class)
              .addBinding("one");
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for java.lang.Integer",
          "at " + getClass().getName());
    }
  }

  /** We just want to make sure that mapbinder's binding depends on the underlying multibinder. */
  public void testMultibinderDependencies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        MapBinder<Integer, String> mapBinder 
            = MapBinder.newMapBinder(binder(), Integer.class, String.class);
        mapBinder.addBinding(1).toInstance("A");
        mapBinder.addBinding(2).to(Key.get(String.class, Names.named("b")));

        bindConstant().annotatedWith(Names.named("b")).to("B");
      }
    });

    Binding<Map<Integer, String>> binding = injector.getBinding(new Key<Map<Integer, String>>() {});
    HasDependencies withDependencies = (HasDependencies) binding;
    Key<?> setKey = new Key<Set<Map.Entry<Integer, Provider<String>>>>() {};
    assertEquals(ImmutableSet.<Dependency<?>>of(Dependency.get(setKey)),
        withDependencies.getDependencies());
  }


  /**
   * Our implementation maintains order, but doesn't guarantee it in the API spec.
   * TODO: specify the iteration order?
   */
  public void testBindOrderEqualsIterationOrder() {
    Injector injector = Guice.createInjector(
        new AbstractModule() {
          protected void configure() {
            MapBinder<String, String> mapBinder
                = MapBinder.newMapBinder(binder(), String.class, String.class);
            mapBinder.addBinding("leonardo").toInstance("blue");
            mapBinder.addBinding("donatello").toInstance("purple");
            install(new AbstractModule() {
              protected void configure() {
                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("michaelangelo").toInstance("orange");
              }
            });
          }
        },
        new AbstractModule() {
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, String.class)
                .addBinding("raphael").toInstance("red");
          }
        });

    Map<String, String> map = injector.getInstance(new Key<Map<String, String>>() {});
    Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
    assertEquals(Maps.immutableEntry("leonardo", "blue"), iterator.next());
    assertEquals(Maps.immutableEntry("donatello", "purple"), iterator.next());
    assertEquals(Maps.immutableEntry("michaelangelo", "orange"), iterator.next());
    assertEquals(Maps.immutableEntry("raphael", "red"), iterator.next());
  }

  @Retention(RUNTIME) @BindingAnnotation
  @interface Abc {}

  @Retention(RUNTIME) @BindingAnnotation
  @interface De {}

  @SuppressWarnings("unchecked")
  private <K, V> Map<K, V> mapOf(Object... elements) {
    Map<K, V> result = new HashMap<K, V>();
    for (int i = 0; i < elements.length; i += 2) {
      result.put((K)elements[i], (V)elements[i+1]);
    }
    return result;
  }
}
