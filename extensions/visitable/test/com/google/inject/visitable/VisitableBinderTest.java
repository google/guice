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

package com.google.inject.visitable;

import com.google.inject.*;
import com.google.inject.name.Names;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.*;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class VisitableBinderTest extends TestCase {

  private EarlyRequestsProvider stubEarlyRequestProvider
      = new EarlyRequestsProvider() {
    public <T> T get(Key<T> key) {
      throw new AssertionFailedError();
    }
  };

  private VisitableBinder binder = new VisitableBinder(stubEarlyRequestProvider);

  public void testAddMessageErrorCommand() {
    Module module = new AbstractModule() {
      protected void configure() {
        addError("Message", "A", "B", "C");
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public Void visitAddMessageError(AddMessageErrorCommand command) {
            assertEquals("A", command.getArguments()[0]);
            assertEquals("B", command.getArguments()[1]);
            assertEquals("C", command.getArguments()[2]);
            assertEquals("Message", command.getMessage());
            return null;
          }
        }
    );
  }

  public void testAddThrowableErrorCommand() {
    Module module = new AbstractModule() {
      protected void configure() {
        addError(new Exception("A"));
      }
    };

    module.configure(binder);
    visitBindings(
        new FailingVisitor() {
          @Override public Void visitAddError(AddThrowableErrorCommand command) {
            assertEquals("A", command.getThrowable().getMessage());
            return null;
          }
        }
    );
  }

  public void testBindConstantAnnotations() {
    Module module = new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(TaggingAnnotation.class).to("A");
        bindConstant().annotatedWith(Names.named("Bee")).to("B");
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(String.class, TaggingAnnotation.class), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("Bee")), command.getKey());
            assertEquals("B", command.getTarget().get(null));
            return null;
          }
        }
    );
  }

  public void testBindConstantTypes() {
    Module module = new AbstractModule() {
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
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(String.class, Names.named("String")), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Integer.class, Names.named("int")), command.getKey());
            assertEquals(2, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Long.class, Names.named("long")), command.getKey());
            assertEquals(3L, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Boolean.class, Names.named("boolean")), command.getKey());
            assertEquals(false, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Double.class, Names.named("double")), command.getKey());
            assertEquals(5.0d, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Float.class, Names.named("float")), command.getKey());
            assertEquals(6.0f, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Short.class, Names.named("short")), command.getKey());
            assertEquals((short) 7, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Character.class, Names.named("char")), command.getKey());
            assertEquals('h', command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(Class.class, Names.named("Class")), command.getKey());
            assertEquals(Iterator.class, command.getTarget().get(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public Void visitConstantBinding(BindConstantCommand command) {
            assertEquals(Key.get(CoinSide.class, Names.named("Enum")), command.getKey());
            assertEquals(CoinSide.TAILS, command.getTarget().get(null));
            return null;
          }
        }
    );
  }

  public void testBindKeysNoAnnotations() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(new TypeLiteral<String>() {}).toInstance("B");
        bind(Key.get(String.class)).toInstance("C");
      }
    };

    module.configure(binder);

    for (Command command : binder.getCommands()) {
      command.acceptVisitor(
          new FailingVisitor() {
            @Override public Void visitBinding(BindCommand command) {
              assertEquals(Key.get(String.class), command.getKey());
              return null;
            }
          }
      );
    }
  }

  public void testBindKeysWithAnnotationType() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).annotatedWith(TaggingAnnotation.class).toInstance("A");
        bind(new TypeLiteral<String>() {}).annotatedWith(TaggingAnnotation.class).toInstance("B");
      }
    };

    module.configure(binder);

    for (Command command : binder.getCommands()) {
      command.acceptVisitor(
          new FailingVisitor() {
            @Override public Void visitBinding(BindCommand command) {
              assertEquals(Key.get(String.class, TaggingAnnotation.class), command.getKey());
              return null;
            }
          }
      );
    }
  }

  public void testBindKeysWithAnnotationInstance() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).annotatedWith(Names.named("a")).toInstance("B");
        bind(new TypeLiteral<String>() {}).annotatedWith(Names.named("a")).toInstance("C");
      }
    };

    module.configure(binder);

    for (Command command : binder.getCommands()) {
      command.acceptVisitor(
          new FailingVisitor() {
            @Override public Void visitBinding(BindCommand command) {
              assertEquals(Key.get(String.class, Names.named("a")), command.getKey());
              return null;
            }
          }
      );
    }
  }

  private static class ListProvider implements Provider<List> {
    public List get() {
      return new ArrayList();
    }
  }

  public void testBindToProvider() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toProvider(new Provider<String>() {
          public String get() {
            return "A";
          }
        });
        bind(List.class).toProvider(ListProvider.class);
        bind(Collection.class).toProvider(Key.get(ListProvider.class));
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().getProvider(null).get());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertNull(command.getTarget().get(null));
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(Collection.class), command.getKey());
            assertNull(command.getTarget().get(null));
            assertEquals(Key.get(ListProvider.class), command.getTarget().getProviderKey(null));
            return null;
          }
        }
    );
  }

  public void testBindToLinkedBinding() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(List.class).to(ArrayList.class);
        bind(Map.class).to(new TypeLiteral<HashMap<Integer, String>>() {});
        bind(Set.class).to(Key.get(TreeSet.class, TaggingAnnotation.class));
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Key.get(ArrayList.class), command.getTarget().getKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Key.get(new TypeLiteral<HashMap<Integer, String>>() {}), command.getTarget().getKey(null));
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertEquals(Key.get(TreeSet.class, TaggingAnnotation.class), command.getTarget().getKey(null));
            return null;
          }
        }
    );
  }

  public void testBindToInstance() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(String.class), command.getKey());
            assertEquals("A", command.getTarget().get(null));
            return null;
          }
        }
    );
  }

  public void testBindInScopes() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(List.class).to(ArrayList.class).in(Scopes.SINGLETON);
        bind(Map.class).to(HashMap.class).in(Singleton.class);
        bind(Set.class).to(TreeSet.class).asEagerSingleton();
      }
    };

    module.configure(binder);

    visitBindings(
        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(List.class), command.getKey());
            assertEquals(Scopes.SINGLETON, command.getScoping().getScope(null));
            assertNull(command.getScoping().getScopeAnnotation(null));
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(Map.class), command.getKey());
            assertEquals(Singleton.class, command.getScoping().getScopeAnnotation(null));
            assertNull(command.getScoping().getScope(null));
            assertFalse(command.getScoping().isEagerSingleton());
            return null;
          }
        },

        new FailingVisitor() {
          @Override public <T> Void visitBinding(BindCommand<T> command) {
            assertEquals(Key.get(Set.class), command.getKey());
            assertNull(command.getScoping().getScopeAnnotation(null));
            assertNull(command.getScoping().getScope(null));
            assertTrue(command.getScoping().isEagerSingleton());
            return null;
          }
        }
    );
  }


  /**
   * Visits each binding with a different visitor.
   */
  private void visitBindings(BinderVisitor<?>... visitors) {
    assertEquals(binder.getCommands().size(), visitors.length);

    for (int i = 0; i < visitors.length; i++) {
      BinderVisitor<?> visitor = visitors[i];
      Command command = binder.getCommands().get(i);
      command.acceptVisitor(visitor);
    }
  }

  private static class FailingVisitor implements BinderVisitor<Void> {
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

    public Void visitConstantBinding(BindConstantCommand command) {
      throw new AssertionFailedError();
    }

    public Void visitConvertToTypes(ConvertToTypesCommand command) {
      throw new AssertionFailedError();
    }

    public <T> Void visitBinding(BindCommand<T> command) {
      throw new AssertionFailedError();
    }

    public Void visitGetProviderCommand(GetProviderCommand command) {
      throw new AssertionFailedError();
    }
  }

  @Retention(RUNTIME)
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  public @interface TaggingAnnotation { }

  public enum CoinSide { HEADS, TAILS }
}
