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

package com.google.inject.spi;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static com.google.inject.Asserts.isIncludeStackTraceComplete;
import static java.util.Comparator.comparing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.name.Names;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
public class SpiBindingsTest extends TestCase {

  public void testBindConstant() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(Names.named("one")).to(1);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
            return null;
          }
        });
  }

  public void testToInstanceBinding() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            checkBindingSource(binding);
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(InstanceBinding<? extends T> binding) {
                    assertEquals("A", binding.getInstance());
                    return null;
                  }
                });
            binding.acceptScopingVisitor(
                new FailingBindingScopingVisitor() {
                  @Override
                  public Void visitEagerSingleton() {
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testToProviderBinding() {
    final Provider<String> stringProvider = new StringProvider();

    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toProvider(stringProvider);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ProviderInstanceBinding);
            checkBindingSource(binding);
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(ProviderInstanceBinding<? extends T> binding) {
                    assertSame(stringProvider, binding.getUserSuppliedProvider());
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testToProviderKeyBinding() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toProvider(StringProvider.class);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ProviderKeyBinding);
            checkBindingSource(binding);
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(ProviderKeyBinding<? extends T> binding) {
                    assertEquals(Key.get(StringProvider.class), binding.getProviderKey());
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testToKeyBinding() {
    final Key<String> aKey = Key.get(String.class, Names.named("a"));
    final Key<String> bKey = Key.get(String.class, Names.named("b"));

    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(aKey).to(bKey);
            bind(bKey).toInstance("B");
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof LinkedKeyBinding);
            checkBindingSource(binding);
            assertEquals(aKey, binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(LinkedKeyBinding<? extends T> binding) {
                    assertEquals(bKey, binding.getLinkedKey());
                    return null;
                  }
                });
            return null;
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertEquals(bKey, binding.getKey());
            return null;
          }
        });
  }

  public void testToConstructorBinding() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(D.class);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof ConstructorBinding);
            checkBindingSource(binding);
            assertEquals(Key.get(D.class), binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(ConstructorBinding<? extends T> binding) {
                    Constructor<?> expected = D.class.getDeclaredConstructors()[0];
                    assertEquals(expected, binding.getConstructor().getMember());
                    assertEquals(ImmutableSet.<InjectionPoint>of(), binding.getInjectableMembers());
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testConstantBinding() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bindConstant().annotatedWith(Names.named("one")).to(1);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> binding) {
            assertTrue(binding instanceof InstanceBinding);
            checkBindingSource(binding);
            assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
            binding.acceptTargetVisitor(
                new FailingTargetVisitor<T>() {
                  @Override
                  public Void visit(InstanceBinding<? extends T> binding) {
                    assertEquals(1, binding.getInstance());
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testConvertedConstantBinding() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(Names.named("one")).to("1");
              }
            });

    Binding<Integer> binding = injector.getBinding(Key.get(Integer.class, Names.named("one")));
    assertEquals(Key.get(Integer.class, Names.named("one")), binding.getKey());
    checkBindingSource(binding);
    assertTrue(binding instanceof ConvertedConstantBinding);
    binding.acceptTargetVisitor(
        new FailingTargetVisitor<Integer>() {
          @Override
          public Void visit(ConvertedConstantBinding<? extends Integer> binding) {
            assertEquals((Integer) 1, binding.getValue());
            assertEquals(Key.get(String.class, Names.named("one")), binding.getSourceKey());
            return null;
          }
        });
  }

  public void testProviderBinding() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("A");
              }
            });

    Key<Provider<String>> providerOfStringKey = new Key<Provider<String>>() {};
    Binding<Provider<String>> binding = injector.getBinding(providerOfStringKey);
    assertEquals(providerOfStringKey, binding.getKey());
    checkBindingSource(binding);
    assertTrue(binding instanceof ProviderBinding);
    binding.acceptTargetVisitor(
        new FailingTargetVisitor<Provider<String>>() {
          @Override
          public Void visit(ProviderBinding<? extends Provider<String>> binding) {
            assertEquals(Key.get(String.class), binding.getProvidedKey());
            return null;
          }
        });
  }

  public void testScopes() {
    checkInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class)
                .annotatedWith(Names.named("a"))
                .toProvider(StringProvider.class)
                .in(Singleton.class);
            bind(String.class)
                .annotatedWith(Names.named("b"))
                .toProvider(StringProvider.class)
                .in(Scopes.SINGLETON);
            bind(String.class)
                .annotatedWith(Names.named("c"))
                .toProvider(StringProvider.class)
                .asEagerSingleton();
            bind(String.class).annotatedWith(Names.named("d")).toProvider(StringProvider.class);
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
            command.acceptScopingVisitor(
                new FailingBindingScopingVisitor() {
                  @Override
                  public Void visitScope(Scope scope) {
                    // even though we bound with an annotation, the injector always uses instances
                    assertSame(Scopes.SINGLETON, scope);
                    return null;
                  }
                });
            return null;
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("b")), command.getKey());
            command.acceptScopingVisitor(
                new FailingBindingScopingVisitor() {
                  @Override
                  public Void visitScope(Scope scope) {
                    assertSame(Scopes.SINGLETON, scope);
                    return null;
                  }
                });
            return null;
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("c")), command.getKey());
            command.acceptScopingVisitor(
                new FailingBindingScopingVisitor() {
                  @Override
                  public Void visitEagerSingleton() {
                    return null;
                  }
                });
            return null;
          }
        },
        new FailingElementVisitor() {
          @Override
          public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class, Names.named("d")), command.getKey());
            command.acceptScopingVisitor(
                new FailingBindingScopingVisitor() {
                  @Override
                  public Void visitNoScoping() {
                    return null;
                  }
                });
            return null;
          }
        });
  }

  public void testExtensionSpi() {
    final AtomicBoolean visiting = new AtomicBoolean(false);

    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class)
                    .toProvider(
                        new ProviderWithExtensionVisitor<String>() {
                          @SuppressWarnings("unchecked") // Safe because V is fixed to String
                          @Override
                          public <B, V> V acceptExtensionVisitor(
                              BindingTargetVisitor<B, V> visitor,
                              ProviderInstanceBinding<? extends B> binding) {
                            assertSame(this, binding.getUserSuppliedProvider());
                            // We can't always check for FailingSpiTargetVisitor,
                            // because constructing the injector visits here, and we need
                            // to process the binding as normal
                            if (visiting.get()) {
                              assertTrue(
                                  "visitor: " + visitor,
                                  visitor instanceof FailingSpiTargetVisitor);
                              return (V) "visited";
                            } else {
                              return visitor.visit(binding);
                            }
                          }

                          @Override
                          public String get() {
                            return "FooBar";
                          }
                        });
              }
            });

    visiting.set(true);

    // Check for Provider<String> binding -- that is still a ProviderBinding.
    Key<Provider<String>> providerOfStringKey = new Key<Provider<String>>() {};
    Binding<Provider<String>> providerBinding = injector.getBinding(providerOfStringKey);
    assertEquals(providerOfStringKey, providerBinding.getKey());
    checkBindingSource(providerBinding);
    assertTrue("binding: " + providerBinding, providerBinding instanceof ProviderBinding);
    providerBinding.acceptTargetVisitor(
        new FailingTargetVisitor<Provider<String>>() {
          @Override
          public Void visit(ProviderBinding<? extends Provider<String>> binding) {
            assertEquals(Key.get(String.class), binding.getProvidedKey());
            return null;
          }
        });

    // Check for String binding -- that one is ProviderInstanceBinding, and gets hooked
    Binding<String> binding = injector.getBinding(String.class);
    assertEquals(Key.get(String.class), binding.getKey());
    checkBindingSource(binding);
    assertTrue(binding instanceof ProviderInstanceBinding);
    assertEquals("visited", binding.acceptTargetVisitor(new FailingSpiTargetVisitor<String>()));
  }

  private static class FailingSpiTargetVisitor<T> extends DefaultBindingTargetVisitor<T, String> {
    @Override
    protected String visitOther(Binding<? extends T> binding) {
      throw new AssertionFailedError();
    }
  }

  public void checkBindingSource(Binding<?> binding) {
    assertContains(binding.getSource().toString(), getDeclaringSourcePart(getClass()));
    ElementSource source = (ElementSource) binding.getSource();
    assertFalse(source.getModuleClassNames().isEmpty());
    if (isIncludeStackTraceComplete()) {
      assertTrue(source.getStackTrace().length > 0);
    } else {
      assertEquals(0, source.getStackTrace().length);
    }
  }

  public void checkInjector(Module module, ElementVisitor<?>... visitors) {
    Injector injector = Guice.createInjector(module);

    List<Binding<?>> bindings = Lists.newArrayList(injector.getBindings().values());
    for (Iterator<Binding<?>> i = bindings.iterator(); i.hasNext(); ) {
      if (BUILT_IN_BINDINGS.contains(i.next().getKey())) {
        i.remove();
      }
    }

    Collections.sort(bindings, orderByKey);

    assertEquals(bindings.size(), visitors.length);

    for (int i = 0; i < visitors.length; i++) {
      ElementVisitor<?> visitor = visitors[i];
      Binding<?> binding = bindings.get(i);
      binding.acceptVisitor(visitor);
    }
  }

  private final ImmutableSet<Key<?>> BUILT_IN_BINDINGS =
      ImmutableSet.of(Key.get(Injector.class), Key.get(Stage.class), Key.get(Logger.class));

  private final Comparator<Binding<?>> orderByKey = comparing(arg -> arg.getKey().toString());

  private static class StringProvider implements Provider<String> {
    @Override
    public String get() {
      return "A";
    }
  }

  private static class C {}

  private static class D extends C {
    @Inject
    public D(Injector unused) {}
  }
}
