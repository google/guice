/**
 * Copyright (C) 2007 Google Inc.
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
import static com.google.inject.Asserts.assertNotSerializable;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BinderTest extends TestCase {

  private ParameterizedType parameterizedWithVariable;
  private ParameterizedType parameterizedWithWildcard;
  private TypeVariable typeVariable;
  private WildcardType wildcardType;

  <T> void parameterizedWithVariable(List<T> typeWithVariables) {}
  <T> void parameterizedWithWildcard(List<? extends Comparable> typeWithWildcard) {}

  @Override protected void setUp() throws Exception {
    parameterizedWithVariable = (ParameterizedType) getClass()
        .getDeclaredMethod("parameterizedWithVariable", List.class).getGenericParameterTypes()[0];
    parameterizedWithWildcard = (ParameterizedType) getClass()
        .getDeclaredMethod("parameterizedWithWildcard", List.class).getGenericParameterTypes()[0];
    typeVariable = (TypeVariable) parameterizedWithVariable.getActualTypeArguments()[0];
    wildcardType = (WildcardType) parameterizedWithWildcard.getActualTypeArguments()[0];
  }

  Provider<Foo> fooProvider;

  public void testProviderFromBinder() {
    Guice.createInjector(new Module() {
      public void configure(Binder binder) {
        fooProvider = binder.getProvider(Foo.class);

        try {
          fooProvider.get();
        } catch (IllegalStateException e) { /* expected */ }
      }
    });

    assertNotNull(fooProvider.get());
  }

  static class Foo {}

  public void testMissingBindings() {
    try {
      Guice.createInjector(new AbstractModule() {
        public void configure() {
          getProvider(Runnable.class);
          bind(Comparator.class);
          bind(Date.class).annotatedWith(Names.named("date"));
        }
      });
    } catch (CreationException e) {
      assertEquals(3, e.getErrorMessages().size());
      assertContains(e.getMessage(),
          "1) Error at " + getClass().getName(),
          "No implementation for java.lang.Runnable was bound.",
          "2) Error at " + getClass().getName(),
          "No implementation for " + Comparator.class.getName() + " was bound.",
          "3) Error at " + getClass().getName(),
          "No implementation for java.util.Date annotated with "
              + "@com.google.inject.name.Named(value=date) was bound.");
    }
  }

  public void testMissingDependency() {
    try {
      Guice.createInjector(new AbstractModule() {
        public void configure() {
          bind(NeedsRunnable.class);
        }
      });
    } catch (CreationException e) {
      assertEquals(1, e.getErrorMessages().size());
      assertContains(e.getMessage(),
          "1) Error at " + getClass().getName(),
          "No implementation for java.lang.Runnable was bound.");
    }
  }

  static class NeedsRunnable {
    @Inject Runnable runnable;
  }

  public void testDanglingConstantBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override public void configure() {
          bindConstant();
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error at " + getClass().getName(),
          "Missing constant value. Please call to(...).");
    }
  }

  public void testToStringOnBinderApi() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override public void configure() {
          assertEquals("Binder", binder().toString());
          assertEquals("Provider<java.lang.Integer>", getProvider(Integer.class).toString());
          assertEquals("Provider<java.util.List<java.lang.String>>",
              getProvider(Key.get(new TypeLiteral<List<String>>() {})).toString());

          assertEquals("AnnotatedBindingBuilder<java.lang.Integer>",
              bind(Integer.class).toString());
          assertEquals("LinkedBindingBuilder<java.lang.Integer>",
              bind(Integer.class).annotatedWith(Names.named("a")).toString());
          assertEquals("AnnotatedConstantBindingBuilder", bindConstant().toString());
          assertEquals("ConstantBindingBuilder",
              bindConstant().annotatedWith(Names.named("b")).toString());
        }
      });
      fail();
    } catch (CreationException ignored) {
    }
  }

  public void testNothingIsSerializableInBinderApi() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override public void configure() {
          try {
            assertNotSerializable(binder());
            assertNotSerializable(getProvider(Integer.class));
            assertNotSerializable(getProvider(Key.get(new TypeLiteral<List<String>>() {})));
            assertNotSerializable(bind(Integer.class));
            assertNotSerializable(bind(Integer.class).annotatedWith(Names.named("a")));
            assertNotSerializable(bindConstant());
            assertNotSerializable(bindConstant().annotatedWith(Names.named("b")));
          } catch (IOException e) {
            fail(e.getMessage());
          }
        }
      });
      fail();
    } catch (CreationException ignored) {
    }
  }

  /**
   * Although {@code String[].class} isn't equal to {@code new
   * GenericArrayTypeImpl(String.class)}, Guice should treat these two types
   * interchangeably.
   */
  public void testArrayTypeCanonicalization() {
    final String[] strings = new String[] { "A" };
    final Integer[] integers = new Integer[] { 1 };
    
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String[].class).toInstance(strings);
        bind(new TypeLiteral<Integer[]>() {}).toInstance(integers);
      }
    });

    assertSame(integers, injector.getInstance(Key.get(new TypeLiteral<Integer[]>() {})));
    assertSame(integers, injector.getInstance(new Key<Integer[]>() {}));
    assertSame(integers, injector.getInstance(Integer[].class));
    assertSame(strings, injector.getInstance(Key.get(new TypeLiteral<String[]>() {})));
    assertSame(strings, injector.getInstance(new Key<String[]>() {}));
    assertSame(strings, injector.getInstance(String[].class));

    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(String[].class).toInstance(strings);
          bind(new TypeLiteral<String[]>() {}).toInstance(strings);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Error at " + getClass().getName(), ".configure(BinderTest.java",
          "A binding to java.lang.String[] was already configured");
      assertContains(expected.getMessage(),
          "1 error[s]");
    }
  }

  /** Test for issue 186 */
  public void testBindDisallowedTypes() throws NoSuchMethodException {
    Type[] types = new Type[] {
        parameterizedWithVariable,
        parameterizedWithWildcard,
        typeVariable,
        wildcardType,
    };

    for (Type type : types) {
      @SuppressWarnings("unchecked") final
      Key<Object> key = (Key<Object>) Key.get(type);

      try {
        Guice.createInjector(new AbstractModule() {
          protected void configure() {
            bind(key).toProvider(Providers.of(null));
          }
        });
        fail("Guice should not allow bindings to " + type);
      } catch (CreationException e) {
        assertContains(e.getMessage(), "Cannot bind types that have type variables");
      }
    }
  }

  /**
   * Untargetted bindings should follow @ImplementedBy and @ProvidedBy
   * annotations if they exist. Otherwise the class should be constructed
   * directly.
   */
  public void testUntargettedBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(HasProvidedBy1.class);
        bind(HasImplementedBy1.class);
        bind(HasProvidedBy2.class);
        bind(HasImplementedBy2.class);
        bind(JustAClass.class);
      }
    });
    
    assertNotNull(injector.getInstance(HasProvidedBy1.class));
    assertNotNull(injector.getInstance(HasImplementedBy1.class));
    assertNotSame(HasProvidedBy2.class,
        injector.getInstance(HasProvidedBy2.class).getClass());
    assertSame(ExtendsHasImplementedBy2.class,
        injector.getInstance(HasImplementedBy2.class).getClass());
    assertSame(JustAClass.class, injector.getInstance(JustAClass.class).getClass());
  }

  public void testPartialInjectorGetInstance() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(MissingParameter.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "1) Error at " + MissingParameter.class.getName() + ".<init>(BinderTest.java:",
          "Could not find a suitable constructor in " + NoInjectConstructor.class.getName());
    }
  }

  static class MissingParameter {
    @Inject MissingParameter(NoInjectConstructor noInjectConstructor) {}
  }

  static class NoInjectConstructor {
    private NoInjectConstructor() {}
  }

  @ProvidedBy(HasProvidedBy1Provider.class)
  interface HasProvidedBy1 {}

  static class HasProvidedBy1Provider implements Provider<HasProvidedBy1> {
    public HasProvidedBy1 get() {
      return new HasProvidedBy1() {};
    }
  }

  @ImplementedBy(ImplementsHasImplementedBy1.class)
  interface HasImplementedBy1 {}

  static class ImplementsHasImplementedBy1 implements HasImplementedBy1 {}

  @ProvidedBy(HasProvidedBy2Provider.class)
  static class HasProvidedBy2 {}

  static class HasProvidedBy2Provider implements Provider<HasProvidedBy2> {
    public HasProvidedBy2 get() {
      return new HasProvidedBy2() {};
    }
  }

  @ImplementedBy(ExtendsHasImplementedBy2.class)
  static class HasImplementedBy2 {}

  static class ExtendsHasImplementedBy2 extends HasImplementedBy2 {}

  static class JustAClass {}


//  public void testBindInterfaceWithoutImplementation() {
//    Guice.createInjector(new AbstractModule() {
//      protected void configure() {
//        bind(Runnable.class);
//      }
//    }).getInstance(Runnable.class);
//  }

  enum Roshambo { ROCK, SCISSORS, PAPER }
}
