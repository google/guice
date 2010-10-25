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

package com.google.inject.spi;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.PrivateBinder;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.ImmutableSet;
import static com.google.inject.internal.util.Iterables.getOnlyElement;

import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ElementsTest extends TestCase {

  // Binder fidelity tests

  public void testAddMessageErrorCommand() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            addError("Message %s %d %s", "A", 5, "C");
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Message A 5 C", command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSources().toString(),
                ElementsTest.class.getName(), ".configure(ElementsTest.java:");
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testAddThrowableErrorCommand() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            addError(new Exception("A"));
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("A", command.getCause().getMessage());
            assertEquals(command.getMessage(),
                "An exception was caught and reported. Message: A");
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testErrorsAddedWhenExceptionsAreThrown() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            install(new AbstractModule() {
              protected void configure() {
                throw new RuntimeException("Throwing RuntimeException in AbstractModule.configure().");
              }
            });

            addError("Code after the exception still gets executed");
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Throwing RuntimeException in AbstractModule.configure().",
                command.getCause().getMessage());
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Code after the exception still gets executed",
                command.getMessage());
            return null;
          }
        }
    );
  }

  private <T> T getInstance(Binding<T> binding) {
    return binding.acceptTargetVisitor(Elements.<T>getInstanceVisitor());
  }

  public void testBindConstantAnnotations() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(SampleAnnotation.class).to("A");
            bindConstant().annotatedWith(Names.named("Bee")).to("B");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertEquals("A", getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(String.class, Names.named("Bee")), command.getKey());
            assertEquals("B", getInstance(command));
            return null;
          }
        }
    );
  }

  public void testBindConstantTypes() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(Names.named("String")).to("A");
            bindConstant().annotatedWith(Names.named("int")).to(2);
            bindConstant().annotatedWith(Names.named("long")).to(3L);
            bindConstant().annotatedWith(Names.named("boolean")).to(false);
            bindConstant().annotatedWith(Names.named("double")).to(5.0d);
            bindConstant().annotatedWith(Names.named("float")).to(6.0f);
            bindConstant().annotatedWith(Names.named("short")).to((short) 7);
            bindConstant().annotatedWith(Names.named("char")).to('h');
            bindConstant().annotatedWith(Names.named("byte")).to((byte) 8);
            bindConstant().annotatedWith(Names.named("Class")).to(Iterator.class);
            bindConstant().annotatedWith(Names.named("Enum")).to(CoinSide.TAILS);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(String.class, Names.named("String")), command.getKey());
            assertEquals("A", getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Integer.class, Names.named("int")), command.getKey());
            assertEquals(2, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Long.class, Names.named("long")), command.getKey());
            assertEquals(3L, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Boolean.class, Names.named("boolean")), command.getKey());
            assertEquals(false, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Double.class, Names.named("double")), command.getKey());
            assertEquals(5.0d, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Float.class, Names.named("float")), command.getKey());
            assertEquals(6.0f, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Short.class, Names.named("short")), command.getKey());
            assertEquals((short) 7, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Character.class, Names.named("char")), command.getKey());
            assertEquals('h', getInstance(command));
            return null;
          }
        },
        
        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Byte.class, Names.named("byte")), command.getKey());
            assertEquals((byte) 8, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(Class.class, Names.named("Class")), command.getKey());
            assertEquals(Iterator.class, getInstance(command));
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(CoinSide.class, Names.named("Enum")), command.getKey());
            assertEquals(CoinSide.TAILS, getInstance(command));
            return null;
          }
        }
    );
  }

  public void testBindKeysNoAnnotations() {
    FailingElementVisitor keyChecker = new FailingElementVisitor() {
      @Override public <T> Void visit(Binding<T> command) {
        assertEquals(Key.get(String.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(new TypeLiteral<String>() {}).toInstance("B");
            bind(Key.get(String.class)).toInstance("C");
          }
        },
        keyChecker,
        keyChecker,
        keyChecker
    );
  }

  public void testBindKeysWithAnnotationType() {
    FailingElementVisitor annotationChecker = new FailingElementVisitor() {
      @Override public <T> Void visit(Binding<T> command) {
        assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(SampleAnnotation.class).toInstance("A");
            bind(new TypeLiteral<String>() {}).annotatedWith(SampleAnnotation.class).toInstance("B");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindKeysWithAnnotationInstance() {
    FailingElementVisitor annotationChecker = new FailingElementVisitor() {
      @Override public <T> Void visit(Binding<T> command) {
        assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
        return null;
      }
    };


    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(Names.named("a")).toInstance("B");
            bind(new TypeLiteral<String>() {}).annotatedWith(Names.named("a")).toInstance("C");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindToProvider() {
    final Provider<String> aProvider = new Provider<String>() {
      public String get() {
        return "A";
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toProvider(aProvider);
            bind(List.class).toProvider(ListProvider.class);
            bind(Collection.class).toProvider(Key.get(ListProvider.class));
            bind(Iterable.class).toProvider(new TypeLiteral<TProvider<List>>() {});
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof ProviderInstanceBinding);
            assertEquals(Key.get(String.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(
                  ProviderInstanceBinding<? extends T> binding) {
                assertSame(aProvider, binding.getProviderInstance());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof ProviderKeyBinding);
            assertEquals(Key.get(List.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ProviderKeyBinding<? extends T> binding) {
                assertEquals(Key.get(ListProvider.class), binding.getProviderKey());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof ProviderKeyBinding);
            assertEquals(Key.get(Collection.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ProviderKeyBinding<? extends T> binding) {
                assertEquals(Key.get(ListProvider.class), binding.getProviderKey());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof ProviderKeyBinding);
            assertEquals(Key.get(Iterable.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ProviderKeyBinding<? extends T> binding) {
                assertEquals(new Key<TProvider<List>>() {}, binding.getProviderKey());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testBindToLinkedBinding() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(List.class).to(ArrayList.class);
            bind(Map.class).to(new TypeLiteral<HashMap<Integer, String>>() {});
            bind(Set.class).to(Key.get(TreeSet.class, SampleAnnotation.class));
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof LinkedKeyBinding);
            assertEquals(Key.get(List.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(LinkedKeyBinding<? extends T> binding) {
                assertEquals(Key.get(ArrayList.class), binding.getLinkedKey());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof LinkedKeyBinding);
            assertEquals(Key.get(Map.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(LinkedKeyBinding<? extends T> binding) {
                assertEquals(Key.get(new TypeLiteral<HashMap<Integer, String>>() {}),
                    binding.getLinkedKey());
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof LinkedKeyBinding);
            assertEquals(Key.get(Set.class), command.getKey());
            command.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(LinkedKeyBinding<? extends T> binding) {
                assertEquals(Key.get(TreeSet.class, SampleAnnotation.class),
                    binding.getLinkedKey());
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testBindToInstance() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertTrue(command instanceof InstanceBinding);
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", getInstance(command));
            return null;
          }
        }
    );
  }

  public void testBindInScopes() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class);
            bind(List.class).to(ArrayList.class).in(Scopes.SINGLETON);
            bind(Map.class).to(HashMap.class).in(Singleton.class);
            bind(Set.class).to(TreeSet.class).asEagerSingleton();
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitNoScoping() {
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitScope(Scope scope) {
                assertEquals(Scopes.SINGLETON, scope);
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitScopeAnnotation(Class<? extends Annotation> annotation) {
                assertEquals(Singleton.class, annotation);
                return null;
              }
            });
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            command.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              public Void visitEagerSingleton() {
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testBindToInstanceInScope() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedBindingBuilder<String> b = bind(String.class);
            b.toInstance("A");
            b.in(Singleton.class);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Setting the scope is not permitted when binding to a single instance.",
                command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
      );
  }

  public void testBindToInstanceScope() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertEquals(Key.get(String.class), binding.getKey());
            binding.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              public Void visitEagerSingleton() {
                return null;
              }
            });
            return null;
          }
        }
      );
  }

  /*if[AOP]*/
  public void testBindIntercepor() {
    final Matcher<Class> classMatcher = Matchers.subclassesOf(List.class);
    final Matcher<Object> methodMatcher = Matchers.any();
    final org.aopalliance.intercept.MethodInterceptor methodInterceptor
        = new org.aopalliance.intercept.MethodInterceptor() {
      public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation) {
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindInterceptor(classMatcher, methodMatcher, methodInterceptor);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(InterceptorBinding command) {
            assertSame(classMatcher, command.getClassMatcher());
            assertSame(methodMatcher, command.getMethodMatcher());
            assertEquals(Arrays.asList(methodInterceptor), command.getInterceptors());
            return null;
          }
        }
    );
  }
  /*end[AOP]*/

  public void testBindScope() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindScope(SampleAnnotation.class, Scopes.NO_SCOPE);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(ScopeBinding command) {
            assertSame(SampleAnnotation.class, command.getAnnotationType());
            assertSame(Scopes.NO_SCOPE, command.getScope());
            return null;
          }
        }
    );
  }

  public void testBindListener() {
    final Matcher<Object> typeMatcher = Matchers.only(TypeLiteral.get(String.class));
    final TypeListener listener = new TypeListener() {
      public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        throw new UnsupportedOperationException();
      }
    };
    
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindListener(typeMatcher, listener);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(TypeListenerBinding binding) {
            assertSame(typeMatcher, binding.getTypeMatcher());
            assertSame(listener, binding.getListener());
            return null;
          }
        }
    );
  }

  public void testConvertToTypes() {
    final TypeConverter typeConverter = new TypeConverter() {
      public Object convert(String value, TypeLiteral<?> toType) {
        return value;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            convertToTypes(Matchers.any(), typeConverter);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(TypeConverterBinding command) {
            assertSame(typeConverter, command.getTypeConverter());
            assertSame(Matchers.any(), command.getTypeMatcher());
            return null;
          }
        }
    );
  }

  public void testGetProvider() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            Provider<String> keyGetProvider
                = getProvider(Key.get(String.class, SampleAnnotation.class));
            try {
              keyGetProvider.get();
            } catch (IllegalStateException e) {
              assertEquals("This Provider cannot be used until the Injector has been created.",
                  e.getMessage());
            }

            Provider<String> typeGetProvider = getProvider(String.class);
            try {
              typeGetProvider.get();
            } catch (IllegalStateException e) {
              assertEquals("This Provider cannot be used until the Injector has been created.",
                  e.getMessage());
            }
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(ProviderLookup<T> command) {
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertNull(command.getDelegate());
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(ProviderLookup<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertNull(command.getDelegate());
            return null;
          }
        }
    );
  }
  
  public void testElementInitialization() {
    final AtomicReference<Provider<String>> providerFromBinder
        = new AtomicReference<Provider<String>>();
    final AtomicReference<MembersInjector<String>> membersInjectorFromBinder
        = new AtomicReference<MembersInjector<String>>();

    final AtomicReference<String> lastInjected = new AtomicReference<String>();
    final MembersInjector<String> stringInjector = new MembersInjector<String>() {
      public void injectMembers(String instance) {
        lastInjected.set(instance);
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            providerFromBinder.set(getProvider(String.class));
            membersInjectorFromBinder.set(getMembersInjector(String.class));
          }
        },

        new FailingElementVisitor() {
          public <T> Void visit(ProviderLookup<T> providerLookup) {
            @SuppressWarnings("unchecked") // we know that T is a String here
            ProviderLookup<String> stringLookup = (ProviderLookup<String>) providerLookup;
            stringLookup.initializeDelegate(Providers.of("out"));

            assertEquals("out", providerFromBinder.get().get());
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(MembersInjectorLookup<T> lookup) {
            @SuppressWarnings("unchecked") // we know that T is a String here
            MembersInjectorLookup<String> stringLookup = (MembersInjectorLookup<String>) lookup;
            stringLookup.initializeDelegate(stringInjector);

            membersInjectorFromBinder.get().injectMembers("in");
            assertEquals("in", lastInjected.get());
            return null;
          }
        });
  }

  public void testGetMembersInjector() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            MembersInjector<A<String>> typeMembersInjector
                = getMembersInjector(new TypeLiteral<A<String>>() {});
            try {
              typeMembersInjector.injectMembers(new A<String>());
            } catch (IllegalStateException e) {
              assertEquals(
                  "This MembersInjector cannot be used until the Injector has been created.",
                  e.getMessage());
            }

            MembersInjector<String> classMembersInjector = getMembersInjector(String.class);
            try {
              classMembersInjector.injectMembers("hello");
            } catch (IllegalStateException e) {
              assertEquals(
                  "This MembersInjector cannot be used until the Injector has been created.",
                  e.getMessage());
            }
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(MembersInjectorLookup<T> command) {
            assertEquals(new TypeLiteral<A<String>>() {}, command.getType());
            assertNull(command.getDelegate());
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(MembersInjectorLookup<T> command) {
            assertEquals(TypeLiteral.get(String.class), command.getType());
            assertNull(command.getDelegate());
            return null;
          }
        }
    );
  }

  public void testRequestInjection() {
    final Object firstObject = new Object();
    final Object secondObject = new Object();

    checkModule(
        new AbstractModule() {
          protected void configure() {
            requestInjection(firstObject);
            requestInjection(secondObject);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(InjectionRequest<?> command) {
            assertEquals(firstObject, command.getInstance());
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(InjectionRequest<?> command) {
            assertEquals(secondObject, command.getInstance());
            return null;
          }
        }
    );
  }

  public void testRequestStaticInjection() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            requestStaticInjection(ArrayList.class);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(StaticInjectionRequest command) {
            assertEquals(ArrayList.class, command.getType());
            return null;
          }
        }
    );
  }

  public void testNewPrivateBinder() {
    final Key<Collection> collection = Key.get(Collection.class, SampleAnnotation.class);
    final Key<ArrayList> arrayList = Key.get(ArrayList.class);
    final ImmutableSet<Key<?>> collections = ImmutableSet.<Key<?>>of(arrayList, collection);

    final Key<?> a = Key.get(String.class, Names.named("a"));
    final Key<?> b = Key.get(String.class, Names.named("b"));
    final ImmutableSet<Key<?>> ab = ImmutableSet.of(a, b);

    checkModule(
        new AbstractModule() {
          protected void configure() {
            PrivateBinder one = binder().newPrivateBinder();
            one.expose(ArrayList.class);
            one.expose(Collection.class).annotatedWith(SampleAnnotation.class);
            one.bind(List.class).to(ArrayList.class);

            PrivateBinder two = binder().withSource("1 ElementsTest.java")
                .newPrivateBinder().withSource("2 ElementsTest.java");
            two.expose(String.class).annotatedWith(Names.named("a"));
            two.expose(b);
            two.bind(List.class).to(ArrayList.class);
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(PrivateElements one) {
            assertEquals(collections, one.getExposedKeys());
            checkElements(one.getElements(),
                new FailingElementVisitor() {
                  @Override public <T> Void visit(Binding<T> binding) {
                    assertEquals(Key.get(List.class), binding.getKey());
                    return null;
                  }
                }
            );
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(PrivateElements two) {
            assertEquals(ab, two.getExposedKeys());
            assertEquals("1 ElementsTest.java", two.getSource());
            checkElements(two.getElements(),
                new FailingElementVisitor() {
                  @Override public <T> Void visit(Binding<T> binding) {
                    assertEquals("2 ElementsTest.java", binding.getSource());
                    assertEquals(Key.get(List.class), binding.getKey());
                    return null;
                  }
                }
            );
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleAnnotationsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedBindingBuilder<String> abb = bind(String.class);
            abb.annotatedWith(SampleAnnotation.class);
            abb.annotatedWith(Names.named("A"));
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("More than one annotation is specified for this binding.",
                command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleTargetsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedBindingBuilder<String> abb = bind(String.class);
            abb.toInstance("A");
            abb.toInstance("B");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Implementation is set more than once.", command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testBindWithMultipleScopesAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            ScopedBindingBuilder sbb = bind(List.class).to(ArrayList.class);
            sbb.in(Scopes.NO_SCOPE);
            sbb.asEagerSingleton();
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("Scope is set more than once.", command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testBindConstantWithMultipleAnnotationsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            AnnotatedConstantBindingBuilder cbb = bindConstant();
            cbb.annotatedWith(SampleAnnotation.class).to("A");
            cbb.annotatedWith(Names.named("A"));
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message command) {
            assertEquals("More than one annotation is specified for this binding.",
                command.getMessage());
            assertNull(command.getCause());
            assertContains(command.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testBindConstantWithMultipleTargetsAddsError() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            ConstantBindingBuilder cbb = bindConstant().annotatedWith(SampleAnnotation.class);
            cbb.to("A");
            cbb.to("B");
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> command) {
            return null;
          }
        },

        new FailingElementVisitor() {
          @Override public Void visit(Message message) {
            assertEquals("Constant value is set more than once.", message.getMessage());
            assertNull(message.getCause());
            assertContains(message.getSource(), "ElementsTest.java");
            return null;
          }
        }
    );
  }

  public void testBindToConstructor() throws NoSuchMethodException, NoSuchFieldException {
    final Constructor<A> aConstructor = A.class.getDeclaredConstructor();
    final Constructor<B> bConstructor = B.class.getDeclaredConstructor(Object.class);
    final Field field = B.class.getDeclaredField("stage");

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(A.class).toConstructor(aConstructor);
            bind(B.class).toConstructor(bConstructor, new TypeLiteral<B<Integer>>() {})
                .in(Singleton.class);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertEquals(new Key<A>() {}, binding.getKey());

            return binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ConstructorBinding<? extends T> constructorBinding) {
                InjectionPoint injectionPoint = constructorBinding.getConstructor();
                assertEquals(aConstructor, injectionPoint.getMember());
                assertEquals(new TypeLiteral<A>() {}, injectionPoint.getDeclaringType());
                return null;
              }
            });
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertEquals(new Key<B>() {}, binding.getKey());
            binding.acceptScopingVisitor(new FailingBindingScopingVisitor() {
              @Override public Void visitScopeAnnotation(Class<? extends Annotation> annotation) {
                assertEquals(Singleton.class, annotation);
                return null;
              }
            });

            binding.acceptTargetVisitor(new FailingTargetVisitor<T>() {
              @Override public Void visit(ConstructorBinding<? extends T> constructorBinding) {
                assertEquals(bConstructor, constructorBinding.getConstructor().getMember());
                assertEquals(Key.get(Integer.class),
                    getOnlyElement(constructorBinding.getConstructor().getDependencies()).getKey());
                assertEquals(field,
                    getOnlyElement(constructorBinding.getInjectableMembers()).getMember());
                assertEquals(2, constructorBinding.getDependencies().size());
/*if[AOP]*/
                assertEquals(ImmutableMap.of(), constructorBinding.getMethodInterceptors());
/*end[AOP]*/
                return null;
              }
            });
            return null;
          }
        }
    );
  }

  public void testBindToMalformedConstructor() throws NoSuchMethodException, NoSuchFieldException {
    final Constructor<C> constructor = C.class.getDeclaredConstructor(Integer.class);

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(C.class).toConstructor(constructor);
          }
        },

        new FailingElementVisitor() {
          @Override public <T> Void visit(Binding<T> binding) {
            assertEquals(Key.get(C.class), binding.getKey());
            assertTrue(binding instanceof UntargettedBinding);
            return null;
          }
        },

        new ExternalFailureVisitor() {
          @Override public Void visit(Message message) {
            assertContains(message.getMessage(),
                C.class.getName() + ".a has more than one annotation ",
                Named.class.getName(), SampleAnnotation.class.getName());
            return null;
          }
        },

        new ExternalFailureVisitor() {
          @Override public Void visit(Message message) {
            assertContains(message.getMessage(),
                C.class.getName() + ".<init>() has more than one annotation ",
                Named.class.getName(), SampleAnnotation.class.getName());
            return null;
          }
        }
    );
  }

  // Business logic tests

  public void testModulesAreInstalledAtMostOnce() {
    final AtomicInteger aConfigureCount = new AtomicInteger(0);
    final Module a = new AbstractModule() {
      public void configure() {
        aConfigureCount.incrementAndGet();
      }
    };

    Elements.getElements(a, a);
    assertEquals(1, aConfigureCount.get());

    aConfigureCount.set(0);
    Module b = new AbstractModule() {
      protected void configure() {
        install(a);
        install(a);
      }
    };

    Elements.getElements(b);
    assertEquals(1, aConfigureCount.get());
  }


  /**
   * Ensures the module performs the commands consistent with {@code visitors}.
   */
  protected void checkModule(Module module, ElementVisitor<?>... visitors) {
    List<Element> elements = Elements.getElements(module);
    assertEquals(elements.size(), visitors.length);
    checkElements(elements, visitors);
  }

  protected void checkElements(List<Element> elements, ElementVisitor<?>... visitors) {
    for (int i = 0; i < visitors.length; i++) {
      ElementVisitor<?> visitor = visitors[i];
      Element element = elements.get(i);
      if (!(visitor instanceof ExternalFailureVisitor)) {
        assertContains(element.getSource().toString(), "ElementsTest.java");
      }
      element.acceptVisitor(visitor);
    }
  }

  private static class ListProvider implements Provider<List> {
    public List get() {
      return new ArrayList();
    }
  }

  private static class TProvider<T> implements Provider<T> {
    public T get() {
      return null;
    }
  }

  /**
   * By extending this interface rather than FailingElementVisitor, the source of the error doesn't
   * need to contain the string {@code ElementsTest.java}.
   */
  abstract class ExternalFailureVisitor extends FailingElementVisitor {}

  @Retention(RUNTIME)
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  public @interface SampleAnnotation { }

  public enum CoinSide { HEADS, TAILS }

  static class A<T> {
    @Inject Stage stage;
  }

  static class B<T> {
    @Inject Stage stage;
    B(T t) {}
  }

  static class C {
    @Inject @Named("foo") @SampleAnnotation String a;
    C(@Named("bar") @SampleAnnotation Integer b) {}
  }
}
