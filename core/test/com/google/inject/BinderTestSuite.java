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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import com.google.inject.util.Providers;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class BinderTestSuite {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    new Builder()
        .name("bind A")
        .module(new AbstractModule() {
          protected void configure() {
            bind(A.class);
          }
        })
        .creationException("No implementation for %s was bound", A.class.getName())
        .addToSuite(suite);

    new Builder()
        .name("bind PlainA named apple")
        .module(new AbstractModule() {
          protected void configure() {
            bind(PlainA.class).annotatedWith(named("apple"));
          }
        })
        .creationException("No implementation for %s annotated with %s was bound",
            PlainA.class.getName(), named("apple"))
        .addToSuite(suite);

    new Builder()
        .name("bind A to new PlainA(1)")
        .module(new AbstractModule() {
          protected void configure() {
            bind(A.class).toInstance(new PlainA(1));
          }
        })
        .creationTime(CreationTime.NONE)
        .expectedValues(new PlainA(1), new PlainA(1), new PlainA(1))
        .addToSuite(suite);

    new Builder()
        .name("no binding, AWithProvidedBy")
        .key(Key.get(AWithProvidedBy.class), InjectsAWithProvidedBy.class)
        .addToSuite(suite);

    new Builder()
        .name("no binding, AWithImplementedBy")
        .key(Key.get(AWithImplementedBy.class), InjectsAWithImplementedBy.class)
        .addToSuite(suite);

    new Builder()
        .name("no binding, ScopedA")
        .key(Key.get(ScopedA.class), InjectsScopedA.class)
        .expectedValues(new PlainA(201), new PlainA(201), new PlainA(202), new PlainA(202))
        .addToSuite(suite);

    new Builder()
        .name("no binding, AWithProvidedBy named apple")
        .key(Key.get(AWithProvidedBy.class, named("apple")),
            InjectsAWithProvidedByNamedApple.class)
        .configurationException("No implementation for %s annotated with %s was bound",
            AWithProvidedBy.class.getName(), named("apple"))
        .addToSuite(suite);

    new Builder()
        .name("no binding, AWithImplementedBy named apple")
        .key(Key.get(AWithImplementedBy.class, named("apple")),
            InjectsAWithImplementedByNamedApple.class)
        .configurationException("No implementation for %s annotated with %s was bound",
            AWithImplementedBy.class.getName(), named("apple"))
        .addToSuite(suite);

    new Builder()
        .name("no binding, ScopedA named apple")
        .key(Key.get(ScopedA.class, named("apple")), InjectsScopedANamedApple.class)
        .configurationException("No implementation for %s annotated with %s was bound",
            ScopedA.class.getName(), named("apple"))
        .addToSuite(suite);

    for (final Scoper scoper : Scoper.values()) {
      new Builder()
          .name("bind PlainA")
          .key(Key.get(PlainA.class), InjectsPlainA.class)
          .module(new AbstractModule() {
            protected void configure() {
              AnnotatedBindingBuilder<PlainA> abb = bind(PlainA.class);
              scoper.configure(abb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind A to PlainA")
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(A.class).to(PlainA.class);
              scoper.configure(sbb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind A to PlainAProvider.class")
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(A.class).toProvider(PlainAProvider.class);
              scoper.configure(sbb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind A to new PlainAProvider()")
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(A.class).toProvider(new PlainAProvider());
              scoper.configure(sbb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind AWithProvidedBy")
          .key(Key.get(AWithProvidedBy.class), InjectsAWithProvidedBy.class)
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(AWithProvidedBy.class);
              scoper.configure(sbb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind AWithImplementedBy")
          .key(Key.get(AWithImplementedBy.class), InjectsAWithImplementedBy.class)
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(AWithImplementedBy.class);
              scoper.configure(sbb);
            }
          })
          .scoper(scoper)
          .addToSuite(suite);

      new Builder()
          .name("bind ScopedA")
          .key(Key.get(ScopedA.class), InjectsScopedA.class)
          .module(new AbstractModule() {
            protected void configure() {
              ScopedBindingBuilder sbb = bind(ScopedA.class);
              scoper.configure(sbb);
            }
          })
          .expectedValues(new PlainA(201), new PlainA(201), new PlainA(202), new PlainA(202))
          .scoper(scoper)
          .addToSuite(suite);


      new Builder()
          .name("bind AWithProvidedBy named apple")
          .module(new AbstractModule() {
            protected void configure() {
              scoper.configure(bind(AWithProvidedBy.class).annotatedWith(named("apple")));
            }
          })
          .creationException("No implementation for %s annotated with %s was bound",
              AWithProvidedBy.class.getName(), named("apple"))
          .addToSuite(suite);

      new Builder()
          .name("bind AWithImplementedBy named apple")
          .module(new AbstractModule() {
            protected void configure() {
              scoper.configure(bind(AWithImplementedBy.class).annotatedWith(named("apple")));
            }
          })
          .creationException("No implementation for %s annotated with %s was bound",
              AWithImplementedBy.class.getName(), named("apple"))
          .addToSuite(suite);

      new Builder()
          .name("bind ScopedA named apple")
          .module(new AbstractModule() {
            protected void configure() {
              scoper.configure(bind(ScopedA.class).annotatedWith(named("apple")));
            }
          })
          .creationException("No implementation for %s annotated with %s was bound",
              ScopedA.class.getName(), named("apple"))
          .addToSuite(suite);

    }
    
    return suite;
  }
  
  enum Scoper {
    UNSCOPED {
      void configure(ScopedBindingBuilder sbb) {}
      void apply(Builder builder) {}
    },

    EAGER_SINGLETON {
      void configure(ScopedBindingBuilder sbb) {
        sbb.asEagerSingleton();
      }
      void apply(Builder builder) {
        builder.expectedValues(new PlainA(101), new PlainA(101), new PlainA(101));
        builder.creationTime(CreationTime.EAGER);
      }
    },

    SCOPES_SINGLETON {
      void configure(ScopedBindingBuilder sbb) {
        sbb.in(Scopes.SINGLETON);
      }
      void apply(Builder builder) {
        builder.expectedValues(new PlainA(201), new PlainA(201), new PlainA(201));
      }
    },

    SINGLETON_DOT_CLASS {
      void configure(ScopedBindingBuilder sbb) {
        sbb.in(Singleton.class);
      }
      void apply(Builder builder) {
        builder.expectedValues(new PlainA(201), new PlainA(201), new PlainA(201));
      }
    },

    TWO_AT_A_TIME_SCOPED_DOT_CLASS {
      void configure(ScopedBindingBuilder sbb) {
        sbb.in(TwoAtATimeScoped.class);
      }
      void apply(Builder builder) {
        builder.expectedValues(new PlainA(201), new PlainA(201), new PlainA(202), new PlainA(202));
      }
    },

    TWO_AT_A_TIME_SCOPE {
      void configure(ScopedBindingBuilder sbb) {
        sbb.in(new TwoAtATimeScope());
      }
      void apply(Builder builder) {
        builder.expectedValues(new PlainA(201), new PlainA(201), new PlainA(202), new PlainA(202));
      }
    };

    abstract void configure(ScopedBindingBuilder sbb);
    abstract void apply(Builder builder);
  }

  /** When Guice creates a value, directly or via a provider */
  enum CreationTime {
    NONE, EAGER, LAZY
  }

  public static class Builder {
    private String name = "test";
    private Key<?> key = Key.get(A.class);
    private Class<? extends Injectable> injectsKey = InjectsA.class;
    private List<Module> modules = Lists.<Module>newArrayList(new AbstractModule() {
      protected void configure() {
        bindScope(TwoAtATimeScoped.class, new TwoAtATimeScope());
      }
    });
    private List<Object> expectedValues = Lists.<Object>newArrayList(
        new PlainA(201), new PlainA(202), new PlainA(203));
    private CreationTime creationTime = CreationTime.LAZY;
    private String creationException;
    private String configurationException;

    public Builder module(Module module) {
      this.modules.add(module);
      return this;
    }

    public Builder creationTime(CreationTime creationTime) {
      this.creationTime = creationTime;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder key(Key<?> key, Class<? extends Injectable> injectsKey) {
      this.key = key;
      this.injectsKey = injectsKey;
      return this;
    }

    private Builder creationException(String message, Object... args) {
      this.creationException = String.format(message, args);
      return this;
    }

    private Builder configurationException(String message, Object... args) {
      configurationException = String.format(message, args);
      return this;
    }

    private Builder scoper(Scoper scoper) {
      name(name + " in " + scoper.toString());
      scoper.apply(this);
      return this;
    }

    private <T> Builder expectedValues(T... values) {
      this.expectedValues.clear();
      this.expectedValues.addAll(Arrays.asList(values));
      return this;
    }

    public void addToSuite(TestSuite suite) {
      if (creationException != null) {
        suite.addTest(new CreationExceptionTest(this));

      } else if (configurationException != null) {
        suite.addTest(new ConfigurationExceptionTest(this));

      } else {
        suite.addTest(new SuccessTest(this));
        if (creationTime != CreationTime.NONE) {
          suite.addTest(new UserExceptionsTest(this));
        }
      }
    }
  }

  public static class SuccessTest extends TestCase {
    final String name;
    final Key<?> key;
    final Class<? extends Injectable> injectsKey;
    final ImmutableList<Module> modules;
    final ImmutableList<Object> expectedValues;

    public SuccessTest(Builder builder) {
      super("test");
      name = builder.name;
      key = builder.key;
      injectsKey = builder.injectsKey;
      modules = ImmutableList.copyOf(builder.modules);
      expectedValues = ImmutableList.copyOf(builder.expectedValues);
    }

    public String getName() {
      return name;
    }

    Injector newInjector() {
      nextId.set(101);
      return Guice.createInjector(modules);
    }

    public void test() throws IllegalAccessException, InstantiationException {
      Injector injector = newInjector();
      nextId.set(201);
      for (Object value : expectedValues) {
        assertEquals(value, injector.getInstance(key));
      }

      Provider<?> provider = newInjector().getProvider(key);
      nextId.set(201);
      for (Object value : expectedValues) {
        assertEquals(value, provider.get());
      }

      Provider<?> bindingProvider = newInjector().getBinding(key).getProvider();
      nextId.set(201);
      for (Object value : expectedValues) {
        assertEquals(value, bindingProvider.get());
      }

      injector = newInjector();
      nextId.set(201);
      for (Object value : expectedValues) {
        Injectable instance = injector.getInstance(injectsKey);
        assertEquals(value, instance.value);
      }

      injector = newInjector();
      nextId.set(201);
      for (Object value : expectedValues) {
        Injectable injectable = injectsKey.newInstance();
        injector.injectMembers(injectable);
        assertEquals(value, injectable.value);
      }

      Injector injector1 = newInjector();
      nextId.set(201);
      Injectable hasProvider = injector1.getInstance(injectsKey);
      hasProvider.provider.get();
      nextId.set(201);
      for (Object value : expectedValues) {
        assertEquals(value, hasProvider.provider.get());
      }
    }
  }

  public static class CreationExceptionTest extends TestCase {
    final String name;
    final Key<?> key;
    final ImmutableList<Module> modules;
    final String creationException;

    public CreationExceptionTest(Builder builder) {
      super("test");
      name = builder.name;
      key = builder.key;
      modules = ImmutableList.copyOf(builder.modules);
      creationException = builder.creationException;
    }

    public String getName() {
      return "creation errors:" + name;
    }

    public void test() {
      try {
        Guice.createInjector(modules);
        fail();
      } catch (CreationException expected) {
        assertContains(expected.getMessage(), creationException);
      }
    }
  }

  public static class ConfigurationExceptionTest extends TestCase {
    final String name;
    final Key<?> key;
    final Class<? extends Injectable> injectsKey;
    final ImmutableList<Module> modules;
    final String configurationException;

    public ConfigurationExceptionTest(Builder builder) {
      super("test");
      name = builder.name;
      key = builder.key;
      injectsKey = builder.injectsKey;
      modules = ImmutableList.copyOf(builder.modules);
      configurationException = builder.configurationException;
    }

    public String getName() {
      return "provision errors:" + name;
    }

    Injector newInjector() {
      return Guice.createInjector(modules);
    }

    public void test() throws IllegalAccessException, InstantiationException {
      try {
        newInjector().getProvider(key);
        fail();
      } catch (ConfigurationException expected) {
        assertContains(expected.getMessage(), configurationException);
      }

      try {
        newInjector().getBinding(key).getProvider();
        fail();
      } catch (ConfigurationException expected) {
        assertContains(expected.getMessage(), configurationException);
      }

      try {
        newInjector().getInstance(key);
        fail();
      } catch (ConfigurationException expected) {
        assertContains(expected.getMessage(), configurationException);
      }

      try {
        newInjector().getInstance(injectsKey);
        fail();
      } catch (ConfigurationException expected) {
        assertContains(expected.getMessage(),
            configurationException, injectsKey.getName() + ".inject",
            configurationException, injectsKey.getName() + ".inject",
            "2 errors");
      }

      try {
        Injectable injectable = injectsKey.newInstance();
        newInjector().injectMembers(injectable);
        fail();
      } catch (ConfigurationException expected) {
        assertContains(expected.getMessage(),
            configurationException, injectsKey.getName() + ".inject",
            configurationException, injectsKey.getName() + ".inject",
            "2 errors");
      }
    }
  }

  public static class UserExceptionsTest extends TestCase {
    final String name;
    final Key<?> key;
    final Class<? extends Injectable> injectsKey;
    final ImmutableList<Module> modules;
    final ImmutableList<Object> expectedValues;
    final CreationTime creationTime;

    public UserExceptionsTest(Builder builder) {
      super("test");
      name = builder.name;
      key = builder.key;
      injectsKey = builder.injectsKey;
      modules = ImmutableList.copyOf(builder.modules);
      expectedValues = ImmutableList.copyOf(builder.expectedValues);
      creationTime = builder.creationTime;
    }

    public String getName() {
      return "provision errors:" + name;
    }

    Injector newInjector() {
      return Guice.createInjector(modules);
    }

    public void test() throws IllegalAccessException, InstantiationException {
      nextId.set(-1);
      try {
        newInjector();
        assertEquals(CreationTime.LAZY, creationTime);
      } catch (CreationException expected) {
        assertEquals(CreationTime.EAGER, creationTime);
        assertContains(expected.getMessage(), "Illegal value: -1");
        return;
      }

      Provider<?> provider = newInjector().getProvider(key);
      Provider<?> bindingProvider = newInjector().getBinding(key).getProvider();

      nextId.set(-1);
      try {
        newInjector().getInstance(key);
        fail();
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1");
      }

      nextId.set(-1);
      try {
        provider.get();
        fail();
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1");
      }

      nextId.set(-1);
      try {
        bindingProvider.get();
        fail();
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1");
      }

      try {
        nextId.set(-1);
        newInjector().getInstance(injectsKey);
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1",
            "for parameter 0 at " + injectsKey.getName() + ".inject");
      }

      nextId.set(201);
      Injectable injectable = injectsKey.newInstance();
      try {
        nextId.set(-1);
        newInjector().injectMembers(injectable);
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1",
            "for parameter 0 at " + injectsKey.getName() + ".inject");
      }

      nextId.set(201);
      Injectable hasProvider = newInjector().getInstance(injectsKey);
      hasProvider.provider.get();
      try {
        nextId.set(-1);
        hasProvider.provider.get();
      } catch (ProvisionException expected) {
        assertContains(expected.getMessage(), "Illegal value: -1");
      }
    }
  }

  /** negative to throw, 101... for eager singletons, 201... for everything else */
  static final AtomicInteger nextId = new AtomicInteger();

  @ProvidedBy(PlainAProvider.class)
  interface AWithProvidedBy {}

  static class InjectsAWithProvidedBy extends Injectable {
    @Inject public void inject(AWithProvidedBy aWithProvidedBy,
        Provider<AWithProvidedBy> aWithProvidedByProvider) {
      this.value = aWithProvidedBy;
      this.provider = aWithProvidedByProvider;
    }
  }

  static class InjectsAWithProvidedByNamedApple extends Injectable {
    @Inject public void inject(@Named("apple") AWithProvidedBy aWithProvidedBy,
        @Named("apple") Provider<AWithProvidedBy> aWithProvidedByProvider) {
      this.value = aWithProvidedBy;
      this.provider = aWithProvidedByProvider;
    }
  }

  @ImplementedBy(PlainA.class)
  interface AWithImplementedBy {}

  static class InjectsAWithImplementedBy extends Injectable {
    @Inject public void inject(AWithImplementedBy aWithImplementedBy,
        Provider<AWithImplementedBy> aWithImplementedByProvider) {
      this.value = aWithImplementedBy;
      this.provider = aWithImplementedByProvider;
    }
  }

  static class InjectsAWithImplementedByNamedApple extends Injectable {
    @Inject public void inject(@Named("apple") AWithImplementedBy aWithImplementedBy,
        @Named("apple") Provider<AWithImplementedBy> aWithImplementedByProvider) {
      this.value = aWithImplementedBy;
      this.provider = aWithImplementedByProvider;
    }
  }

  interface A extends AWithProvidedBy, AWithImplementedBy {}

  static class InjectsA extends Injectable {
    @Inject public void inject(A a, Provider<A> aProvider) {
      this.value = a;
      this.provider = aProvider;
    }
  }

  static class PlainA implements A {
    final int value;
    PlainA() {
      value = nextId.getAndIncrement();
      if (value < 0) {
        throw new RuntimeException("Illegal value: " + value);
      }
    }
    PlainA(int value) {
      this.value = value;
    }
    public boolean equals(Object obj) {
      return obj instanceof PlainA
          && value == ((PlainA) obj).value;
    }
    public int hashCode() {
      return value;
    }
    public String toString() {
      return "PlainA#" + value;
    }
  }

  static class PlainAProvider implements Provider<A> {
    public A get() {
      return new PlainA();
    }
  }

  static class InjectsPlainA extends Injectable {
    @Inject public void inject(PlainA plainA, Provider<PlainA> plainAProvider) {
      this.value = plainA;
      this.provider = plainAProvider;
    }
  }

  /** This scope hands out each value exactly twice  */
  static class TwoAtATimeScope implements Scope {
    public <T> Provider<T> scope(Key<T> key, final Provider<T> unscoped) {
      return new Provider<T>() {
        T instance;
        public T get() {
          if (instance == null) {
            instance = unscoped.get();
            return instance;
          } else {
            T result = instance;
            instance = null;
            return result;
          }
        }
      };
    }
  }

  @Target({ TYPE, METHOD }) @Retention(RUNTIME) @ScopeAnnotation
  public @interface TwoAtATimeScoped {}

  @TwoAtATimeScoped
  static class ScopedA extends PlainA {}

  static class InjectsScopedA extends Injectable {
    @Inject public void inject(ScopedA scopedA, Provider<ScopedA> scopedAProvider) {
      this.value = scopedA;
      this.provider = scopedAProvider;
    }
  }

  static class InjectsScopedANamedApple extends Injectable {
    @Inject public void inject(@Named("apple") ScopedA scopedA,
        @Named("apple") Provider<ScopedA> scopedAProvider) {
      this.value = scopedA;
      this.provider = scopedAProvider;
    }
  }

  static class Injectable {
    Object value = new Object();
    Provider<?> provider = Providers.of(new Object());
  }
}
