package com.google.inject.spi;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.Asserts;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import junit.framework.TestCase;

/** @author sberlin@gmail.com (Sam Berlin) */
public class InjectorSpiTest extends TestCase {

  public void testExistingBinding() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class);
                bind(Baz.class);
              }
            });
    // Sanity check -- ensure we return the proper binding for all existing bindings.
    for (Map.Entry<Key<?>, Binding<?>> entry : injector.getAllBindings().entrySet()) {
      assertSame(entry.getValue(), injector.getExistingBinding(entry.getKey()));
    }

    // Now run through specifics...
    Binding<?> binding;

    // 1) non-Provider Foo.class
    binding = injector.getExistingBinding(Key.get(Foo.class));
    assertNotNull(binding);
    assertEquals(Foo.class, binding.getKey().getTypeLiteral().getRawType());

    // 2) Provider<Foo> class (should already exist, because Baz @Injects it).
    // the assertTrue is a bit stricter than necessary, but makes sure this works for pre-existing
    // Provider bindings
    assertTrue(injector.getAllBindings().containsKey(Key.get(new TypeLiteral<Provider<Foo>>() {})));
    binding = injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Foo>>() {}));
    assertNotNull(binding);
    assertEquals(Provider.class, binding.getKey().getTypeLiteral().getRawType());
    assertEquals(Foo.class, ((Provider) binding.getProvider().get()).get().getClass());

    // 3) non-Provider Baz.class
    binding = injector.getExistingBinding(Key.get(Baz.class));
    assertNotNull(binding);
    assertEquals(Baz.class, binding.getKey().getTypeLiteral().getRawType());

    // 4) Provider<Baz> class (should not already exist, because nothing used it yet).
    // the assertFalse is a bit stricter than necessary, but makes sure this works for
    // non-pre-existing Provider bindings
    assertFalse(
        injector.getAllBindings().containsKey(Key.get(new TypeLiteral<Provider<Baz>>() {})));
    binding = injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Baz>>() {}));
    assertNotNull(binding);
    assertEquals(Provider.class, binding.getKey().getTypeLiteral().getRawType());
    assertEquals(Baz.class, ((Provider) binding.getProvider().get()).get().getClass());

    // 5) non-Provider Bar, doesn't exist.
    assertNull(injector.getExistingBinding(Key.get(Bar.class)));

    // 6) Provider Bar, doesn't exist.
    assertNull(injector.getExistingBinding(Key.get(new TypeLiteral<Provider<Bar>>() {})));
  }

  @SuppressWarnings("unused")
  private static void customMethod(Foo foo, Bar bar) {}

  public void testGetElements_standardItems() {
    Injector injector = Guice.createInjector(new AbstractModule() {});

    final List<Binding<?>> bindings = new ArrayList<>();
    final List<TypeConverterBinding> typeConverters = new ArrayList<>();
    final List<ScopeBinding> scopes = new ArrayList<>();
    for (Element element : injector.getElements()) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public <T> Void visit(Binding<T> binding) {
              bindings.add(binding);
              return null;
            }

            @Override
            public Void visit(ScopeBinding scopeBinding) {
              scopes.add(scopeBinding);
              return null;
            }

            @Override
            public Void visit(TypeConverterBinding typeConverterBinding) {
              typeConverters.add(typeConverterBinding);
              return null;
            }

            @Override
            protected Void visitOther(Element element) {
              throw new IllegalStateException("Unexpected element: " + element);
            }
          });
    }

    Set<Key<?>> actualKeys = new HashSet<>();
    for (Binding<?> binding : bindings) {
      actualKeys.add(binding.getKey());
    }
    assertThat(actualKeys)
        .containsExactly(Key.get(Stage.class), Key.get(Injector.class), Key.get(Logger.class));
    assertThat(scopes).hasSize(2); // singleton for javax.inject.Singleton & c.g.i.Singleton
    assertThat(typeConverters).hasSize(10); // all the built-in converters.
  }

  public void testGetElements_providerLookups() {
    Method customMethod;
    try {
      customMethod = getClass().getDeclaredMethod("customMethod", Foo.class, Bar.class);
    } catch (NoSuchMethodException | SecurityException e) {
      throw new RuntimeException(e);
    }
    InjectionPoint ip = InjectionPoint.forMethod(customMethod, TypeLiteral.get(getClass()));
    final Dependency<?> fooDep =
        ip.getDependencies().get(0).getParameterIndex() == 0
            ? ip.getDependencies().get(0)
            : ip.getDependencies().get(1);
    final Dependency<?> barDep =
        ip.getDependencies().get(1).getParameterIndex() == 1
            ? ip.getDependencies().get(1)
            : ip.getDependencies().get(0);
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class);
                bind(Baz.class);
                binder().getProvider(fooDep);
                binder().getProvider(barDep);
              }
            });

    final List<ProviderLookup<?>> lookups = new ArrayList<>();
    for (Element element : injector.getElements()) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public <T> Void visit(ProviderLookup<T> providerLookup) {
              lookups.add(providerLookup);
              return null;
            }
          });
    }
    boolean foundFooLookup = false;
    boolean foundBarLookup = false;
    for (ProviderLookup<?> lookup : lookups) {
      if (lookup.getKey().getTypeLiteral().getRawType().equals(Foo.class)) {
        foundFooLookup = true;
        assertThat(lookup.getDependency()).isEqualTo(fooDep);
      } else if (lookup.getKey().getTypeLiteral().getRawType().equals(Bar.class)) {
        foundBarLookup = true;
        assertThat(lookup.getDependency()).isEqualTo(barDep);
      } else {
        fail("Unexpected lookup: " + lookup);
      }
    }
    assertTrue(foundFooLookup);
    assertTrue(foundBarLookup);
  }

  public void testGetElements_staticInjectionRequests() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("");
                bind(Number.class).toInstance(1);
                requestStaticInjection(StaticallyInject.class);
              }
            });

    final List<StaticInjectionRequest> requests = new ArrayList<>();
    for (Element element : injector.getElements()) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public Void visit(StaticInjectionRequest staticInjectionRequest) {
              requests.add(staticInjectionRequest);
              return null;
            }
          });
    }
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).getType()).isEqualTo(StaticallyInject.class);
    assertThat(requests.get(0).getInjectionPoints())
        .containsExactly(
            new InjectionPoint(
                TypeLiteral.get(SuperStatic.class),
                SuperStatic.class.getDeclaredField("staticNumber"),
                /* optional= */ false),
            new InjectionPoint(
                TypeLiteral.get(StaticallyInject.class),
                StaticallyInject.class.getDeclaredField("staticString"),
                /* optional= */ false));
  }

  public void testGetElements_injectionRequest() throws Exception {
    Object foo =
        new Object() {
          @Inject String string;
        };
    final AtomicReference<?> finalFoo = new AtomicReference<>(foo);
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("");
                requestInjection(finalFoo.get());
              }
            });
    finalFoo.set(null); // clear it out so 'foo' is the only ref.

    final List<InjectionRequest<?>> requests = new ArrayList<>();
    for (Element element : injector.getElements()) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public Void visit(InjectionRequest<?> injectionRequests) {
              requests.add(injectionRequests);
              return null;
            }
          });
    }
    assertThat(requests).hasSize(1);
    assertThat(requests.get(0).getType()).isEqualTo(TypeLiteral.get(foo.getClass()));
    assertThat(requests.get(0).getInstance()).isNull();
    assertThat(requests.get(0).getInjectionPoints())
        .containsExactly(
            new InjectionPoint(
                TypeLiteral.get(foo.getClass()),
                foo.getClass().getDeclaredField("string"),
                /* optional= */ false));

    // Also validate that 'foo' can be reclaimed.
    WeakReference<?> fooRef = new WeakReference<>(foo);
    foo = null;
    Asserts.awaitClear(fooRef);
  }

  public void testGetElements_membersInjectorLookup() throws Exception {
    final Object foo =
        new Object() {
          @Inject String string;
        };
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("");
                getMembersInjector(foo.getClass());
              }
            });

    final List<MembersInjectorLookup<?>> lookups = new ArrayList<>();
    for (Element element : injector.getElements()) {
      element.acceptVisitor(
          new DefaultElementVisitor<Void>() {
            @Override
            public <T> Void visit(MembersInjectorLookup<T> lookup) {
              lookups.add(lookup);
              return null;
            }
          });
    }
    assertThat(lookups).hasSize(1);
    assertThat(lookups.get(0).getType()).isEqualTo(TypeLiteral.get(foo.getClass()));
    assertThat(lookups.get(0).getInjectionPoints())
        .containsExactly(
            new InjectionPoint(
                TypeLiteral.get(foo.getClass()),
                foo.getClass().getDeclaredField("string"),
                /* optional= */ false));
  }

  static class StaticallyInject extends SuperStatic {
    @Inject static String staticString;
  }

  static class SuperStatic {
    @Inject static Number staticNumber;
  }

  private static class Foo {}

  private static class Bar {}

  private static class Baz {
    @SuppressWarnings("unused")
    @Inject
    Provider<Foo> fooP;
  }

  public void testGetAllMembersInjectorInjectionPoints_injectMembers_returned() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(42);
                bind(String.class).toInstance("bar");
              }
            });
    injector.injectMembers(new ClassWithInjectableField("foo"));
    Map<TypeLiteral<?>, List<InjectionPoint>> injectionPoints =
        injector.getAllMembersInjectorInjectionPoints();
    TypeLiteral<ClassWithInjectableField> expectedTypeLiteral =
        TypeLiteral.get(ClassWithInjectableField.class);
    assertThat(injectionPoints.keySet()).containsExactly(expectedTypeLiteral);
    Key<?> actualDependencyKey =
        Iterables.getOnlyElement(
                Iterables.getOnlyElement(injectionPoints.get(expectedTypeLiteral))
                    .getDependencies())
            .getKey();
    assertEquals(Key.get(Integer.class), actualDependencyKey);
  }

  public void testGetAllMembersInjectorInjectionPoints_getInstance() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(42);
                bind(String.class).toInstance("bar");
              }
            });
    injector.getInstance(ClassWithInjectableField.class);
    Map<TypeLiteral<?>, List<InjectionPoint>> injectionPoints =
        injector.getAllMembersInjectorInjectionPoints();
    assertThat(injectionPoints).isEmpty();
  }

  public void testGetAllMembersInjectorInjectionPoints_getInstanceAndInjectMembers() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Integer.class).toInstance(42);
                bind(String.class).toInstance("bar");
              }
            });
    injector.injectMembers(new ClassWithInjectableField("foo"));
    injector.getInstance(ClassWithInjectableField.class);
    Map<TypeLiteral<?>, List<InjectionPoint>> injectionPoints =
        injector.getAllMembersInjectorInjectionPoints();
    TypeLiteral<ClassWithInjectableField> expectedTypeLiteral =
        TypeLiteral.get(ClassWithInjectableField.class);
    assertThat(injectionPoints.keySet()).containsExactly(expectedTypeLiteral);
    Key<?> actualDependencyKey =
        Iterables.getOnlyElement(
                Iterables.getOnlyElement(injectionPoints.get(expectedTypeLiteral))
                    .getDependencies())
            .getKey();
    assertEquals(Key.get(Integer.class), actualDependencyKey);
  }

  private static class ClassWithInjectableField {

    @Inject
    ClassWithInjectableField(String name) {}

    @Inject private Integer instanceField;

    @Inject private static Double staticField;
  }
}
