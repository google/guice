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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.internal.RealMultibinder.collectionOfJavaxProvidersOf;
import static com.google.inject.internal.SpiUtils.VisitType.BOTH;
import static com.google.inject.internal.SpiUtils.VisitType.MODULE;
import static com.google.inject.internal.SpiUtils.assertSetVisitor;
import static com.google.inject.internal.SpiUtils.instance;
import static com.google.inject.internal.SpiUtils.providerInstance;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.util.Modules;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
public class MultibinderTest extends TestCase {

  final TypeLiteral<Optional<String>> optionalOfString = new TypeLiteral<Optional<String>>() {};
  final TypeLiteral<Map<String, String>> mapOfStringString =
      new TypeLiteral<Map<String, String>>() {};
  final TypeLiteral<Set<String>> setOfString = new TypeLiteral<Set<String>>() {};
  final TypeLiteral<Set<Integer>> setOfInteger = new TypeLiteral<Set<Integer>>() {};
  final TypeLiteral<String> stringType = TypeLiteral.get(String.class);
  final TypeLiteral<Integer> intType = TypeLiteral.get(Integer.class);
  final TypeLiteral<List<String>> listOfStrings = new TypeLiteral<List<String>>() {};
  final TypeLiteral<Set<List<String>>> setOfListOfStrings = new TypeLiteral<Set<List<String>>>() {};
  final TypeLiteral<Collection<Provider<String>>> collectionOfProvidersOfStrings =
      new TypeLiteral<Collection<Provider<String>>>() {};

  public void testMultibinderAggregatesMultipleModules() {
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
            multibinder.addBinding().toInstance("C");
          }
        };
    Module de =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("D");
            multibinder.addBinding().toInstance("E");
          }
        };

    Injector injector = Guice.createInjector(abc, de);
    Key<Set<String>> setKey = Key.get(setOfString);
    Set<String> abcde = injector.getInstance(setKey);
    Set<String> results = setOf("A", "B", "C", "D", "E");

    assertEquals(results, abcde);
    assertSetVisitor(
        setKey,
        stringType,
        setOf(abc, de),
        BOTH,
        false,
        0,
        instance("A"),
        instance("B"),
        instance("C"),
        instance("D"),
        instance("E"));
  }

  public void testMultibinderAggregationForAnnotationInstance() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class, Names.named("abc"));
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");

            multibinder = Multibinder.newSetBinder(binder(), String.class, Names.named("abc"));
            multibinder.addBinding().toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Set<String>> setKey = Key.get(setOfString, Names.named("abc"));
    Set<String> abc = injector.getInstance(setKey);
    Set<String> results = setOf("A", "B", "C");
    assertEquals(results, abc);
    assertSetVisitor(
        setKey,
        stringType,
        setOf(module),
        BOTH,
        false,
        0,
        instance("A"),
        instance("B"),
        instance("C"));
  }

  public void testMultibinderAggregationForAnnotationType() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class, Abc.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");

            multibinder = Multibinder.newSetBinder(binder(), String.class, Abc.class);
            multibinder.addBinding().toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Set<String>> setKey = Key.get(setOfString, Abc.class);
    Set<String> abcde = injector.getInstance(setKey);
    Set<String> results = setOf("A", "B", "C");
    assertEquals(results, abcde);
    assertSetVisitor(
        setKey,
        stringType,
        setOf(module),
        BOTH,
        false,
        0,
        instance("A"),
        instance("B"),
        instance("C"));
  }

  public void testMultibinderWithMultipleAnnotationValueSets() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> abcMultibinder =
                Multibinder.newSetBinder(binder(), String.class, named("abc"));
            abcMultibinder.addBinding().toInstance("A");
            abcMultibinder.addBinding().toInstance("B");
            abcMultibinder.addBinding().toInstance("C");

            Multibinder<String> deMultibinder =
                Multibinder.newSetBinder(binder(), String.class, named("de"));
            deMultibinder.addBinding().toInstance("D");
            deMultibinder.addBinding().toInstance("E");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Set<String>> abcSetKey = Key.get(setOfString, named("abc"));
    Set<String> abc = injector.getInstance(abcSetKey);
    Key<Set<String>> deSetKey = Key.get(setOfString, named("de"));
    Set<String> de = injector.getInstance(deSetKey);
    Set<String> abcResults = setOf("A", "B", "C");
    assertEquals(abcResults, abc);
    Set<String> deResults = setOf("D", "E");
    assertEquals(deResults, de);
    assertSetVisitor(
        abcSetKey,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("A"),
        instance("B"),
        instance("C"));
    assertSetVisitor(
        deSetKey, stringType, setOf(module), BOTH, false, 1, instance("D"), instance("E"));
  }

  public void testMultibinderWithMultipleAnnotationTypeSets() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> abcMultibinder =
                Multibinder.newSetBinder(binder(), String.class, Abc.class);
            abcMultibinder.addBinding().toInstance("A");
            abcMultibinder.addBinding().toInstance("B");
            abcMultibinder.addBinding().toInstance("C");

            Multibinder<String> deMultibinder =
                Multibinder.newSetBinder(binder(), String.class, De.class);
            deMultibinder.addBinding().toInstance("D");
            deMultibinder.addBinding().toInstance("E");
          }
        };
    Injector injector = Guice.createInjector(module);

    Key<Set<String>> abcSetKey = Key.get(setOfString, Abc.class);
    Set<String> abc = injector.getInstance(abcSetKey);
    Key<Set<String>> deSetKey = Key.get(setOfString, De.class);
    Set<String> de = injector.getInstance(deSetKey);
    Set<String> abcResults = setOf("A", "B", "C");
    assertEquals(abcResults, abc);
    Set<String> deResults = setOf("D", "E");
    assertEquals(deResults, de);
    assertSetVisitor(
        abcSetKey,
        stringType,
        setOf(module),
        BOTH,
        false,
        1,
        instance("A"),
        instance("B"),
        instance("C"));
    assertSetVisitor(
        deSetKey, stringType, setOf(module), BOTH, false, 1, instance("D"), instance("E"));
  }

  public void testMultibinderWithMultipleSetTypes() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");
            Multibinder.newSetBinder(binder(), Integer.class).addBinding().toInstance(1);
          }
        };
    Injector injector = Guice.createInjector(module);

    assertEquals(setOf("A"), injector.getInstance(Key.get(setOfString)));
    assertEquals(setOf(1), injector.getInstance(Key.get(setOfInteger)));
    assertSetVisitor(
        Key.get(setOfString), stringType, setOf(module), BOTH, false, 1, instance("A"));
    assertSetVisitor(Key.get(setOfInteger), intType, setOf(module), BOTH, false, 1, instance(1));
  }

  public void testMultibinderWithEmptySet() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder.newSetBinder(binder(), String.class);
          }
        };
    Injector injector = Guice.createInjector(module);

    Set<String> set = injector.getInstance(Key.get(setOfString));
    assertEquals(Collections.emptySet(), set);
    assertSetVisitor(Key.get(setOfString), stringType, setOf(module), BOTH, false, 0);
  }

  public void testMultibinderSetIsUnmodifiable() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");
              }
            });

    Set<String> set = injector.getInstance(Key.get(setOfString));
    try {
      set.clear();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  public void testMultibinderSetIsSerializable() throws IOException, ClassNotFoundException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");
              }
            });

    Set<String> set = injector.getInstance(Key.get(setOfString));
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteStream);
    try {
      objectOutputStream.writeObject(set);
    } finally {
      objectOutputStream.close();
    }
    ObjectInputStream objectInputStream =
        new ObjectInputStream(new ByteArrayInputStream(byteStream.toByteArray()));
    try {
      Object setCopy = objectInputStream.readObject();
      assertEquals(set, setCopy);
    } finally {
      objectInputStream.close();
    }
  }

  public void testMultibinderSetIsLazy() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder.newSetBinder(binder(), Integer.class)
                .addBinding()
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

    assertEquals(setOf(1), injector.getInstance(Key.get(setOfInteger)));
    assertEquals(setOf(2), injector.getInstance(Key.get(setOfInteger)));
    assertEquals(setOf(3), injector.getInstance(Key.get(setOfInteger)));
    assertSetVisitor(
        Key.get(setOfInteger), intType, setOf(module), BOTH, false, 0, providerInstance(1));
  }

  public void testMultibinderSetForbidsDuplicateElements() {
    Module module1 =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toProvider(Providers.of("A"));
          }
        };
    Module module2 =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
          }
        };
    Injector injector = Guice.createInjector(module1, module2);

    try {
      injector.getInstance(Key.get(setOfString));
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) Set injection failed due to duplicated element \"A\"",
          "Bound at " + module1.getClass().getName(),
          "Bound at " + module2.getClass().getName());
    }

    // But we can still visit the module!
    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(module1, module2),
        MODULE,
        false,
        0,
        instance("A"),
        instance("A"));
  }

  public void testMultibinderSetShowsBothElementsIfToStringDifferent() {
    // A simple example of a type whose toString returns more information than its equals method
    // considers.
    class ValueType {
      int a;
      int b;

      ValueType(int a, int b) {
        this.a = a;
        this.b = b;
      }

      @Override
      public boolean equals(Object obj) {
        return (obj instanceof ValueType) && (((ValueType) obj).a == a);
      }

      @Override
      public int hashCode() {
        return a;
      }

      @Override
      public String toString() {
        return String.format("ValueType(%d,%d)", a, b);
      }
    }

    Module module1 =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<ValueType> multibinder =
                Multibinder.newSetBinder(binder(), ValueType.class);
            multibinder.addBinding().toProvider(Providers.of(new ValueType(1, 2)));
          }
        };
    Module module2 =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<ValueType> multibinder =
                Multibinder.newSetBinder(binder(), ValueType.class);
            multibinder.addBinding().toInstance(new ValueType(1, 3));
          }
        };
    Injector injector = Guice.createInjector(module1, module2);

    TypeLiteral<ValueType> valueType = TypeLiteral.get(ValueType.class);
    TypeLiteral<Set<ValueType>> setOfValueType = new TypeLiteral<Set<ValueType>>() {};
    try {
      injector.getInstance(Key.get(setOfValueType));
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) Set injection failed due to multiple elements comparing equal:",
          "\"ValueType(1,2)\"",
          "bound at " + module1.getClass().getName(),
          "\"ValueType(1,3)\"",
          "bound at " + module2.getClass().getName());
    }

    // But we can still visit the module!
    assertSetVisitor(
        Key.get(setOfValueType),
        valueType,
        setOf(module1, module2),
        MODULE,
        false,
        0,
        instance(new ValueType(1, 2)),
        instance(new ValueType(1, 3)));
  }

  public void testMultibinderSetPermitDuplicateElements() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
          }
        };
    Module bc =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.permitDuplicates();
            multibinder.addBinding().toInstance("B");
            multibinder.addBinding().toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(setOf("A", "B", "C"), injector.getInstance(Key.get(setOfString)));
    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(ab, bc),
        BOTH,
        true,
        0,
        instance("A"),
        instance("B"),
        instance("C"));
  }

  public void testMultibinderSetPermitDuplicateElementsFromOtherModule() {
    // This module duplicates a binding for "B", which would normally be an error.
    // Because module cd is also installed and the Multibinder<String>
    // in cd sets permitDuplicates, there should be no error.
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
            multibinder.addBinding().toProvider(Providers.of("B"));
          }
        };
    Module cd =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.permitDuplicates();
            multibinder.addBinding().toInstance("C");
            multibinder.addBinding().toInstance("D");
          }
        };
    Injector injector = Guice.createInjector(ab, cd);

    assertEquals(setOf("A", "B", "C", "D"), injector.getInstance(Key.get(setOfString)));
    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(ab, cd),
        BOTH,
        true,
        0,
        instance("A"),
        instance("B"),
        providerInstance("B"),
        instance("C"),
        instance("D"));
  }

  public void testMultibinderSetPermitDuplicateCallsToPermitDuplicates() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.permitDuplicates();
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
          }
        };
    Module bc =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.permitDuplicates();
            multibinder.addBinding().toInstance("B");
            multibinder.addBinding().toInstance("C");
          }
        };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(setOf("A", "B", "C"), injector.getInstance(Key.get(setOfString)));
    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(ab, bc),
        BOTH,
        true,
        0,
        instance("A"),
        instance("B"),
        instance("C"));
  }

  public void testMultibinderSetForbidsNullElements() {
    Module m =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder.newSetBinder(binder(), String.class)
                .addBinding()
                .toProvider(Providers.<String>of(null));
          }
        };
    Injector injector = Guice.createInjector(m);

    try {
      injector.getInstance(Key.get(setOfString));
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) Set injection failed due to null element bound at: "
              + m.getClass().getName()
              + ".configure(");
    }
  }

  public void testSourceLinesInMultibindings() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              Multibinder.newSetBinder(binder(), Integer.class).addBinding();
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          true,
          "No implementation for java.lang.Integer",
          "at " + getClass().getName());
    }
  }

  /**
   * We just want to make sure that multibinder's binding depends on each of its values. We don't
   * really care about the underlying structure of those bindings, which are implementation details.
   */
  public void testMultibinderDependencies() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
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
   * We just want to make sure that multibinder's binding depends on each of its values. We don't
   * really care about the underlying structure of those bindings, which are implementation details.
   */
  public void testMultibinderDependenciesInToolStage() {
    Injector injector =
        Guice.createInjector(
            Stage.TOOL,
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
                multibinder.addBinding().toInstance("A");
                multibinder.addBinding().to(Key.get(String.class, Names.named("b")));

                bindConstant().annotatedWith(Names.named("b")).to("B");
              }
            });

    Binding<Set<String>> binding = injector.getBinding(new Key<Set<String>>() {});
    HasDependencies withDependencies = (HasDependencies) binding;
    InstanceBinding<?> instanceBinding = null;
    LinkedKeyBinding<?> linkedBinding = null;
    // The non-tool stage test can test this by calling injector.getInstance to ensure
    // the right values are returned -- in tool stage we can't do that.  It's also a
    // little difficult to validate the dependencies & bindings, because they're
    // bindings created internally within Multibinder.
    // To workaround this, we just validate that the dependencies lookup to a single
    // InstanceBinding whose value is "A" and another LinkedBinding whose target is
    // the Key of @Named("b") String=B
    for (Dependency<?> dependency : withDependencies.getDependencies()) {
      Binding<?> b = injector.getBinding(dependency.getKey());
      if (b instanceof InstanceBinding) {
        if (instanceBinding != null) {
          fail(
              "Already have an instance binding of: "
                  + instanceBinding
                  + ", and now want to add: "
                  + b);
        } else {
          instanceBinding = (InstanceBinding) b;
        }
      } else if (b instanceof LinkedKeyBinding) {
        if (linkedBinding != null) {
          fail(
              "Already have a linked binding of: " + linkedBinding + ", and now want to add: " + b);
        } else {
          linkedBinding = (LinkedKeyBinding) b;
        }
      } else {
        fail("Unexpected dependency of: " + dependency);
      }
    }

    assertNotNull(instanceBinding);
    assertNotNull(linkedBinding);

    assertEquals("A", instanceBinding.getInstance());
    assertEquals(Key.get(String.class, Names.named("b")), linkedBinding.getLinkedKey());
  }

  /**
   * Our implementation maintains order, but doesn't guarantee it in the API spec. TODO: specify the
   * iteration order?
   */
  public void testBindOrderEqualsIterationOrder() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
                multibinder.addBinding().toInstance("leonardo");
                multibinder.addBinding().toInstance("donatello");
                install(
                    new AbstractModule() {
                      @Override
                      protected void configure() {
                        Multibinder.newSetBinder(binder(), String.class)
                            .addBinding()
                            .toInstance("michaelangelo");
                      }
                    });
              }
            },
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("raphael");
              }
            });

    List<String> inOrder = ImmutableList.copyOf(injector.getInstance(Key.get(setOfString)));
    assertEquals(ImmutableList.of("leonardo", "donatello", "michaelangelo", "raphael"), inOrder);
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Abc {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface De {}

  private <T> Set<T> setOf(T... elements) {
    Set<T> result = Sets.newHashSet();
    Collections.addAll(result, elements);
    return result;
  }

  /** With overrides, we should get the union of all multibindings. */
  public void testModuleOverrideAndMultibindings() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
          }
        };
    Module cd =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("C");
            multibinder.addBinding().toInstance("D");
          }
        };
    Module ef =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("E");
            multibinder.addBinding().toInstance("F");
          }
        };

    Module abcd = Modules.override(ab).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(
        ImmutableSet.of("A", "B", "C", "D", "E", "F"), injector.getInstance(Key.get(setOfString)));

    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(abcd, ef),
        BOTH,
        false,
        0,
        instance("A"),
        instance("B"),
        instance("C"),
        instance("D"),
        instance("E"),
        instance("F"));
  }

  /** With overrides, we should get the union of all multibindings. */
  public void testModuleOverrideAndMultibindingsWithPermitDuplicates() {
    Module abc =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
            multibinder.addBinding().toInstance("C");
            multibinder.permitDuplicates();
          }
        };
    Module cd =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("C");
            multibinder.addBinding().toInstance("D");
            multibinder.permitDuplicates();
          }
        };
    Module ef =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("E");
            multibinder.addBinding().toInstance("F");
            multibinder.permitDuplicates();
          }
        };

    Module abcd = Modules.override(abc).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(
        ImmutableSet.of("A", "B", "C", "D", "E", "F"), injector.getInstance(Key.get(setOfString)));

    assertSetVisitor(
        Key.get(setOfString),
        stringType,
        setOf(abcd, ef),
        BOTH,
        true,
        0,
        instance("A"),
        instance("B"),
        instance("C"),
        instance("D"),
        instance("E"),
        instance("F"));
  }

  /** Doubly-installed modules should not conflict, even when one is overridden. */
  public void testModuleOverrideRepeatedInstallsAndMultibindings_toInstance() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("A");
            multibinder.addBinding().toInstance("B");
          }
        };

    // Guice guarantees this assertion, as the same module cannot be installed twice.
    assertEquals(
        ImmutableSet.of("A", "B"), Guice.createInjector(ab, ab).getInstance(Key.get(setOfString)));

    // Guice will only guarantee this assertion if Multibinder ensures the bindings match.
    Injector injector = Guice.createInjector(ab, Modules.override(ab).with(ab));
    assertEquals(ImmutableSet.of("A", "B"), injector.getInstance(Key.get(setOfString)));
  }

  public void testModuleOverrideRepeatedInstallsAndMultibindings_toKey() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Key<String> aKey = Key.get(String.class, Names.named("A_string"));
            Key<String> bKey = Key.get(String.class, Names.named("B_string"));
            bind(aKey).toInstance("A");
            bind(bKey).toInstance("B");

            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().to(aKey);
            multibinder.addBinding().to(bKey);
          }
        };

    // Guice guarantees this assertion, as the same module cannot be installed twice.
    assertEquals(
        ImmutableSet.of("A", "B"), Guice.createInjector(ab, ab).getInstance(Key.get(setOfString)));

    // Guice will only guarantee this assertion if Multibinder ensures the bindings match.
    Injector injector = Guice.createInjector(ab, Modules.override(ab).with(ab));
    assertEquals(ImmutableSet.of("A", "B"), injector.getInstance(Key.get(setOfString)));
  }

  public void testModuleOverrideRepeatedInstallsAndMultibindings_toProviderInstance() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toProvider(Providers.of("A"));
            multibinder.addBinding().toProvider(Providers.of("B"));
          }
        };

    // Guice guarantees this assertion, as the same module cannot be installed twice.
    assertEquals(
        ImmutableSet.of("A", "B"), Guice.createInjector(ab, ab).getInstance(Key.get(setOfString)));

    // Guice will only guarantee this assertion if Multibinder ensures the bindings match.
    Injector injector = Guice.createInjector(ab, Modules.override(ab).with(ab));
    assertEquals(ImmutableSet.of("A", "B"), injector.getInstance(Key.get(setOfString)));
  }

  private static class AStringProvider implements Provider<String> {
    @Override
    public String get() {
      return "A";
    }
  }

  private static class BStringProvider implements Provider<String> {
    @Override
    public String get() {
      return "B";
    }
  }

  public void testModuleOverrideRepeatedInstallsAndMultibindings_toProviderKey() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toProvider(Key.get(AStringProvider.class));
            multibinder.addBinding().toProvider(Key.get(BStringProvider.class));
          }
        };

    // Guice guarantees this assertion, as the same module cannot be installed twice.
    assertEquals(
        ImmutableSet.of("A", "B"), Guice.createInjector(ab, ab).getInstance(Key.get(setOfString)));

    // Guice will only guarantee this assertion if Multibinder ensures the bindings match.
    Injector injector = Guice.createInjector(ab, Modules.override(ab).with(ab));
    assertEquals(ImmutableSet.of("A", "B"), injector.getInstance(Key.get(setOfString)));
  }

  private static class StringGrabber {
    private final String string;

    @SuppressWarnings("unused") // Found by reflection
    public StringGrabber(@Named("A_string") String string) {
      this.string = string;
    }

    @SuppressWarnings("unused") // Found by reflection
    public StringGrabber(@Named("B_string") String string, int unused) {
      this.string = string;
    }

    @Override
    public int hashCode() {
      return string.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof StringGrabber) && ((StringGrabber) obj).string.equals(string);
    }

    @Override
    public String toString() {
      return "StringGrabber(" + string + ")";
    }

    static Set<String> values(Iterable<StringGrabber> grabbers) {
      Set<String> result = new HashSet<>();
      for (StringGrabber grabber : grabbers) {
        result.add(grabber.string);
      }
      return result;
    }
  }

  public void testModuleOverrideRepeatedInstallsAndMultibindings_toConstructor() {
    TypeLiteral<Set<StringGrabber>> setOfStringGrabber = new TypeLiteral<Set<StringGrabber>>() {};
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Key<String> aKey = Key.get(String.class, Names.named("A_string"));
            Key<String> bKey = Key.get(String.class, Names.named("B_string"));
            bind(aKey).toInstance("A");
            bind(bKey).toInstance("B");
            bind(Integer.class).toInstance(0); // used to disambiguate constructors

            Multibinder<StringGrabber> multibinder =
                Multibinder.newSetBinder(binder(), StringGrabber.class);
            try {
              multibinder
                  .addBinding()
                  .toConstructor(StringGrabber.class.getConstructor(String.class));
              multibinder
                  .addBinding()
                  .toConstructor(StringGrabber.class.getConstructor(String.class, int.class));
            } catch (NoSuchMethodException e) {
              fail("No such method: " + e.getMessage());
            }
          }
        };

    // Guice guarantees this assertion, as the same module cannot be installed twice.
    assertEquals(
        ImmutableSet.of("A", "B"),
        StringGrabber.values(
            Guice.createInjector(ab, ab).getInstance(Key.get(setOfStringGrabber))));

    // Guice will only guarantee this assertion if Multibinder ensures the bindings match.
    Injector injector = Guice.createInjector(ab, Modules.override(ab).with(ab));
    assertEquals(
        ImmutableSet.of("A", "B"),
        StringGrabber.values(injector.getInstance(Key.get(setOfStringGrabber))));
  }

  /**
   * Unscoped bindings should not conflict, whether they were bound with no explicit scope, or
   * explicitly bound in {@link Scopes#NO_SCOPE}.
   */
  public void testDuplicateUnscopedBindings() {
    Module singleBinding =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Integer.class).to(Key.get(Integer.class, named("A")));
            bind(Integer.class).to(Key.get(Integer.class, named("A"))).in(Scopes.NO_SCOPE);
          }

          @Provides
          @Named("A")
          int provideInteger() {
            return 5;
          }
        };
    Module multibinding =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<Integer> multibinder = Multibinder.newSetBinder(binder(), Integer.class);
            multibinder.addBinding().to(Key.get(Integer.class, named("A")));
            multibinder.addBinding().to(Key.get(Integer.class, named("A"))).in(Scopes.NO_SCOPE);
          }
        };

    assertEquals(5, (int) Guice.createInjector(singleBinding).getInstance(Integer.class));
    assertEquals(
        ImmutableSet.of(5),
        Guice.createInjector(singleBinding, multibinding).getInstance(Key.get(setOfInteger)));
  }

  /** Ensure key hash codes are fixed at injection time, not binding time. */
  public void testKeyHashCodesFixedAtInjectionTime() {
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<List<String>> multibinder =
                Multibinder.newSetBinder(binder(), listOfStrings);
            List<String> list = Lists.newArrayList();
            multibinder.addBinding().toInstance(list);
            list.add("A");
            list.add("B");
          }
        };

    Injector injector = Guice.createInjector(ab);
    for (Entry<Key<?>, Binding<?>> entry : injector.getAllBindings().entrySet()) {
      Key<?> bindingKey = entry.getKey();
      Key<?> clonedKey;
      if (bindingKey.getAnnotation() != null) {
        clonedKey = bindingKey.ofType(bindingKey.getTypeLiteral());
      } else if (bindingKey.getAnnotationType() != null) {
        clonedKey = bindingKey.ofType(bindingKey.getTypeLiteral());
      } else {
        clonedKey = Key.get(bindingKey.getTypeLiteral());
      }
      assertEquals(bindingKey, clonedKey);
      assertEquals(
          "Incorrect hashcode for " + bindingKey + " -> " + entry.getValue(),
          bindingKey.hashCode(),
          clonedKey.hashCode());
    }
  }

  /** Ensure bindings do not rehash their keys once returned from {@link Elements#getElements}. */
  public void testBindingKeysFixedOnReturnFromGetElements() {
    final List<String> list = Lists.newArrayList();
    Module ab =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<List<String>> multibinder =
                Multibinder.newSetBinder(binder(), listOfStrings);
            multibinder.addBinding().toInstance(list);
            list.add("A");
            list.add("B");
          }
        };

    InstanceBinding<?> binding =
        Elements.getElements(ab).stream()
            .filter(InstanceBinding.class::isInstance)
            .map(InstanceBinding.class::cast)
            .collect(onlyElement());
    Key<?> keyBefore = binding.getKey();
    assertEquals(listOfStrings, keyBefore.getTypeLiteral());

    list.add("C");
    Key<?> keyAfter = binding.getKey();
    assertSame(keyBefore, keyAfter);
  }

  /*
   * Verify through gratuitous mutation that key hashCode snapshots and whatnot happens at the right
   * times, by binding two lists that are different at injector creation, but compare equal when the
   * module is configured *and* when the set is instantiated.
   */
  public void testConcurrentMutation_bindingsDiffentAtInjectorCreation() {
    // We initially bind two equal lists
    final List<String> list1 = Lists.newArrayList();
    final List<String> list2 = Lists.newArrayList();
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<List<String>> multibinder =
                Multibinder.newSetBinder(binder(), listOfStrings);
            multibinder.addBinding().toInstance(list1);
            multibinder.addBinding().toInstance(list2);
          }
        };
    List<Element> elements = Elements.getElements(module);

    // Now we change the lists so they no longer match, and create the injector.
    list1.add("A");
    list2.add("B");
    Injector injector = Guice.createInjector(Elements.getModule(elements));

    // Now we change the lists so they compare equal again, and create the set.
    list1.add(1, "B");
    list2.add(0, "A");
    try {
      injector.getInstance(Key.get(setOfListOfStrings));
      fail();
    } catch (ProvisionException e) {
      assertEquals(1, e.getErrorMessages().size());
      assertContains(
          Iterables.getOnlyElement(e.getErrorMessages()).getMessage().toString(),
          "Set injection failed due to duplicated element \"[A, B]\"");
    }

    // Finally, we change the lists again so they are once more different, and ensure the set
    // contains both.
    list1.remove("A");
    list2.remove("B");
    Set<List<String>> set = injector.getInstance(Key.get(setOfListOfStrings));
    assertEquals(ImmutableSet.of(ImmutableList.of("A"), ImmutableList.of("B")), set);
  }

  /*
   * Verify through gratuitous mutation that key hashCode snapshots and whatnot happen at the right
   * times, by binding two lists that compare equal at injector creation, but are different when the
   * module is configured *and* when the set is instantiated.
   */
  public void testConcurrentMutation_bindingsSameAtInjectorCreation() {
    // We initially bind two distinct lists
    final List<String> list1 = Lists.newArrayList("A");
    final List<String> list2 = Lists.newArrayList("B");
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<List<String>> multibinder =
                Multibinder.newSetBinder(binder(), listOfStrings);
            multibinder.addBinding().toInstance(list1);
            multibinder.addBinding().toInstance(list2);
          }
        };
    List<Element> elements = Elements.getElements(module);

    // Now we change the lists so they compare equal, and create the injector.
    list1.add(1, "B");
    list2.add(0, "A");
    Injector injector = Guice.createInjector(Elements.getModule(elements));

    // Now we change the lists again so they are once more different, and create the set.
    list1.remove("A");
    list2.remove("B");
    Set<List<String>> set = injector.getInstance(Key.get(setOfListOfStrings));

    // The set will contain just one of the two lists.
    // (In fact, it will be the first one we bound, but we don't promise that, so we won't test it.)
    assertTrue(
        ImmutableSet.of(ImmutableList.of("A")).equals(set)
            || ImmutableSet.of(ImmutableList.of("B")).equals(set));
  }

  @BindingAnnotation
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  private static @interface Marker {}

  @Marker
  public void testMultibinderMatching() throws Exception {
    Method m = MultibinderTest.class.getDeclaredMethod("testMultibinderMatching");
    assertNotNull(m);
    final Annotation marker = m.getAnnotation(Marker.class);
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              public void configure() {
                Multibinder<Integer> mb1 =
                    Multibinder.newSetBinder(binder(), Integer.class, Marker.class);
                Multibinder<Integer> mb2 =
                    Multibinder.newSetBinder(binder(), Integer.class, marker);
                mb1.addBinding().toInstance(1);
                mb2.addBinding().toInstance(2);

                // This assures us that the two binders are equivalent, so we expect the instance added to
                // each to have been added to one set.
                assertEquals(mb1, mb2);
              }
            });
    TypeLiteral<Set<Integer>> t = new TypeLiteral<Set<Integer>>() {};
    Set<Integer> s1 = injector.getInstance(Key.get(t, Marker.class));
    Set<Integer> s2 = injector.getInstance(Key.get(t, marker));

    // This assures us that the two sets are in fact equal.  They may not be same set (as in Java
    // object identical), but we shouldn't expect that, since probably Guice creates the set each
    // time in case the elements are dependent on scope.
    assertEquals(s1, s2);

    // This ensures that MultiBinder is internally using the correct set name --
    // making sure that instances of marker annotations have the same set name as
    // MarkerAnnotation.class.
    Set<Integer> expected = new HashSet<>();
    expected.add(1);
    expected.add(2);
    assertEquals(expected, s1);
  }

  // See issue 670
  public void testSetAndMapValueAreDistinct() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");

                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("B")
                    .toInstance("b");

                OptionalBinder.newOptionalBinder(binder(), String.class)
                    .setDefault()
                    .toInstance("C");
                OptionalBinder.newOptionalBinder(binder(), String.class)
                    .setBinding()
                    .toInstance("D");
              }
            });

    assertEquals(ImmutableSet.of("A"), injector.getInstance(Key.get(setOfString)));
    assertEquals(ImmutableMap.of("B", "b"), injector.getInstance(Key.get(mapOfStringString)));
    assertEquals(Optional.of("D"), injector.getInstance(Key.get(optionalOfString)));
  }

  // See issue 670
  public void testSetAndMapValueAreDistinctInSpi() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("A");

                MapBinder.newMapBinder(binder(), String.class, String.class)
                    .addBinding("B")
                    .toInstance("b");

                OptionalBinder.newOptionalBinder(binder(), String.class)
                    .setDefault()
                    .toInstance("C");
              }
            });
    Collector collector = new Collector();
    Binding<Map<String, String>> mapbinding = injector.getBinding(Key.get(mapOfStringString));
    mapbinding.acceptTargetVisitor(collector);
    assertNotNull(collector.mapbinding);

    Binding<Set<String>> setbinding = injector.getBinding(Key.get(setOfString));
    setbinding.acceptTargetVisitor(collector);
    assertNotNull(collector.setbinding);

    Binding<Optional<String>> optionalbinding = injector.getBinding(Key.get(optionalOfString));
    optionalbinding.acceptTargetVisitor(collector);
    assertNotNull(collector.optionalbinding);

    // There should only be three instance bindings for string types
    // (but because of the OptionalBinder, there's 2 ProviderInstanceBindings also).
    // We also know the InstanceBindings will be in the order: A, b, C because that's
    // how we bound them, and binding order is preserved.
    List<Binding<String>> bindings =
        injector.findBindingsByType(stringType).stream()
            .filter(Predicates.instanceOf(InstanceBinding.class))
            .collect(toImmutableList());
    assertEquals(bindings.toString(), 3, bindings.size());
    Binding<String> a = bindings.get(0);
    Binding<String> b = bindings.get(1);
    Binding<String> c = bindings.get(2);
    assertEquals("A", ((InstanceBinding<String>) a).getInstance());
    assertEquals("b", ((InstanceBinding<String>) b).getInstance());
    assertEquals("C", ((InstanceBinding<String>) c).getInstance());

    // Make sure the correct elements belong to their own sets.
    assertFalse(collector.mapbinding.containsElement(a));
    assertTrue(collector.mapbinding.containsElement(b));
    assertFalse(collector.mapbinding.containsElement(c));

    assertTrue(collector.setbinding.containsElement(a));
    assertFalse(collector.setbinding.containsElement(b));
    assertFalse(collector.setbinding.containsElement(c));

    assertFalse(collector.optionalbinding.containsElement(a));
    assertFalse(collector.optionalbinding.containsElement(b));
    assertTrue(collector.optionalbinding.containsElement(c));
  }

  public void testMultibinderCanInjectCollectionOfProviders() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toProvider(Providers.of("A"));
            multibinder.addBinding().toProvider(Providers.of("B"));
            multibinder.addBinding().toInstance("C");
          }
        };
    Collection<String> expectedValues = ImmutableList.of("A", "B", "C");

    Injector injector = Guice.createInjector(module);

    Collection<Provider<String>> providers =
        injector.getInstance(Key.get(collectionOfProvidersOfStrings));
    assertEquals(expectedValues, collectValues(providers));

    Collection<javax.inject.Provider<String>> javaxProviders =
        injector.getInstance(Key.get(collectionOfJavaxProvidersOf(stringType)));
    assertEquals(expectedValues, collectValues(javaxProviders));
  }

  public void testMultibinderCanInjectCollectionOfProvidersWithAnnotation() {
    final Annotation ann = Names.named("foo");
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            final Multibinder<String> multibinder =
                Multibinder.newSetBinder(binder(), String.class, ann);
            multibinder.addBinding().toProvider(Providers.of("A"));
            multibinder.addBinding().toProvider(Providers.of("B"));
            multibinder.addBinding().toInstance("C");
          }
        };
    Collection<String> expectedValues = ImmutableList.of("A", "B", "C");

    Injector injector = Guice.createInjector(module);

    Collection<Provider<String>> providers =
        injector.getInstance(Key.get(collectionOfProvidersOfStrings, ann));
    Collection<String> values = collectValues(providers);
    assertEquals(expectedValues, values);

    Collection<javax.inject.Provider<String>> javaxProviders =
        injector.getInstance(Key.get(collectionOfJavaxProvidersOf(stringType), ann));
    assertEquals(expectedValues, collectValues(javaxProviders));
  }

  public void testMultibindingProviderDependencies() {
    final Annotation setAnn = Names.named("foo");
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder<String> multibinder =
                    Multibinder.newSetBinder(binder(), String.class, setAnn);
                multibinder.addBinding().toInstance("a");
                multibinder.addBinding().toInstance("b");
              }
            });
    HasDependencies providerBinding =
        (HasDependencies) injector.getBinding(new Key<Collection<Provider<String>>>(setAnn) {});
    HasDependencies setBinding =
        (HasDependencies) injector.getBinding(new Key<Set<String>>(setAnn) {});
    // sanity check the size
    assertEquals(setBinding.getDependencies().toString(), 2, setBinding.getDependencies().size());
    Set<Dependency<?>> expected = Sets.newHashSet();
    for (Dependency<?> dep : setBinding.getDependencies()) {
      Key<?> key = dep.getKey();
      Dependency<?> providerDependency =
          Dependency.get(key.ofType(Types.providerOf(key.getTypeLiteral().getType())));
      expected.add(providerDependency);
    }
    assertEquals(expected, providerBinding.getDependencies());
  }

  public void testEmptyMultibinder() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                Multibinder.newSetBinder(binder(), String.class);
              }
            });
    assertEquals(ImmutableSet.of(), injector.getInstance(new Key<Set<String>>() {}));
    assertEquals(
        ImmutableList.of(), injector.getInstance(new Key<Collection<Provider<String>>>() {}));
  }

  private static final class ObjectWithInjectionPoint {
    boolean setterHasBeenCalled;

    @Inject
    void setter(String dummy) {
      setterHasBeenCalled = true;
    }
  }

  // This tests for a behavior where InstanceBindingImpl.getProvider() would return uninitialized
  // instances if called during injector creation (depending on the order of injection requests).
  public void testMultibinderDependsOnInstanceBindingWithInjectionPoints() {
    Guice.createInjector(
        new AbstractModule() {
          private Provider<Set<ObjectWithInjectionPoint>> provider;

          @Override
          protected void configure() {
            bind(Object.class).toInstance(this); // force setter() to be injected first
            bind(String.class).toInstance("foo");
            this.provider = getProvider(new Key<Set<ObjectWithInjectionPoint>>() {});
            Multibinder.newSetBinder(binder(), ObjectWithInjectionPoint.class)
                .addBinding()
                .toInstance(new ObjectWithInjectionPoint());
          }

          @Inject
          void setter(String s) {
            for (ObjectWithInjectionPoint item : provider.get()) {
              assertTrue(item.setterHasBeenCalled);
            }
          }
        });
  }

  public void testMultibinderWithWildcard() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("a");
            multibinder.addBinding().toInstance("b");
            multibinder.addBinding().toInstance("c");
          }
        };
    Injector injector = Guice.createInjector(module);

    Set<String> set = injector.getInstance(new Key<Set<String>>() {});
    assertEquals(ImmutableSet.of("a", "b", "c"), set);

    Set<? extends String> setOfWildcard = injector.getInstance(new Key<Set<? extends String>>() {});
    assertEquals(ImmutableSet.of("a", "b", "c"), setOfWildcard);
  }

  /**
   * Injection of {@code Set<? extends T>} wasn't added until 2020-07. It's possible that
   * applications already have a binding to that type. If they do, confirm that Guice fails fast
   * with a duplicate binding error.
   */
  public void testMultibinderConflictsWithExistingWildcard() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("a");
            multibinder.addBinding().toInstance("b");
            multibinder.addBinding().toInstance("c");
          }

          @Provides
          public Set<? extends String> provideStrings() {
            return ImmutableSet.of("d", "e", "f");
          }
        };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "A binding to java.util.Set<? extends java.lang.String> was already configured"));
    }
  }

  /**
   * This is the same as the previous test, but it gets at the conflicting set through a multibinder
   * rather than through a regular binding. It's unlikely that application developers would do this
   * in practice, but if they do we want to make sure it is detected and fails fast.
   */
  public void testMultibinderConflictsWithExistingMultibinder() {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
            multibinder.addBinding().toInstance("a");
            multibinder.addBinding().toInstance("b");
            multibinder.addBinding().toInstance("c");

            // Safe because Set<? extends String> can be used as Set<String> in this context
            @SuppressWarnings("unchecked")
            Multibinder<String> multibinder2 =
                Multibinder.newSetBinder(
                    binder(), (TypeLiteral<String>) TypeLiteral.get(Types.subtypeOf(String.class)));
            multibinder2.addBinding().toInstance("d");
            multibinder2.addBinding().toInstance("e");
          }
        };

    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException e) {
      assertTrue(
          e.getMessage()
              .contains(
                  "A binding to java.util.Set<? extends java.lang.String> was already configured"));
    }
  }

  private <T> Collection<T> collectValues(
      Collection<? extends javax.inject.Provider<T>> providers) {
    Collection<T> values = Lists.newArrayList();
    for (javax.inject.Provider<T> provider : providers) {
      values.add(provider.get());
    }
    return values;
  }
}
