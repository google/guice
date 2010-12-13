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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.multibindings.SpiUtils.assertMapVisitor;
import static com.google.inject.multibindings.SpiUtils.instance;
import static com.google.inject.multibindings.SpiUtils.providerInstance;
import static com.google.inject.multibindings.SpiUtils.VisitType.BOTH;
import static com.google.inject.multibindings.SpiUtils.VisitType.MODULE;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Maps;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.util.Modules;
import com.google.inject.util.Providers;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author dpb@google.com (David P. Baker)
 */
public class MapBinderTest extends TestCase {

  final TypeLiteral<Map<String, String>> mapOfString = new TypeLiteral<Map<String, String>>() {};
  final TypeLiteral<Map<String, Integer>> mapOfInteger = new TypeLiteral<Map<String, Integer>>() {};
  final TypeLiteral<Map<String, Set<String>>> mapOfSetOfString =
      new TypeLiteral<Map<String, Set<String>>>() {};
      
  private final TypeLiteral<String> stringType = TypeLiteral.get(String.class);
  private final TypeLiteral<Integer> intType = TypeLiteral.get(Integer.class);
  private final TypeLiteral<Set<String>> stringSetType = new TypeLiteral<Set<String>>() {};

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
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(abc, de), BOTH, false, 0,
        instance("a", "A"), instance("b", "B"), instance("c", "C"), instance("d", "D"), instance("e", "E"));
  }

  public void testMapBinderAggregationForAnnotationInstance() {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Names.named("abc"));
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");

        multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Names.named("abc"));
        multibinder.addBinding("c").toInstance("C");
      }
    };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> key = Key.get(mapOfString, Names.named("abc"));
    Map<String, String> abc = injector.getInstance(key);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertMapVisitor(key, stringType, stringType, setOf(module), BOTH, false, 0,
        instance("a", "A"), instance("b", "B"), instance("c", "C"));
  }

  public void testMapBinderAggregationForAnnotationType() {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");

        multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("c").toInstance("C");
      }
    };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> key = Key.get(mapOfString, Abc.class);
    Map<String, String> abc = injector.getInstance(key);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertMapVisitor(key, stringType, stringType, setOf(module), BOTH, false, 0,
        instance("a", "A"), instance("b", "B"), instance("c", "C"));
  }

  public void testMapBinderWithMultipleAnnotationValueSets() {
    Module module = new AbstractModule() {
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
    };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> abcKey = Key.get(mapOfString, named("abc"));
    Map<String, String> abc = injector.getInstance(abcKey);
    Key<Map<String, String>> deKey = Key.get(mapOfString, named("de"));
    Map<String, String> de = injector.getInstance(deKey);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertEquals(mapOf("d", "D", "e", "E"), de);
    assertMapVisitor(abcKey, stringType, stringType, setOf(module), BOTH, false, 1,
        instance("a", "A"), instance("b", "B"), instance("c", "C"));
    assertMapVisitor(deKey, stringType, stringType, setOf(module), BOTH, false, 1,
        instance("d", "D"), instance("e", "E"));     
  }

  public void testMapBinderWithMultipleAnnotationTypeSets() {
    Module module = new AbstractModule() {
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
    };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> abcKey = Key.get(mapOfString, Abc.class);
    Map<String, String> abc = injector.getInstance(abcKey);
    Key<Map<String, String>> deKey = Key.get(mapOfString, De.class);
    Map<String, String> de = injector.getInstance(deKey);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertEquals(mapOf("d", "D", "e", "E"), de);
    assertMapVisitor(abcKey, stringType, stringType, setOf(module), BOTH, false, 1,
        instance("a", "A"), instance("b", "B"), instance("c", "C"));
    assertMapVisitor(deKey, stringType, stringType, setOf(module), BOTH, false, 1,
        instance("d", "D"), instance("e", "E"));
  }

  public void testMapBinderWithMultipleTypes() {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class)
            .addBinding("a").toInstance("A");
        MapBinder.newMapBinder(binder(), String.class, Integer.class)
            .addBinding("1").toInstance(1);
      }
    };
    Injector injector = Guice.createInjector(module);

    assertEquals(mapOf("a", "A"), injector.getInstance(Key.get(mapOfString)));
    assertEquals(mapOf("1", 1), injector.getInstance(Key.get(mapOfInteger)));
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(module), BOTH, false, 1,
        instance("a", "A"));
    assertMapVisitor(Key.get(mapOfInteger), stringType, intType, setOf(module), BOTH, false, 1,
        instance("1", 1));
  }

  public void testMapBinderWithEmptyMap() {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, String.class);
      }
    };
    Injector injector = Guice.createInjector(module);

    Map<String, String> map = injector.getInstance(Key.get(mapOfString));
    assertEquals(Collections.emptyMap(), map);
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(module), BOTH, false, 0);
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
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, Integer.class)
            .addBinding("num").toProvider(new Provider<Integer>() {
          int nextValue = 1;
          public Integer get() {
            return nextValue++;
          }
        });
      }
    };
    Injector injector = Guice.createInjector(module);

    assertEquals(mapOf("num", 1), injector.getInstance(Key.get(mapOfInteger)));
    assertEquals(mapOf("num", 2), injector.getInstance(Key.get(mapOfInteger)));
    assertEquals(mapOf("num", 3), injector.getInstance(Key.get(mapOfInteger)));
    assertMapVisitor(Key.get(mapOfInteger), stringType, intType, setOf(module), BOTH, false, 0,
        providerInstance("num", 1));
  }

  public void testMapBinderMapForbidsDuplicateKeys() {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("a").toInstance("B");
      }
    };
    try {
      Guice.createInjector(module);
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(),
          "Map injection failed due to duplicated key \"a\"");
    }
    
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(module), MODULE, false, 0,
        instance("a", "A"), instance("a", "B"));
  }

  public void testMapBinderMapPermitDuplicateElements() {
    Module ab = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");
      }
    };
    Module bc = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("b").toInstance("B");
        multibinder.addBinding("c").toInstance("C");
        multibinder.permitDuplicates();
      }
    };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(ab, bc), BOTH, true, 0,
        instance("a", "A"), instance("b", "B"), instance("b", "B"), instance("c", "C"));
  }

  public void testMapBinderMultimap() {
    AbstractModule ab1c = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B1");
        multibinder.addBinding("c").toInstance("C");
      }
    };
    AbstractModule b2c = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        multibinder.addBinding("b").toInstance("B2");
        multibinder.addBinding("c").toInstance("C");
        multibinder.permitDuplicates();
      }
    };
    Injector injector = Guice.createInjector(ab1c, b2c);

    assertEquals(mapOf("a", setOf("A"), "b", setOf("B1", "B2"), "c", setOf("C")),
        injector.getInstance(Key.get(mapOfSetOfString)));
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(ab1c, b2c), BOTH, true, 0,
        instance("a", "A"), instance("b", "B1"), instance("b", "B2"), instance("c", "C"), instance("c", "C"));
  }

  public void testMapBinderMultimapWithAnotation() {
    AbstractModule ab1 = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B1");
      }
    };
    AbstractModule b2c = new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(
            binder(), String.class, String.class, Abc.class);
        multibinder.addBinding("b").toInstance("B2");
        multibinder.addBinding("c").toInstance("C");
        multibinder.permitDuplicates();
      }
    };
    Injector injector = Guice.createInjector(ab1, b2c);

    assertEquals(mapOf("a", setOf("A"), "b", setOf("B1", "B2"), "c", setOf("C")),
        injector.getInstance(Key.get(mapOfSetOfString, Abc.class)));
    try {
      injector.getInstance(Key.get(mapOfSetOfString));
      fail();
    } catch (ConfigurationException expected) {}
    
    assertMapVisitor(Key.get(mapOfString, Abc.class), stringType, stringType, setOf(ab1, b2c), BOTH, true, 0,
        instance("a", "A"), instance("b", "B1"), instance("b", "B2"), instance("c", "C"));
  }

  public void testMapBinderMultimapIsUnmodifiable() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        MapBinder<String, String> mapBinder = MapBinder.newMapBinder(
            binder(), String.class, String.class);
        mapBinder.addBinding("a").toInstance("A");
        mapBinder.permitDuplicates();
      }
    });

    Map<String, Set<String>> map = injector.getInstance(Key.get(mapOfSetOfString));
    try {
      map.clear();
      fail();
    } catch(UnsupportedOperationException expected) {
    }
    try {
      map.get("a").clear();
      fail();
    } catch(UnsupportedOperationException expected) {
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

  /** We just want to make sure that mapbinder's binding depends on the underlying multibinder. */
  public void testMultibinderDependenciesInToolStage() {
    Injector injector = Guice.createInjector(Stage.TOOL, new AbstractModule() {
        protected void configure() {
          MapBinder<Integer, String> mapBinder
              = MapBinder.newMapBinder(binder(), Integer.class, String.class);
          mapBinder.addBinding(1).toInstance("A");
          mapBinder.addBinding(2).to(Key.get(String.class, Names.named("b")));
  
          bindConstant().annotatedWith(Names.named("b")).to("B");
        }});

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
  
  /**
   * With overrides, we should get the union of all map bindings.
   */
  public void testModuleOverrideAndMapBindings() {
    Module ab = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");
      }
    };
    Module cd = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("c").toInstance("C");
        multibinder.addBinding("d").toInstance("D");
      }
    };
    Module ef = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("e").toInstance("E");
        multibinder.addBinding("f").toInstance("F");
      }
    };

    Module abcd = Modules.override(ab).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E", "f", "F"),
        injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(abcd, ef), BOTH, false, 0,
        instance("a", "A"), instance("b", "B"), instance("c", "C"), instance("d", "D"), instance(
            "e", "E"), instance("f", "F"));
  }
  
  /**
   * With overrides, we should get the union of all map bindings.
   */
  public void testModuleOverrideAndMapBindingsWithPermitDuplicates() {
    Module abc = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("a").toInstance("A");
        multibinder.addBinding("b").toInstance("B");
        multibinder.addBinding("c").toInstance("C");
        multibinder.permitDuplicates();
      }
    };
    Module cd = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("c").toInstance("C");
        multibinder.addBinding("d").toInstance("D");
        multibinder.permitDuplicates();
      }
    };
    Module ef = new AbstractModule() {
      protected void configure() {
        MapBinder<String, String> multibinder = MapBinder.newMapBinder(binder(), String.class, String.class);
        multibinder.addBinding("e").toInstance("E");
        multibinder.addBinding("f").toInstance("F");
        multibinder.permitDuplicates();
      }
    };

    Module abcd = Modules.override(abc).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E", "f", "F"),
        injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(abcd, ef), BOTH, true, 0,
        instance("a", "A"), instance("b", "B"), instance("c", "C"), instance("c", "C"), instance(
            "d", "D"), instance("e", "E"), instance("f", "F"));

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

  @SuppressWarnings("unchecked")
  private <V> Set<V> setOf(V... elements) {
    return new HashSet(Arrays.asList(elements));
  }

  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  private static @interface Marker {}

  @Marker
  public void testMapBinderMatching() throws Exception {
    Method m = MapBinderTest.class.getDeclaredMethod("testMapBinderMatching");
    assertNotNull(m);
    final Annotation marker = m.getAnnotation(Marker.class);
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override public void configure() {
        MapBinder<Integer, Integer> mb1 =
          MapBinder.newMapBinder(binder(), Integer.class, Integer.class, Marker.class);
        MapBinder<Integer, Integer> mb2 = 
          MapBinder.newMapBinder(binder(), Integer.class, Integer.class, marker);
        mb1.addBinding(1).toInstance(1);
        mb2.addBinding(2).toInstance(2);

        // This assures us that the two binders are equivalent, so we expect the instance added to
        // each to have been added to one set.
        assertEquals(mb1, mb2);
      }
    });
    TypeLiteral<Map<Integer, Integer>> t = new TypeLiteral<Map<Integer, Integer>>() {};
    Map<Integer, Integer> s1 = injector.getInstance(Key.get(t, Marker.class));
    Map<Integer, Integer> s2 = injector.getInstance(Key.get(t, marker));

    // This assures us that the two sets are in fact equal.  They may not be same set (as in Java
    // object identical), but we shouldn't expect that, since probably Guice creates the set each
    // time in case the elements are dependent on scope.
    assertEquals(s1, s2);

    // This ensures that MultiBinder is internally using the correct set name --
    // making sure that instances of marker annotations have the same set name as
    // MarkerAnnotation.class.
    Map<Integer, Integer> expected = new HashMap<Integer, Integer>();
    expected.put(1, 1);
    expected.put(2, 2);
    assertEquals(expected, s1);
  }  
}
