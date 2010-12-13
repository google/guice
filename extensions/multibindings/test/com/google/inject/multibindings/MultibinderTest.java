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
import static com.google.inject.multibindings.SpiUtils.assertSetVisitor;
import static com.google.inject.multibindings.SpiUtils.instance;
import static com.google.inject.multibindings.SpiUtils.providerInstance;
import static com.google.inject.multibindings.SpiUtils.VisitType.BOTH;
import static com.google.inject.multibindings.SpiUtils.VisitType.MODULE;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Sets;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.util.Modules;
import com.google.inject.util.Providers;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MultibinderTest extends TestCase {

  final TypeLiteral<Set<String>> setOfString = new TypeLiteral<Set<String>>() {};
  final TypeLiteral<Set<Integer>> setOfInteger = new TypeLiteral<Set<Integer>>() {};
  final TypeLiteral<String> stringType = TypeLiteral.get(String.class);
  final TypeLiteral<Integer> intType = TypeLiteral.get(Integer.class);

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
    Key<Set<String>> setKey = Key.get(setOfString);
    Set<String> abcde = injector.getInstance(setKey);
    Set<String> results = setOf("A", "B", "C", "D", "E");

    assertEquals(results, abcde);
    assertSetVisitor(setKey, stringType, setOf(abc, de), BOTH, false, 0, instance("A"),
        instance("B"), instance("C"), instance("D"), instance("E"));
  }

  public void testMultibinderAggregationForAnnotationInstance() {
    Module module = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder
            = Multibinder.newSetBinder(binder(), String.class, Names.named("abc"));
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
    assertSetVisitor(setKey, stringType, setOf(module), BOTH, false, 0, instance("A"),
        instance("B"), instance("C"));
  }

  public void testMultibinderAggregationForAnnotationType() {
    Module module = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder
            = Multibinder.newSetBinder(binder(), String.class, Abc.class);
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
    assertSetVisitor(setKey, stringType, setOf(module), BOTH, false, 0, instance("A"),
        instance("B"), instance("C"));
  }

  public void testMultibinderWithMultipleAnnotationValueSets() {
    Module module = new AbstractModule() {
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
    assertSetVisitor(abcSetKey, stringType, setOf(module), BOTH, false, 1, instance("A"),
        instance("B"), instance("C"));
    assertSetVisitor(deSetKey, stringType, setOf(module), BOTH, false, 1, instance("D"), instance("E"));
  }

  public void testMultibinderWithMultipleAnnotationTypeSets() {
    Module module = new AbstractModule() {
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
    assertSetVisitor(abcSetKey, stringType, setOf(module), BOTH, false, 1, instance("A"),
        instance("B"), instance("C"));
    assertSetVisitor(deSetKey, stringType, setOf(module), BOTH, false, 1, instance("D"), instance("E"));
  }

  public void testMultibinderWithMultipleSetTypes() {
    Module module = new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), String.class)
            .addBinding().toInstance("A");
        Multibinder.newSetBinder(binder(), Integer.class)
            .addBinding().toInstance(1);
      }
    };
    Injector injector = Guice.createInjector(module);

    assertEquals(setOf("A"), injector.getInstance(Key.get(setOfString)));
    assertEquals(setOf(1), injector.getInstance(Key.get(setOfInteger)));
    assertSetVisitor(Key.get(setOfString), stringType, setOf(module), BOTH, false, 1, instance("A"));
    assertSetVisitor(Key.get(setOfInteger), intType, setOf(module), BOTH, false, 1, instance(1));
  }

  public void testMultibinderWithEmptySet() {
    Module module = new AbstractModule() {
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
    Module module = new AbstractModule() {
      protected void configure() {
        Multibinder.newSetBinder(binder(), Integer.class)
            .addBinding().toProvider(new Provider<Integer>() {
          int nextValue = 1;
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
    assertSetVisitor(Key.get(setOfInteger), intType, setOf(module), BOTH, false, 0, providerInstance(1));
  }

  public void testMultibinderSetForbidsDuplicateElements() {
    Module module = new AbstractModule() {
      protected void configure() {
        final Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("A");
      }
    };
    Injector injector = Guice.createInjector(module);

    try {
      injector.getInstance(Key.get(setOfString));
      fail();
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(),
          "1) Set injection failed due to duplicated element \"A\"");
    }

    // But we can still visit the module!
    assertSetVisitor(Key.get(setOfString), stringType, setOf(module), MODULE, false, 0,
        instance("A"), instance("A"));
  }

  public void testMultibinderSetPermitDuplicateElements() {
    Module ab = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");
      }
    };
    Module bc = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.permitDuplicates();
        multibinder.addBinding().toInstance("B");
        multibinder.addBinding().toInstance("C");
      }
    };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(setOf("A", "B", "C"), injector.getInstance(Key.get(setOfString)));
    assertSetVisitor(Key.get(setOfString), stringType, setOf(ab, bc), BOTH, true, 0,
        instance("A"), instance("B"), instance("B"), instance("C"));
  }

  public void testMultibinderSetPermitDuplicateCallsToPermitDuplicates() {
    Module ab = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.permitDuplicates();
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");
      }
    };
    Module bc = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.permitDuplicates();
        multibinder.addBinding().toInstance("B");
        multibinder.addBinding().toInstance("C");
      }
    };
    Injector injector = Guice.createInjector(ab, bc);

    assertEquals(setOf("A", "B", "C"), injector.getInstance(Key.get(setOfString)));
    assertSetVisitor(Key.get(setOfString), stringType, setOf(ab, bc), BOTH, true, 0,
        instance("A"), instance("B"), instance("B"), instance("C"));
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
   * We just want to make sure that multibinder's binding depends on each of its values. We don't
   * really care about the underlying structure of those bindings, which are implementation details.
   */
  public void testMultibinderDependenciesInToolStage() {
    Injector injector = Guice.createInjector(Stage.TOOL, new AbstractModule() {
        protected void configure() {
          Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
          multibinder.addBinding().toInstance("A");
          multibinder.addBinding().to(Key.get(String.class, Names.named("b")));
  
          bindConstant().annotatedWith(Names.named("b")).to("B");
        }});

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
      if(b instanceof InstanceBinding) {
        if(instanceBinding != null) {
          fail("Already have an instance binding of: " + instanceBinding + ", and now want to add: " + b);
        } else {
          instanceBinding = (InstanceBinding)b;
        }
      } else if(b instanceof LinkedKeyBinding) {
        if(linkedBinding != null) {
          fail("Already have a linked binding of: " + linkedBinding + ", and now want to add: " + b);
        } else {
          linkedBinding = (LinkedKeyBinding)b;
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

  /**
   * With overrides, we should get the union of all multibindings.
   */
  public void testModuleOverrideAndMultibindings() {
    Module ab = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");
      }
    };
    Module cd = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("C");
        multibinder.addBinding().toInstance("D");
      }
    };
    Module ef = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("E");
        multibinder.addBinding().toInstance("F");
      }
    };

    Module abcd = Modules.override(ab).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(ImmutableSet.of("A", "B", "C", "D", "E", "F"),
        injector.getInstance(Key.get(setOfString)));

    assertSetVisitor(Key.get(setOfString), stringType, setOf(abcd, ef), BOTH, false, 0,
        instance("A"), instance("B"), instance("C"), instance("D"), instance("E"), instance("F"));
  }

  /**
   * With overrides, we should get the union of all multibindings.
   */
  public void testModuleOverrideAndMultibindingsWithPermitDuplicates() {
    Module abc = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("A");
        multibinder.addBinding().toInstance("B");
        multibinder.addBinding().toInstance("C");
        multibinder.permitDuplicates();
      }
    };
    Module cd = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("C");
        multibinder.addBinding().toInstance("D");
        multibinder.permitDuplicates();
      }
    };
    Module ef = new AbstractModule() {
      protected void configure() {
        Multibinder<String> multibinder = Multibinder.newSetBinder(binder(), String.class);
        multibinder.addBinding().toInstance("E");
        multibinder.addBinding().toInstance("F");
        multibinder.permitDuplicates();
      }
    };

    Module abcd = Modules.override(abc).with(cd);
    Injector injector = Guice.createInjector(abcd, ef);
    assertEquals(ImmutableSet.of("A", "B", "C", "D", "E", "F"),
        injector.getInstance(Key.get(setOfString)));

    assertSetVisitor(Key.get(setOfString), stringType, setOf(abcd, ef), BOTH, true, 0,
        instance("A"), instance("B"), instance("C"), instance("C"), instance("D"), instance("E"), instance("F"));
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
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override public void configure() {
        Multibinder<Integer> mb1 = Multibinder.newSetBinder(binder(), Integer.class, Marker.class);
        Multibinder<Integer> mb2 = Multibinder.newSetBinder(binder(), Integer.class, marker);
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
    Set<Integer> expected = new HashSet<Integer>();
    expected.add(1);
    expected.add(2);
    assertEquals(expected, s1);
  }
}
