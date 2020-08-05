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

package com.google.inject.internal;

import static com.google.inject.Asserts.asModuleChain;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.internal.SpiUtils.VisitType.BOTH;
import static com.google.inject.internal.SpiUtils.VisitType.MODULE;
import static com.google.inject.internal.SpiUtils.assertMapVisitor;
import static com.google.inject.internal.SpiUtils.instance;
import static com.google.inject.internal.SpiUtils.providerInstance;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Asserts;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.RealMapBinder.ProviderMapEntry;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.MapBinderBinding;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Elements;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.util.Modules;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/** @author dpb@google.com (David P. Baker) */
public class MapBinderTest extends TestCase {

  private static final ImmutableSet<Key<?>> FRAMEWORK_KEYS =
      ImmutableSet.of(
          Key.get(java.util.logging.Logger.class), Key.get(Stage.class), Key.get(Injector.class));

  final TypeLiteral<Map<String, javax.inject.Provider<String>>> mapOfStringJavaxProvider =
      new TypeLiteral<Map<String, javax.inject.Provider<String>>>() {};
  final TypeLiteral<Map<String, Provider<String>>> mapOfStringProvider =
      new TypeLiteral<Map<String, Provider<String>>>() {};
  final TypeLiteral<Map<String, String>> mapOfString = new TypeLiteral<Map<String, String>>() {};
  final TypeLiteral<Map<Integer, String>> mapOfIntString =
      new TypeLiteral<Map<Integer, String>>() {};
  final TypeLiteral<Map<String, Integer>> mapOfInteger = new TypeLiteral<Map<String, Integer>>() {};
  final TypeLiteral<Map<String, Set<String>>> mapOfSetOfString =
      new TypeLiteral<Map<String, Set<String>>>() {};

  private final TypeLiteral<String> stringType = TypeLiteral.get(String.class);
  private final TypeLiteral<Integer> intType = TypeLiteral.get(Integer.class);

  private Type javaxProviderOf(Type type) {
    return Types.javaxProviderOf(type);
  }

  private Type mapEntryOf(Type keyType, Type valueType) {
    return Types.newParameterizedTypeWithOwner(Map.class, Map.Entry.class, keyType, valueType);
  }

  private Type collectionOf(Type type) {
    return Types.newParameterizedType(Collection.class, type);
  }

  public void testAllBindings() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, String.class).permitDuplicates();
          }
        };

    Injector injector = Guice.createInjector(module);

    Map<Key<?>, Binding<?>> bindings = injector.getBindings();

    ImmutableSet<Key<?>> expectedBindings =
        ImmutableSet.<Key<?>>builder()
            .add(
                // Map<K, V>
                Key.get(Types.mapOf(String.class, String.class)),
                // Map<K, Provider<V>>
                Key.get(Types.mapOf(String.class, Types.providerOf(String.class))),
                // Map<K, javax.inject.Provider<V>>
                Key.get(Types.mapOf(String.class, javaxProviderOf(String.class))),
                // Map<K, Set<V>>
                Key.get(Types.mapOf(String.class, Types.setOf(String.class))),
                // Map<K, Set<Provider<V>>
                Key.get(Types.mapOf(String.class, Types.setOf(Types.providerOf(String.class)))),
                // Map<K, Set<javax.inject.Provider<V>>
                Key.get(
                    Types.mapOf(String.class, Types.setOf(Types.javaxProviderOf(String.class)))),
                // Map<K, Collection<Provider<V>>
                Key.get(
                    Types.mapOf(String.class, Types.collectionOf(Types.providerOf(String.class)))),
                // Map<K, Collection<javax.inject.Provider<V>>
                Key.get(
                    Types.mapOf(
                        String.class, Types.collectionOf(Types.javaxProviderOf(String.class)))),
                // Set<Map.Entry<K, Provider<V>>>
                Key.get(Types.setOf(mapEntryOf(String.class, Types.providerOf(String.class)))),
                // Set<Map.Entry<K, javax.inject.Provider<V>>>
                Key.get(Types.setOf(mapEntryOf(String.class, Types.javaxProviderOf(String.class)))),
                // Collection<Provider<Map.Entry<K, Provider<V>>>>
                Key.get(
                    collectionOf(
                        Types.providerOf(
                            mapEntryOf(String.class, Types.providerOf(String.class))))),
                // Collection<javax.inject.Provider<Map.Entry<K, Provider<V>>>>
                Key.get(
                    collectionOf(
                        Types.javaxProviderOf(
                            mapEntryOf(String.class, Types.providerOf(String.class))))),
                // Set<? extends Map.Entry<K, Provider<V>>>
                Key.get(
                    Types.setOf(
                        Types.subtypeOf(mapEntryOf(String.class, Types.providerOf(String.class))))),
                // @Named(...) Boolean
                Key.get(
                    Boolean.class,
                    named(
                        "Multibinder<java.util.Map$Entry<java.lang.String, "
                            + "com.google.inject.Provider<java.lang.String>>> permits duplicates")),
                // Map<K, ? extends V>
                Key.get(Types.mapOf(String.class, Types.subtypeOf(String.class))))
            .addAll(FRAMEWORK_KEYS)
            .build();

    Set<Key<?>> missingBindings = Sets.difference(expectedBindings, bindings.keySet());
    Set<Key<?>> extraBindings = Sets.difference(bindings.keySet(), expectedBindings);

    assertTrue(
        "There should be no missing bindings. Missing: " + missingBindings,
        missingBindings.isEmpty());
    assertTrue(
        "There should be no extra bindings. Extra: " + extraBindings, extraBindings.isEmpty());
  }

  public void testMapBinderAggregatesMultipleModules() {
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
            multibinder.addBinding("c").toInstance("C");
          }
        };
    Module de =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("d").toInstance("D");
            multibinder.addBinding("e").toInstance("E");
          }
        };

    Injector injector = Guice.createInjector(abc, de);
    Map<String, String> abcde = injector.getInstance(Key.get(mapOfString));

    assertEquals(mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E"), abcde);
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(abc, de),
        BOTH,
        false,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"),
        instance("d", "D"),
        instance("e", "E"));

    // just make sure these succeed
    injector.getInstance(Key.get(mapOfStringProvider));
    injector.getInstance(Key.get(mapOfStringJavaxProvider));
  }

  public void testMapBinderAggregationForAnnotationInstance() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Names.named("abc"));
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");

            multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Names.named("abc"));
            multibinder.addBinding("c").toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> key = Key.get(mapOfString, Names.named("abc"));
    Map<String, String> abc = injector.getInstance(key);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertMapVisitor(
        key,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"));

    // just make sure these succeed
    injector.getInstance(Key.get(mapOfStringProvider, Names.named("abc")));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, Names.named("abc")));
  }

  public void testMapBinderAggregationForAnnotationType() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Abc.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");

            multibinder = MapBinder.newMapBinder(binder(), String.class, String.class, Abc.class);
            multibinder.addBinding("c").toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Map<String, String>> key = Key.get(mapOfString, Abc.class);
    Map<String, String> abc = injector.getInstance(key);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), abc);
    assertMapVisitor(
        key,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"));

    // just make sure these succeed
    injector.getInstance(Key.get(mapOfStringProvider, Abc.class));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, Abc.class));
  }

  public void testMapBinderWithMultipleAnnotationValueSets() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> abcMapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, named("abc"));
            abcMapBinder.addBinding("a").toInstance("A");
            abcMapBinder.addBinding("b").toInstance("B");
            abcMapBinder.addBinding("c").toInstance("C");

            MapBinder<String, String> deMapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, named("de"));
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
    assertMapVisitor(
        abcKey,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"));
    assertMapVisitor(
        deKey,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("d", "D"),
        instance("e", "E"));

    // just make sure these succeed
    injector.getInstance(Key.get(mapOfStringProvider, named("abc")));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, named("abc")));
    injector.getInstance(Key.get(mapOfStringProvider, named("de")));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, named("de")));
  }

  public void testMapBinderWithMultipleAnnotationTypeSets() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> abcMapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Abc.class);
            abcMapBinder.addBinding("a").toInstance("A");
            abcMapBinder.addBinding("b").toInstance("B");
            abcMapBinder.addBinding("c").toInstance("C");

            MapBinder<String, String> deMapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, De.class);
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
    assertMapVisitor(
        abcKey,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"));
    assertMapVisitor(
        deKey,
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("d", "D"),
        instance("e", "E"));

    // just make sure these succeed
    injector.getInstance(Key.get(mapOfStringProvider, Abc.class));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, Abc.class));
    injector.getInstance(Key.get(mapOfStringProvider, De.class));
    injector.getInstance(Key.get(mapOfStringJavaxProvider, De.class));
  }

  public void testMapBinderWithMultipleTypes() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, String.class)
                .addBinding("a")
                .toInstance("A");
            MapBinder.newMapBinder(binder(), String.class, Integer.class)
                .addBinding("1")
                .toInstance(1);
          }
        };
    Injector injector = Guice.createInjector(module);

    assertEquals(mapOf("a", "A"), injector.getInstance(Key.get(mapOfString)));
    assertEquals(mapOf("1", 1), injector.getInstance(Key.get(mapOfInteger)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("a", "A"));
    assertMapVisitor(
        Key.get(mapOfInteger),
        stringType,
        intType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("1", 1));
  }

  public void testMapBinderWithEmptyMap() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, String.class);
          }
        };
    Injector injector = Guice.createInjector(module);

    Map<String, String> map = injector.getInstance(Key.get(mapOfString));
    assertEquals(Collections.emptyMap(), map);
    assertMapVisitor(Key.get(mapOfString), stringType, stringType, setOf(module), BOTH, false, 0);
  }

  public void testMapBinderMapIsUnmodifiable() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("a")
                    .toInstance("A");
              }
            });

    Map<String, String> map = injector.getInstance(Key.get(mapOfString));
    try {
      map.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  public void testMapBinderMapIsLazy() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, Integer.class)
                .addBinding("num")
                .toProvider(
                    new Provider<Integer>() {
                      int nextValue = 1;

                      @Override
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
    assertMapVisitor(
        Key.get(mapOfInteger),
        stringType,
        intType,
        setOf(module),
        BOTH,
        false,
        0,
        providerInstance("num", 1));
  }

  public void testMapBinderMapForbidsDuplicateKeys() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("a").toInstance("B");
          }
        };
    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Map injection failed due to duplicated key \"a\"");
    }

    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(module),
        MODULE,
        false,
        0,
        instance("a", "A"),
        instance("a", "B"));
  }

  public void testExhaustiveDuplicateErrorMessage() throws Exception {
    class Module1 extends AbstractModule {
      @Override
      protected void configure() {
        MapBinder<String, Object> mapbinder =
            MapBinder.newMapBinder(binder(), String.class, Object.class);
        mapbinder.addBinding("a").to(String.class);
      }
    }
    class Module2 extends AbstractModule {
      @Override
      protected void configure() {
        MapBinder<String, Object> mapbinder =
            MapBinder.newMapBinder(binder(), String.class, Object.class);
        mapbinder.addBinding("a").to(Integer.class);
        mapbinder.addBinding("b").to(String.class);
      }
    }
    class Module3 extends AbstractModule {
      @Override
      protected void configure() {
        MapBinder<String, Object> mapbinder =
            MapBinder.newMapBinder(binder(), String.class, Object.class);
        mapbinder.addBinding("b").to(Integer.class);
      }
    }
    class Main extends AbstractModule {
      @Override
      protected void configure() {
        MapBinder.newMapBinder(binder(), String.class, Object.class);
        install(new Module1());
        install(new Module2());
        install(new Module3());
      }

      @Provides
      String provideString() {
        return "foo";
      }

      @Provides
      Integer provideInt() {
        return 42;
      }
    }
    try {
      Guice.createInjector(new Main());
      fail();
    } catch (CreationException ce) {
      assertContains(
          ce.getMessage(),
          "Map injection failed due to duplicated key \"a\", from bindings:",
          asModuleChain(Main.class, Module1.class),
          asModuleChain(Main.class, Module2.class),
          "and key: \"b\", from bindings:",
          asModuleChain(Main.class, Module2.class),
          asModuleChain(Main.class, Module3.class),
          "at " + Main.class.getName() + ".configure(",
          asModuleChain(Main.class, RealMapBinder.class));
      assertEquals(1, ce.getErrorMessages().size());
    }
  }

  public void testMapBinderMapPermitDuplicateElements() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
            multibinder.permitDuplicates();
          }
        };
    Module bc =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("b").toInstance("B");
            multibinder.addBinding("c").toInstance("C");
            multibinder.permitDuplicates();
          }
        };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(ab, bc),
        BOTH,
        true,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"));
  }

  public void testMapBinderMapDoesNotDedupeDuplicateValues() {
    class ValueType {
      int keyPart;
      int dataPart;

      private ValueType(int keyPart, int dataPart) {
        this.keyPart = keyPart;
        this.dataPart = dataPart;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof ValueType) && (keyPart == ((ValueType) obj).keyPart);
      }

      @Override
      public int hashCode() {
        return keyPart;
      }
    }
    Module m1 =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, ValueType> multibinder =
                MapBinder.newMapBinder(binder(), String.class, ValueType.class);
            multibinder.addBinding("a").toInstance(new ValueType(1, 2));
          }
        };
    Module m2 =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, ValueType> multibinder =
                MapBinder.newMapBinder(binder(), String.class, ValueType.class);
            multibinder.addBinding("b").toInstance(new ValueType(1, 3));
          }
        };

    Injector injector = Guice.createInjector(m1, m2);
    Map<String, ValueType> map = injector.getInstance(new Key<Map<String, ValueType>>() {});
    assertEquals(2, map.get("a").dataPart);
    assertEquals(3, map.get("b").dataPart);
  }

  public void testMapBinderMultimap() {
    AbstractModule ab1c =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B1");
            multibinder.addBinding("c").toInstance("C");
          }
        };
    AbstractModule b2c =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("b").toInstance("B2");
            multibinder.addBinding("c").toInstance("C");
            multibinder.permitDuplicates();
          }
        };
    Injector injector = Guice.createInjector(ab1c, b2c);

    assertEquals(
        mapOf("a", setOf("A"), "b", setOf("B1", "B2"), "c", setOf("C")),
        injector.getInstance(Key.get(mapOfSetOfString)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(ab1c, b2c),
        BOTH,
        true,
        0,
        instance("a", "A"),
        instance("b", "B1"),
        instance("b", "B2"),
        instance("c", "C"));
  }

  public void testMapBinderMultimapWithAnotation() {
    AbstractModule ab1 =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Abc.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B1");
          }
        };
    AbstractModule b2c =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class, Abc.class);
            multibinder.addBinding("b").toInstance("B2");
            multibinder.addBinding("c").toInstance("C");
            multibinder.permitDuplicates();
          }
        };
    Injector injector = Guice.createInjector(ab1, b2c);

    assertEquals(
        mapOf("a", setOf("A"), "b", setOf("B1", "B2"), "c", setOf("C")),
        injector.getInstance(Key.get(mapOfSetOfString, Abc.class)));
    try {
      injector.getInstance(Key.get(mapOfSetOfString));
      fail();
    } catch (ConfigurationException expected) {
    }

    assertMapVisitor(
        Key.get(mapOfString, Abc.class),
        stringType,
        stringType,
        setOf(ab1, b2c),
        BOTH,
        true,
        0,
        instance("a", "A"),
        instance("b", "B1"),
        instance("b", "B2"),
        instance("c", "C"));
  }

  public void testMapBinderMultimapIsUnmodifiable() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder<String, String> mapBinder =
                    MapBinder.newMapBinder(binder(), String.class, String.class);
                mapBinder.addBinding("a").toInstance("A");
                mapBinder.permitDuplicates();
              }
            });

    Map<String, Set<String>> map = injector.getInstance(Key.get(mapOfSetOfString));
    try {
      map.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
    try {
      map.get("a").clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  public void testMapBinderMapForbidsNullKeys() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              MapBinder.newMapBinder(binder(), String.class, String.class).addBinding(null);
            }
          });
      fail();
    } catch (CreationException expected) {
    }
  }

  public void testMapBinderMapForbidsNullValues() {
    Module m =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder.newMapBinder(binder(), String.class, String.class)
                .addBinding("null")
                .toProvider(Providers.<String>of(null));
          }
        };
    Injector injector = Guice.createInjector(m);

    try {
      injector.getInstance(Key.get(mapOfString));
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) Map injection failed due to null value for key \"null\", bound at: "
              + m.getClass().getName()
              + ".configure(");
    }
  }

  public void testMapBinderProviderIsScoped() {
    final Provider<Integer> counter =
        new Provider<Integer>() {
          int next = 1;

          @Override
          public Integer get() {
            return next++;
          }
        };

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder.newMapBinder(binder(), String.class, Integer.class)
                    .addBinding("one")
                    .toProvider(counter)
                    .asEagerSingleton();
              }
            });

    assertEquals(1, (int) injector.getInstance(Key.get(mapOfInteger)).get("one"));
    assertEquals(1, (int) injector.getInstance(Key.get(mapOfInteger)).get("one"));
  }

  public void testSourceLinesInMapBindings() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              MapBinder.newMapBinder(binder(), String.class, Integer.class).addBinding("one");
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) No implementation for java.lang.Integer",
          "at " + getClass().getName());
    }
  }

  /** Check that the dependencies are correct. */
  public void testMultibinderDependencies() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder<Integer, String> mapBinder =
                    MapBinder.newMapBinder(binder(), Integer.class, String.class);
                mapBinder.addBinding(1).toInstance("A");
                mapBinder.addBinding(2).to(Key.get(String.class, Names.named("b")));

                bindConstant().annotatedWith(Names.named("b")).to("B");
              }
            });

    Binding<Map<Integer, String>> binding = injector.getBinding(new Key<Map<Integer, String>>() {});
    HasDependencies withDependencies = (HasDependencies) binding;
    Set<Dependency<?>> actualDependencies = withDependencies.getDependencies();

    // We expect two dependencies, because the dependencies are annotated with
    // Element, which has a uniqueId, it's difficult to directly compare them.
    // Instead we will manually compare all the fields except the uniqueId
    assertEquals(2, actualDependencies.size());
    for (Dependency<?> dependency : actualDependencies) {
      Key<?> key = dependency.getKey();
      assertEquals(new TypeLiteral<String>() {}, key.getTypeLiteral());
      Annotation annotation = dependency.getKey().getAnnotation();
      assertTrue(annotation instanceof Element);
      Element element = (Element) annotation;
      assertEquals("", element.setName());
      assertEquals(Element.Type.MAPBINDER, element.type());
      assertEquals("java.lang.Integer", element.keyType());
    }

    Set<String> elements = Sets.newHashSet();
    elements.addAll(recurseForDependencies(injector, withDependencies));
    assertEquals(ImmutableSet.of("A", "B"), elements);
  }

  private Set<String> recurseForDependencies(Injector injector, HasDependencies hasDependencies) {
    Set<String> elements = Sets.newHashSet();
    for (Dependency<?> dependency : hasDependencies.getDependencies()) {
      Binding<?> binding = injector.getBinding(dependency.getKey());
      HasDependencies deps = (HasDependencies) binding;
      if (binding instanceof InstanceBinding) {
        elements.add((String) ((InstanceBinding<?>) binding).getInstance());
      } else {
        elements.addAll(recurseForDependencies(injector, deps));
      }
    }
    return elements;
  }

  /** Check that the dependencies are correct in the Tool Stage. */
  public void testMultibinderDependenciesInToolStage() {
    Injector injector =
        Guice.createInjector(
            Stage.TOOL,
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder<Integer, String> mapBinder =
                    MapBinder.newMapBinder(binder(), Integer.class, String.class);
                mapBinder.addBinding(1).toInstance("A");
                mapBinder.addBinding(2).to(Key.get(String.class, Names.named("b")));

                bindConstant().annotatedWith(Names.named("b")).to("B");
              }
            });

    Binding<Map<Integer, String>> binding = injector.getBinding(new Key<Map<Integer, String>>() {});
    HasDependencies withDependencies = (HasDependencies) binding;
    Set<Dependency<?>> actualDependencies = withDependencies.getDependencies();

    // We expect two dependencies, because the dependencies are annotated with
    // Element, which has a uniqueId, it's difficult to directly compare them.
    // Instead we will manually compare all the fields except the uniqueId
    assertEquals(2, actualDependencies.size());
    for (Dependency<?> dependency : actualDependencies) {
      Key<?> key = dependency.getKey();
      assertEquals(new TypeLiteral<String>() {}, key.getTypeLiteral());
      Annotation annotation = dependency.getKey().getAnnotation();
      assertTrue(annotation instanceof Element);
      Element element = (Element) annotation;
      assertEquals("", element.setName());
      assertEquals(Element.Type.MAPBINDER, element.type());
      assertEquals("java.lang.Integer", element.keyType());
    }
  }

  /** Our implementation maintains order, but doesn't guarantee it in the API spec. */
  // TODO: specify the iteration order
  public void testBindOrderEqualsIterationOrder() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder<String, String> mapBinder =
                    MapBinder.newMapBinder(binder(), String.class, String.class);
                mapBinder.addBinding("leonardo").toInstance("blue");
                mapBinder.addBinding("donatello").toInstance("purple");
                install(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        MapBinder.newMapBinder(binder(), String.class, String.class)
                            .addBinding("michaelangelo")
                            .toInstance("orange");
                      }
                    });
              }
            },
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("raphael")
                    .toInstance("red");
              }
            });

    Map<String, String> map = injector.getInstance(new Key<Map<String, String>>() {});
    Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
    assertEquals(Maps.immutableEntry("leonardo", "blue"), iterator.next());
    assertEquals(Maps.immutableEntry("donatello", "purple"), iterator.next());
    assertEquals(Maps.immutableEntry("michaelangelo", "orange"), iterator.next());
    assertEquals(Maps.immutableEntry("raphael", "red"), iterator.next());
  }

  /** With overrides, we should get the union of all map bindings. */
  public void testModuleOverrideAndMapBindings() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
          }
        };
    Module cd =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("c").toInstance("C");
            multibinder.addBinding("d").toInstance("D");
          }
        };
    Module ef =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("e").toInstance("E");
            multibinder.addBinding("f").toInstance("F");
          }
        };

    Module abcd = Modules.override(ab).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(
        mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E", "f", "F"),
        injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(abcd, ef),
        BOTH,
        false,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"),
        instance("d", "D"),
        instance("e", "E"),
        instance("f", "F"));
  }

  public void testDeduplicateMapBindings() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> mapbinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            mapbinder.addBinding("a").toInstance("A");
            mapbinder.addBinding("a").toInstance("A");
            mapbinder.addBinding("b").toInstance("B");
            mapbinder.addBinding("b").toInstance("B");
          }
        };
    Injector injector = Guice.createInjector(module);
    assertEquals(mapOf("a", "A", "b", "B"), injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(module),
        BOTH,
        false,
        0,
        instance("a", "A"),
        instance("b", "B"));
  }

  /** With overrides, we should get the union of all map bindings. */
  public void testModuleOverrideAndMapBindingsWithPermitDuplicates() {
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
            multibinder.addBinding("c").toInstance("C");
            multibinder.permitDuplicates();
          }
        };
    Module cd =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("c").toInstance("C");
            multibinder.addBinding("d").toInstance("D");
            multibinder.permitDuplicates();
          }
        };
    Module ef =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("e").toInstance("E");
            multibinder.addBinding("f").toInstance("F");
            multibinder.permitDuplicates();
          }
        };

    Module abcd = Modules.override(abc).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(
        mapOf("a", "A", "b", "B", "c", "C", "d", "D", "e", "E", "f", "F"),
        injector.getInstance(Key.get(mapOfString)));
    assertMapVisitor(
        Key.get(mapOfString),
        stringType,
        stringType,
        setOf(abcd, ef),
        BOTH,
        true,
        0,
        instance("a", "A"),
        instance("b", "B"),
        instance("c", "C"),
        instance("d", "D"),
        instance("e", "E"),
        instance("f", "F"));
  }

  /** Ensure there are no initialization race conditions in basic map injection. */
  public void testBasicMapDependencyInjection() {
    final AtomicReference<Map<String, String>> injectedMap =
        new AtomicReference<Map<String, String>>();
    final Object anObject =
        new Object() {
          @Inject
          void initialize(Map<String, String> map) {
            injectedMap.set(map);
          }
        };
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            requestInjection(anObject);
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
            multibinder.addBinding("c").toInstance("C");
          }
        };
    Guice.createInjector(abc);
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), injectedMap.get());
  }

  /** Ensure there are no initialization race conditions in provider multimap injection. */
  public void testProviderMultimapDependencyInjection() {
    final AtomicReference<Map<String, Set<Provider<String>>>> injectedMultimap =
        new AtomicReference<Map<String, Set<Provider<String>>>>();
    final Object anObject =
        new Object() {
          @Inject
          void initialize(Map<String, Set<Provider<String>>> multimap) {
            injectedMultimap.set(multimap);
          }
        };
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            requestInjection(anObject);
            MapBinder<String, String> multibinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            multibinder.permitDuplicates();
            multibinder.addBinding("a").toInstance("A");
            multibinder.addBinding("b").toInstance("B");
            multibinder.addBinding("c").toInstance("C");
          }
        };
    Guice.createInjector(abc);
    Map<String, String> map =
        Maps.transformValues(
            injectedMultimap.get(),
            stringProvidersSet -> Iterables.getOnlyElement(stringProvidersSet).get());
    assertEquals(mapOf("a", "A", "b", "B", "c", "C"), map);
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Abc {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface De {}

  @SuppressWarnings("unchecked")
  private <K, V> Map<K, V> mapOf(Object... elements) {
    Map<K, V> result = new HashMap<>();
    for (int i = 0; i < elements.length; i += 2) {
      result.put((K) elements[i], (V) elements[i + 1]);
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private <V> Set<V> setOf(V... elements) {
    return new HashSet<V>(Arrays.asList(elements));
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
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              public void configure() {
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
    Map<Integer, Integer> expected = new HashMap<>();
    expected.put(1, 1);
    expected.put(2, 2);
    assertEquals(expected, s1);
  }

  public void testTwoMapBindersAreDistinct() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("A")
                    .toInstance("a");

                MapBinder.newMapBinder(binder(), Integer.class, String.class)
                    .addBinding(1)
                    .toInstance("b");
              }
            });
    Collector collector = new Collector();
    Binding<Map<String, String>> map1 = injector.getBinding(Key.get(mapOfString));
    map1.acceptTargetVisitor(collector);
    assertNotNull(collector.mapbinding);
    MapBinderBinding<?> map1Binding = collector.mapbinding;

    Binding<Map<Integer, String>> map2 = injector.getBinding(Key.get(mapOfIntString));
    map2.acceptTargetVisitor(collector);
    assertNotNull(collector.mapbinding);
    MapBinderBinding<?> map2Binding = collector.mapbinding;

    List<Binding<String>> bindings = injector.findBindingsByType(stringType);
    assertEquals("should have two elements: " + bindings, 2, bindings.size());
    Binding<String> a = bindings.get(0);
    Binding<String> b = bindings.get(1);
    assertEquals("a", ((InstanceBinding<String>) a).getInstance());
    assertEquals("b", ((InstanceBinding<String>) b).getInstance());

    // Make sure the correct elements belong to their own sets.
    assertTrue(map1Binding.containsElement(a));
    assertFalse(map1Binding.containsElement(b));

    assertFalse(map2Binding.containsElement(a));
    assertTrue(map2Binding.containsElement(b));
  }

  // Tests for com.google.inject.internal.WeakKeySet not leaking memory.
  public void testWeakKeySet_integration_mapbinder() {
    Key<Map<String, String>> mapKey = Key.get(new TypeLiteral<Map<String, String>>() {});

    Injector parentInjector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("hi");
              }
            });
    WeakKeySetUtils.assertNotBlacklisted(parentInjector, mapKey);

    Injector childInjector =
        parentInjector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                MapBinder<String, String> binder =
                    MapBinder.newMapBinder(binder(), String.class, String.class);
                binder.addBinding("bar").toInstance("foo");
              }
            });
    WeakReference<Injector> weakRef = new WeakReference<>(childInjector);
    WeakKeySetUtils.assertBlacklisted(parentInjector, mapKey);

    // Clear the ref, GC, and ensure that we are no longer blacklisting.
    childInjector = null;

    Asserts.awaitClear(weakRef);
    WeakKeySetUtils.assertNotBlacklisted(parentInjector, mapKey);
  }

  @SuppressWarnings("rawtypes")
  public void testGetEntries() {
    List<com.google.inject.spi.Element> elements =
        Elements.getElements(new MapBinderWithTwoEntriesModule());

    // Get the MapBinderBinding
    MapBinderBinding<?> mapBinderBinding = getMapBinderBinding(elements);

    // Execute the call to getEntries
    List<Map.Entry<?, Binding<?>>> mapEntries = mapBinderBinding.getEntries(elements);

    // Assert on the results
    Map.Entry<?, Binding<?>> firstEntry = mapEntries.get(0);
    assertEquals("keyOne", firstEntry.getKey());
    Binding<?> firstBinding = firstEntry.getValue();
    assertEquals("valueOne", ((InstanceBinding) firstBinding).getInstance());

    Map.Entry<?, Binding<?>> secondEntry = mapEntries.get(1);
    assertEquals("keyTwo", secondEntry.getKey());
    Binding<?> secondBinding = secondEntry.getValue();
    assertEquals("valueTwo", ((InstanceBinding) secondBinding).getInstance());
  }

  @SuppressWarnings("rawtypes")
  public void testGetEntriesWithDuplicateKeys() {
    // Set up the module
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> mapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            mapBinder.addBinding("A").toInstance("a1");
            mapBinder.addBinding("A").toInstance("a2");
            mapBinder.permitDuplicates();
          }
        };

    // Get the MapBinderBinding
    List<com.google.inject.spi.Element> elements = Elements.getElements(module);
    MapBinderBinding<?> mapBinderBinding = getMapBinderBinding(elements);

    // Execute the call to getEntries
    List<Map.Entry<?, Binding<?>>> mapEntries = mapBinderBinding.getEntries(elements);

    // Assert on the results
    Map.Entry<?, Binding<?>> firstEntry = mapEntries.get(0);
    assertEquals("A", firstEntry.getKey());
    Binding<?> firstBinding = firstEntry.getValue();
    assertEquals("a1", ((InstanceBinding) firstBinding).getInstance());

    Map.Entry<?, Binding<?>> secondEntry = mapEntries.get(1);
    assertEquals("A", secondEntry.getKey());
    Binding<?> secondBinding = secondEntry.getValue();
    assertEquals("a2", ((InstanceBinding) secondBinding).getInstance());
  }

  @SuppressWarnings("rawtypes")
  public void testGetEntriesWithDuplicateValues() {
    // Set up the module
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<String, String> mapBinder =
                MapBinder.newMapBinder(binder(), String.class, String.class);
            mapBinder.addBinding("A").toInstance("a");
            mapBinder.addBinding("A").toInstance("a");
          }
        };

    // Get the MapBinderBinding
    List<com.google.inject.spi.Element> elements = Elements.getElements(module);
    MapBinderBinding<?> mapBinderBinding = getMapBinderBinding(elements);

    // Execute the call to getEntries
    List<Map.Entry<?, Binding<?>>> mapEntries = mapBinderBinding.getEntries(elements);

    // Assert on the results
    Map.Entry<?, Binding<?>> firstEntry = mapEntries.get(0);
    assertEquals("A", firstEntry.getKey());
    Binding<?> firstBinding = firstEntry.getValue();
    assertEquals("a", ((InstanceBinding) firstBinding).getInstance());

    Map.Entry<?, Binding<?>> secondEntry = mapEntries.get(1);
    assertEquals("A", secondEntry.getKey());
    Binding<?> secondBinding = secondEntry.getValue();
    assertEquals("a", ((InstanceBinding) secondBinding).getInstance());
  }

  @SuppressWarnings("rawtypes")
  public void testGetEntriesMissingProviderMapEntry() {
    List<com.google.inject.spi.Element> elements =
        Lists.newArrayList(Elements.getElements(new MapBinderWithTwoEntriesModule()));

    // Get the MapBinderBinding
    MapBinderBinding<?> mapBinderBinding = getMapBinderBinding(elements);

    // Remove the ProviderMapEntry for "a" from the elements
    com.google.inject.spi.Element providerMapEntryForA = getProviderMapEntry("keyOne", elements);
    boolean removeSuccessful = elements.remove(providerMapEntryForA);
    assertTrue(removeSuccessful);

    // Execute the call to getEntries, we expect it to fail
    try {
      mapBinderBinding.getEntries(elements);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(
          expected.getMessage(),
          "Expected a 1:1 mapping from map keys to values.",
          "Found these Bindings that were missing an associated entry:",
          "java.lang.String",
          "bound at:",
          "MapBinderWithTwoEntriesModule");
    }
  }

  /**
   * Will find and return the {@link com.google.inject.spi.Element} that is a {@link
   * ProviderMapEntry} with a key that matches the one supplied by the user in {@code k}.
   *
   * <p>Will return {@code null} if it cannot be found.
   */
  private static com.google.inject.spi.Element getProviderMapEntry(
      Object kToFind, Iterable<com.google.inject.spi.Element> elements) {
    for (com.google.inject.spi.Element element : elements) {
      if (element instanceof ProviderInstanceBinding) {
        javax.inject.Provider<?> usp =
            ((ProviderInstanceBinding<?>) element).getUserSuppliedProvider();
        if (usp instanceof ProviderMapEntry) {
          ProviderMapEntry<?, ?> pme = (ProviderMapEntry<?, ?>) usp;

          // Check if the key from the ProviderMapEntry matches the one we're looking for
          if (kToFind.equals(pme.getKey())) {
            return element;
          }
        }
      }
    }
    // No matching ProviderMapEntry found
    return null;
  }

  @SuppressWarnings("rawtypes")
  public void testGetEntriesMissingBindingForValue() {
    List<com.google.inject.spi.Element> elements =
        Lists.newArrayList(Elements.getElements(new MapBinderWithTwoEntriesModule()));

    // Get the MapBinderBinding
    MapBinderBinding<?> mapBinderBinding = getMapBinderBinding(elements);

    // Remove the ProviderMapEntry for "a" from the elements
    com.google.inject.spi.Element bindingForA = getInstanceBindingForValue("valueOne", elements);
    boolean removeSuccessful = elements.remove(bindingForA);
    assertTrue(removeSuccessful);

    // Execute the call to getEntries, we expect it to fail
    try {
      mapBinderBinding.getEntries(elements);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(
          expected.getMessage(),
          "Expected a 1:1 mapping from map keys to values.",
          "Found these map keys without a corresponding value:",
          "keyOne",
          "bound at:",
          "MapBinderWithTwoEntriesModule");
    }
  }

  public void testMapBinderWildcardsAlias() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<Integer, String> mapBinder =
                MapBinder.newMapBinder(binder(), Integer.class, String.class);
            mapBinder.addBinding(1).toInstance("1");
            mapBinder.addBinding(2).toInstance("2");
          }
        };
    Injector injector = Guice.createInjector(module);

    Map<Integer, String> expectedMap = ImmutableMap.of(1, "1", 2, "2");
    assertEquals(expectedMap, injector.getInstance(new Key<Map<Integer, String>>() {}));
    assertEquals(expectedMap, injector.getInstance(new Key<Map<Integer, ? extends String>>() {}));
  }

  /**
   * Injection of {@code Map<K, ? extends V>} wasn't added until 2020-07. It's possible that
   * applications already have a binding to that type. If they do, confirm that Guice fails fast
   * with a duplicate binding error.
   */
  public void testMapBinderConflictsWithExistingWildcard() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<Integer, String> mapBinder =
                MapBinder.newMapBinder(binder(), Integer.class, String.class);
            mapBinder.addBinding(1).toInstance("1");
            mapBinder.addBinding(2).toInstance("2");
          }

          @Provides
          protected Map<Integer, ? extends String> provideMap() {
            return ImmutableMap.of(1, "1", 2, "2");
          }
        };
    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "A binding to java.util.Map<java.lang.Integer, ? extends java.lang.String> was"
                      + " already configured"));
    }
  }

  /**
   * This is the same as the previous test, but it gets at the conflicting set through a MapBinder
   * rather than through a regular binding. It's unlikely that application developers would do this
   * in practice, but if they do we want to make sure it is detected and fails fast.
   */
  public void testMapBinderConflictsWithExistingMapBinder() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            MapBinder<Integer, String> mapBinder =
                MapBinder.newMapBinder(binder(), Integer.class, String.class);
            mapBinder.addBinding(1).toInstance("1");
            mapBinder.addBinding(2).toInstance("2");

            // Cast TypeLiteral<? extends String> to TypeLiteral<String> so the test can add
            // bindings below (i.e. it would be an error to add bindings if the MapBinder were an
            // MapBinder<Integer, ? extends String>).
            @SuppressWarnings("unchecked") // see above comment
            TypeLiteral<String> valueType =
                (TypeLiteral<String>) TypeLiteral.get(Types.subtypeOf(String.class));
            MapBinder<Integer, String> mapBinder2 =
                MapBinder.newMapBinder(binder(), TypeLiteral.get(Integer.class), valueType);
            mapBinder2.addBinding(1).toInstance("1");
            mapBinder2.addBinding(2).toInstance("2");
          }
        };
    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "A binding to java.util.Map<java.lang.Integer, ? extends java.lang.String> was"
                      + " already configured"));
    }
  }

  /**
   * Will find and return the {@link com.google.inject.spi.Element} that is an {@link
   * InstanceBinding} and binds {@code vToFind}.
   */
  private static com.google.inject.spi.Element getInstanceBindingForValue(
      Object vToFind, Iterable<com.google.inject.spi.Element> elements) {
    for (com.google.inject.spi.Element element : elements) {
      if (element instanceof InstanceBinding) {
        Object instanceFromBinding = ((InstanceBinding<?>) element).getInstance();
        if (vToFind.equals(instanceFromBinding)) {
          return element;
        }
      }
    }
    // No matching binding found
    return null;
  }

  /** A simple module with a MapBinder with two entries. */
  private static final class MapBinderWithTwoEntriesModule extends AbstractModule {
    @Override
    protected void configure() {
      MapBinder<String, String> mapBinder =
          MapBinder.newMapBinder(binder(), String.class, String.class);
      mapBinder.addBinding("keyOne").toInstance("valueOne");
      mapBinder.addBinding("keyTwo").toInstance("valueTwo");
    }
  }

  /**
   * Given an {@link Iterable} of elements, return the one that is a {@link MapBinderBinding}, or
   * {@code null} if it cannot be found.
   */
  private static MapBinderBinding<?> getMapBinderBinding(
      Iterable<com.google.inject.spi.Element> elements) {
    final Collector collector = new Collector();
    for (com.google.inject.spi.Element element : elements) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public <T> Void visit(Binding<T> binding) {
              binding.acceptTargetVisitor(collector);
              return null;
            }
          });
    }
    return collector.mapbinding;
  }
}
