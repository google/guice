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

package com.google.inject.commands;

import com.google.inject.*;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.TypeConverter;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.*;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class CommandRecorderTest extends TestCase {

  protected EarlyRequestsProvider earlyRequestProvider = new EarlyRequestsProvider() {
    public <T> T get(Key<T> key) {
      throw new AssertionFailedError();
    }
  };

  private CommandRecorder commandRecorder = new CommandRecorder(earlyRequestProvider);

  public void testAddMessageErrorCommand() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            addError("Message", "A", "B", "C");
          }
        },

        new FailingVisitor() {
          @Override public Void visitAddMessageError(AddMessageErrorCommand command) {
            assertEquals(Arrays.asList("A", "B", "C"), command.getArguments());
            assertEquals("Message", command.getMessage());
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

        new FailingVisitor() {
          @Override public Void visitAddError(AddThrowableErrorCommand command) {
            assertEquals("A", command.getThrowable().getMessage());
            return null;
          }
        }
    );
  }

  public void testBindConstantAnnotations() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindConstant().annotatedWith(SampleAnnotation.class).to("A");
            bindConstant().annotatedWith(Names.named("Bee")).to("B");
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("Bee")), command.getKey());
            assertEquals("B", command.getTarget().get(null));
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
            bindConstant().annotatedWith(Names.named("Class")).to(Iterator.class);
            bindConstant().annotatedWith(Names.named("Enum")).to(CoinSide.TAILS);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("String")), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Integer.class, Names.named("int")), command.getKey());
            assertEquals(2, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Long.class, Names.named("long")), command.getKey());
            assertEquals(3L, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Boolean.class, Names.named("boolean")), command.getKey());
            assertEquals(false, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Double.class, Names.named("double")), command.getKey());
            assertEquals(5.0d, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Float.class, Names.named("float")), command.getKey());
            assertEquals(6.0f, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Short.class, Names.named("short")), command.getKey());
            assertEquals((short) 7, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Character.class, Names.named("char")), command.getKey());
            assertEquals('h', command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(Class.class, Names.named("Class")), command.getKey());
            assertEquals(Iterator.class, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindConstant(BindConstantCommand command) {
            assertEquals(Key.get(CoinSide.class, Names.named("Enum")), command.getKey());
            assertEquals(CoinSide.TAILS, command.getTarget().get(null));
            return null;
          }
        }
    );
  }

  public void testBindKeysNoAnnotations() {
    FailingVisitor keyChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toInstance("A");
            bind(new TypeLiteral<String>() {
            }).toInstance("B");
            bind(Key.get(String.class)).toInstance("C");
          }
        },
        keyChecker,
        keyChecker,
        keyChecker
    );
  }

  public void testBindKeysWithAnnotationType() {
    FailingVisitor annotationChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(SampleAnnotation.class).toInstance("A");
            bind(new TypeLiteral<String>() {
            }).annotatedWith(SampleAnnotation.class).toInstance("B");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindKeysWithAnnotationInstance() {
    FailingVisitor annotationChecker = new FailingVisitor() {
      @Override public Void visitBind(BindCommand command) {
        assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
        return null;
      }
    };


    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).annotatedWith(Names.named("a")).toInstance("B");
            bind(new TypeLiteral<String>() {
            }).annotatedWith(Names.named("a")).toInstance("C");
          }
        },
        annotationChecker,
        annotationChecker
    );
  }

  public void testBindToProvider() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(String.class).toProvider(new Provider<String>() {
              public String get() {
                return "A";
              }
            });
            bind(List.class).toProvider(ListProvider.class);
            bind(Collection.class).toProvider(Key.get(ListProvider.class));
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().getProvider(null).get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertNull(command.getTarget().get(null));
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Collection.class), command.getKey());
            assertNull(command.getTarget().get(null));
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey(null));
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
            bind(Map.class).to(new TypeLiteral<HashMap<Integer, String>>() { });
            bind(Set.class).to(Key.get(TreeSet.class, SampleAnnotation.class));
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Key.get(ArrayList.class), command.getTarget().getKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Key.get(new TypeLiteral<HashMap<Integer, String>>() {}), command.getTarget().getKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertEquals(Key.get(TreeSet.class, SampleAnnotation.class), command.getTarget().getKey(null));
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

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        }
    );
  }

  public void testBindInScopes() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bind(List.class).to(ArrayList.class).in(Scopes.SINGLETON);
            bind(Map.class).to(HashMap.class).in(Singleton.class);
            bind(Set.class).to(TreeSet.class).asEagerSingleton();
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Scopes.SINGLETON, command.getScoping().getScope(null));
            assertNull(command.getScoping().getScopeAnnotation(null));
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Singleton.class, command.getScoping().getScopeAnnotation(null));
            assertNull(command.getScoping().getScope(null));
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBind(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertNull(command.getScoping().getScopeAnnotation(null));
            assertNull(command.getScoping().getScope(null));
            assertTrue(command.getScoping().isEagerSingleton());
            return null;
          }
        }
    );
  }

  public void testBindIntercepor() {
    final Matcher<Class> classMatcher = Matchers.subclassesOf(List.class);
    final Matcher<Object> methodMatcher = Matchers.any();
    final MethodInterceptor methodInterceptor = new MethodInterceptor() {
      public Object invoke(MethodInvocation methodInvocation) {
        return null;
      }
    };

    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindInterceptor(classMatcher, methodMatcher, methodInterceptor);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindInterceptor(BindInterceptorCommand command) {
            assertSame(classMatcher, command.getClassMatcher());
            assertSame(methodMatcher, command.getMethodMatcher());
            assertEquals(Arrays.asList(methodInterceptor), command.getInterceptors());
            return null;
          }
        }
    );
  }

  public void testBindScope() {
    checkModule(
        new AbstractModule() {
          protected void configure() {
            bindScope(SampleAnnotation.class, Scopes.NO_SCOPE);
          }
        },

        new FailingVisitor() {
          @Override public Void visitBindScope(BindScopeCommand command) {
            assertSame(SampleAnnotation.class, command.getAnnotationType());
            assertSame(Scopes.NO_SCOPE, command.getScope());
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

        new FailingVisitor() {
          @Override public Void visitConvertToTypes(ConvertToTypesCommand command) {
            assertSame(typeConverter, command.getTypeConverter());
            assertSame(Matchers.any(), command.getTypeMatcher());
            return null;
          }
        }
    );
  }

  public void testGetProvider() {
    final List<Key> calls = new ArrayList<Key>();

    earlyRequestProvider = new EarlyRequestsProvider() {
      @SuppressWarnings({"unchecked"})
      public <T> T get(Key<T> key) {
        calls.add(key);
        return (T) "A";
      }
    };

    commandRecorder = new CommandRecorder(earlyRequestProvider);

    checkModule(
        new AbstractModule() {
          protected void configure() {
            Provider<String> keyGetProvider = getProvider(Key.get(String.class, SampleAnnotation.class));
            assertEquals("A", keyGetProvider.get());
            assertEquals(Key.get(String.class, SampleAnnotation.class), calls.get(0));

            Provider<String> typeGetProvider = getProvider(String.class);
            assertEquals("A", typeGetProvider.get());
            assertEquals(Key.get(String.class), calls.get(1));
            assertEquals(2, calls.size());
          }
        },

        new FailingVisitor() {
          @Override public Void visitGetProvider(GetProviderCommand command) {
            assertEquals(Key.get(String.class, SampleAnnotation.class), command.getKey());
            assertEquals(earlyRequestProvider, command.getEarlyRequestsProvider());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitGetProvider(GetProviderCommand command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals(earlyRequestProvider, command.getEarlyRequestsProvider());
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

        new FailingVisitor() {
          @Override public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
            assertEquals(Arrays.asList(ArrayList.class), command.getTypes());
            return null;
          }
        }
    );
  }

  public void testMalformedBindCommands() {
    CommandRecorder recorder = new CommandRecorder(earlyRequestProvider);

    recorder.recordCommands(new AbstractModule() {
      protected void configure() {
        AnnotatedBindingBuilder<String> abb = bind(String.class);
        abb.annotatedWith(SampleAnnotation.class);
        try {
          abb.annotatedWith(Names.named("A"));
          fail();
        } catch(IllegalStateException expected) {
        }
      }
    });

    recorder.recordCommands(new AbstractModule() {
      protected void configure() {
        AnnotatedBindingBuilder<String> abb = bind(String.class);
        abb.toInstance("A");
        try {
          abb.toInstance("B");
          fail();
        } catch(IllegalStateException expected) {
        }
      }
    });

    recorder.recordCommands(new AbstractModule() {
      protected void configure() {
        ScopedBindingBuilder sbb = bind(List.class).to(ArrayList.class);
        sbb.in(Scopes.NO_SCOPE);
        try {
          sbb.asEagerSingleton();
          fail();
        } catch(IllegalStateException expected) {
        }
      }
    });
  }

  public void testMalformedBindConstantCommands() {
    CommandRecorder recorder = new CommandRecorder(earlyRequestProvider);

    recorder.recordCommands(new AbstractModule() {
      protected void configure() {
        AnnotatedConstantBindingBuilder cbb = bindConstant();
        cbb.annotatedWith(SampleAnnotation.class);
        try {
          cbb.annotatedWith(Names.named("A"));
          fail();
        } catch(IllegalStateException expected) {
        }
      }
    });

    recorder.recordCommands(new AbstractModule() {
      protected void configure() {
        ConstantBindingBuilder cbb = bindConstant().annotatedWith(SampleAnnotation.class);
        cbb.to("A");
        try {
          cbb.to("B");
          fail();
        } catch(IllegalStateException expected) {
        }
      }
    });
  }

  /**
   * Ensures the module performs the commands consistent with {@code visitors}.
   */
  protected void checkModule(Module module, Command.Visitor<?>... visitors) {
    List<Command> commands = commandRecorder.recordCommands(module);

    assertEquals(commands.size(), visitors.length);

    for (int i = 0; i < visitors.length; i++) {
      Command.Visitor<?> visitor = visitors[i];
      Command command = commands.get(i);
      command.acceptVisitor(visitor);
    }
  }

  private static class ListProvider implements Provider<List> {
    public List get() {
      return new ArrayList();
    }
  }

  private static class FailingVisitor implements Command.Visitor<Void> {
    public Void visitAddMessageError(AddMessageErrorCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitAddError(AddThrowableErrorCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindInterceptor(BindInterceptorCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindScope(BindScopeCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitRequestStaticInjection(RequestStaticInjectionCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitBindConstant(BindConstantCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitConvertToTypes(ConvertToTypesCommand command) {
      throw new AssertionFailedError();
    }

    public <T> Void visitBind(BindCommand<T> command) {
      throw new AssertionFailedError();
    }

    public Void visitGetProvider(GetProviderCommand command) {
      throw new AssertionFailedError();
    }
  }

  @Retention(RUNTIME)
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  public @interface SampleAnnotation { }

  public enum CoinSide { HEADS, TAILS }
}
