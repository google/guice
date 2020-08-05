/*
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

package com.google.inject.spi;

import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
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
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Errors;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import com.google.inject.util.Types;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
@SuppressWarnings("ProvidesMethodOutsideOfModule")
public class ProviderMethodsTest extends TestCase implements Module {

  @SuppressWarnings("unchecked")
  public void testProviderMethods() {
    Injector injector = Guice.createInjector(this);

    Bob bob = injector.getInstance(Bob.class);
    assertEquals("A Bob", bob.getName());

    Bob clone = injector.getInstance(Bob.class);
    assertEquals("A Bob", clone.getName());

    assertNotSame(bob, clone);
    assertSame(bob.getDaughter(), clone.getDaughter());

    Key<Bob> soleBobKey = Key.get(Bob.class, Sole.class);
    assertSame(injector.getInstance(soleBobKey), injector.getInstance(soleBobKey));
  }

  @Override
  public void configure(Binder binder) {}

  interface Bob {
    String getName();

    Dagny getDaughter();
  }

  interface Dagny {
    int getAge();
  }

  @Provides
  Bob provideBob(final Dagny dagny) {
    return new Bob() {
      @Override
      public String getName() {
        return "A Bob";
      }

      @Override
      public Dagny getDaughter() {
        return dagny;
      }
    };
  }

  @Provides
  @Singleton
  @Sole
  Bob provideSoleBob(final Dagny dagny) {
    return new Bob() {
      @Override
      public String getName() {
        return "Only Bob";
      }

      @Override
      public Dagny getDaughter() {
        return dagny;
      }
    };
  }

  @Provides
  @Singleton
  Dagny provideDagny() {
    return new Dagny() {
      @Override
      public int getAge() {
        return 1;
      }
    };
  }

  @Retention(RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @BindingAnnotation
  @interface Sole {}

  public void testCircularDependency() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              @Provides
              Foo newFoo(final Bar bar) {
                return new Foo() {

                  @Override
                  public Bar getBar() {
                    return bar;
                  }

                  @Override
                  public int getI() {
                    return 5;
                  }
                };
              }

              @Provides
              Bar newBar(final Foo foo) {
                return new Bar() {

                  @Override
                  public Foo getFoo() {
                    return foo;
                  }

                  @Override
                  public int getI() {
                    return 10;
                  }
                };
              }
            });

    Foo foo = injector.getInstance(Foo.class);
    assertEquals(5, foo.getI());
    assertEquals(10, foo.getBar().getI());
    assertEquals(5, foo.getBar().getFoo().getI());
  }

  public interface Foo {
    Bar getBar();

    int getI();
  }

  public interface Bar {
    Foo getFoo();

    int getI();
  }

  public void testMultipleBindingAnnotations() {
    try {
      Guice.createInjector(
          new AbstractModule() {

            @Provides
            @Named("A")
            @Blue
            public String provideString() {
              return "a";
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "more than one annotation annotated with @BindingAnnotation:",
          "Named",
          "Blue",
          "at " + getClass().getName(),
          ".provideString(ProviderMethodsTest.java:");
    }
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Blue {}

  public void testGenericProviderMethods() {
    Injector injector =
        Guice.createInjector(new ProvideTs<String>("A", "B") {}, new ProvideTs<Integer>(1, 2) {});

    assertEquals("A", injector.getInstance(Key.get(String.class, Names.named("First"))));
    assertEquals("B", injector.getInstance(Key.get(String.class, Names.named("Second"))));
    assertEquals(
        ImmutableSet.of("A", "B"), injector.getInstance(Key.get(Types.setOf(String.class))));

    assertEquals(1, injector.getInstance(Key.get(Integer.class, Names.named("First"))).intValue());
    assertEquals(2, injector.getInstance(Key.get(Integer.class, Names.named("Second"))).intValue());
    assertEquals(ImmutableSet.of(1, 2), injector.getInstance(Key.get(Types.setOf(Integer.class))));
  }

  abstract class ProvideTs<T> extends AbstractModule {
    final T first;
    final T second;

    protected ProvideTs(T first, T second) {
      this.first = first;
      this.second = second;
    }

    @Named("First")
    @Provides
    T provideFirst() {
      return first;
    }

    @Named("Second")
    @Provides
    T provideSecond() {
      return second;
    }

    @Provides
    Set<T> provideBoth(@Named("First") T first, @Named("Second") T second) {
      return ImmutableSet.of(first, second);
    }
  }

  public void testAutomaticProviderMethods() {
    Injector injector =
        Guice.createInjector(
            (Module)
                new AbstractModule() {

                  private int next = 1;

                  @Provides
                  @Named("count")
                  public Integer provideCount() {
                    return next++;
                  }
                });

    assertEquals(1, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
    assertEquals(2, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
    assertEquals(3, injector.getInstance(Key.get(Integer.class, Names.named("count"))).intValue());
  }

  /**
   * If the user installs provider methods for the module manually, that shouldn't cause a double
   * binding of the provider methods' types.
   */
  public void testAutomaticProviderMethodsDoNotCauseDoubleBinding() {
    Module installsSelf =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(this);
            bind(Integer.class).toInstance(5);
          }

          @Provides
          public String provideString(Integer count) {
            return "A" + count;
          }
        };

    Injector injector = Guice.createInjector(installsSelf);
    assertEquals("A5", injector.getInstance(String.class));
  }

  public void testWildcardProviderMethods() {
    final List<String> strings = ImmutableList.of("A", "B", "C");
    final List<Number> numbers = ImmutableList.<Number>of(1, 2, 3);

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                @SuppressWarnings("unchecked")
                Key<List<? super Integer>> listOfSupertypesOfInteger =
                    (Key<List<? super Integer>>)
                        Key.get(Types.listOf(Types.supertypeOf(Integer.class)));
                bind(listOfSupertypesOfInteger).toInstance(numbers);
              }

              @Provides
              public List<? extends CharSequence> provideCharSequences() {
                return strings;
              }

              @Provides
              public Class<?> provideType() {
                return Float.class;
              }
            });

    assertSame(strings, injector.getInstance(HasWildcardInjection.class).charSequences);
    assertSame(numbers, injector.getInstance(HasWildcardInjection.class).numbers);
    assertSame(Float.class, injector.getInstance(HasWildcardInjection.class).type);
  }

  static class HasWildcardInjection {
    @Inject List<? extends CharSequence> charSequences;
    @Inject List<? super Integer> numbers;
    @Inject Class<?> type;
  }

  public void testProviderMethodDependenciesAreExposed() throws Exception {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Integer.class).toInstance(50);
            bindConstant().annotatedWith(Names.named("units")).to("Kg");
          }

          @Provides
          @Named("weight")
          String provideWeight(Integer count, @Named("units") String units) {
            return count + units;
          }
        };
    Injector injector = Guice.createInjector(module);

    ProviderInstanceBinding<?> binding =
        (ProviderInstanceBinding<?>)
            injector.getBinding(Key.get(String.class, Names.named("weight")));
    Method method =
        module.getClass().getDeclaredMethod("provideWeight", Integer.class, String.class);
    InjectionPoint point = new InjectionPoint(TypeLiteral.get(module.getClass()), method, false);
    assertEquals(
        ImmutableSet.<Dependency<?>>of(
            new Dependency<Integer>(point, Key.get(Integer.class), false, 0),
            new Dependency<String>(point, Key.get(String.class, Names.named("units")), false, 1)),
        binding.getDependencies());
  }

  public void testNonModuleProviderMethods() {
    final Object methodsObject =
        new Object() {
          @Provides
          @Named("foo")
          String provideFoo() {
            return "foo-value";
          }
        };

    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(ProviderMethodsModule.forObject(methodsObject));
          }
        };

    Injector injector = Guice.createInjector(module);

    Key<String> key = Key.get(String.class, Names.named("foo"));
    assertEquals("foo-value", injector.getInstance(key));

    // Test the provider method object itself. This makes sure getInstance works, since GIN uses it
    List<Element> elements = Elements.getElements(module);
    assertEquals(1, elements.size());

    Element element = elements.get(0);
    assertTrue(
        element + " instanceof ProviderInstanceBinding",
        element instanceof ProviderInstanceBinding);

    ProviderInstanceBinding<?> binding = (ProviderInstanceBinding<?>) element;
    javax.inject.Provider<?> provider = binding.getUserSuppliedProvider();
    assertTrue(provider instanceof ProviderMethod);
    assertEquals(methodsObject, ((ProviderMethod) provider).getInstance());
    assertSame(provider, binding.getProviderInstance());
  }

  public void testVoidProviderMethods() {
    try {
      Guice.createInjector(
          new AbstractModule() {

            @Provides
            void provideFoo() {}
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Provider methods must return a value. Do not return void.",
          getClass().getName(),
          ".provideFoo(ProviderMethodsTest.java:");
    }
  }

  public void testInjectsJustOneLogger() {
    AtomicReference<Logger> loggerRef = new AtomicReference<>();
    Injector injector = Guice.createInjector(new FooModule(loggerRef));

    assertNull(loggerRef.get());
    injector.getInstance(Integer.class);
    Logger lastLogger = loggerRef.getAndSet(null);
    assertNotNull(lastLogger);
    injector.getInstance(Integer.class);
    assertSame(lastLogger, loggerRef.get());

    assertEquals(FooModule.class.getName(), lastLogger.getName());
  }

  private static class FooModule extends AbstractModule {
    private final AtomicReference<Logger> loggerRef;

    public FooModule(AtomicReference<Logger> loggerRef) {
      this.loggerRef = loggerRef;
    }

    @SuppressWarnings("unused")
    @Provides
    Integer foo(Logger logger) {
      loggerRef.set(logger);
      return 42;
    }
  }

  public void testSpi() throws Exception {
    Module m1 =
        new AbstractModule() {

          @Provides
          @Named("foo")
          String provideFoo(Integer dep) {
            return "foo";
          }
        };
    Module m2 =
        new AbstractModule() {

          @Provides
          Integer provideInt(@Named("foo") String dep) {
            return 42;
          }
        };
    Injector injector = Guice.createInjector(m1, m2);

    Binding<String> stringBinding = injector.getBinding(Key.get(String.class, Names.named("foo")));
    ProvidesMethodBinding<String> stringMethod =
        stringBinding.acceptTargetVisitor(new BindingCapturer<String>());
    assertEquals(m1, stringMethod.getEnclosingInstance());
    assertEquals(
        m1.getClass().getDeclaredMethod("provideFoo", Integer.class), stringMethod.getMethod());
    assertEquals(
        ((HasDependencies) stringBinding).getDependencies(), stringMethod.getDependencies());
    assertEquals(Key.get(String.class, Names.named("foo")), stringMethod.getKey());

    Binding<Integer> intBinding = injector.getBinding(Integer.class);
    ProvidesMethodBinding<Integer> intMethod =
        intBinding.acceptTargetVisitor(new BindingCapturer<Integer>());
    assertEquals(m2, intMethod.getEnclosingInstance());
    assertEquals(
        m2.getClass().getDeclaredMethod("provideInt", String.class), intMethod.getMethod());
    assertEquals(((HasDependencies) intBinding).getDependencies(), intMethod.getDependencies());
    assertEquals(Key.get(Integer.class), intMethod.getKey());
  }

  private static class BindingCapturer<T>
      extends DefaultBindingTargetVisitor<T, ProvidesMethodBinding<T>>
      implements ProvidesMethodTargetVisitor<T, ProvidesMethodBinding<T>> {

    @Override
    @SuppressWarnings("unchecked")
    public ProvidesMethodBinding<T> visit(
        ProvidesMethodBinding<? extends T> providesMethodBinding) {
      return (ProvidesMethodBinding<T>) providesMethodBinding;
    }

    @Override
    protected ProvidesMethodBinding<T> visitOther(Binding<? extends T> binding) {
      throw new IllegalStateException("unexpected visit of: " + binding);
    }
  }

  public void testProvidesMethodVisibility() {
    Injector injector = Guice.createInjector(new VisibilityModule());

    assertEquals(42, injector.getInstance(Integer.class).intValue());
    assertEquals(42L, injector.getInstance(Long.class).longValue());
    assertEquals(42D, injector.getInstance(Double.class).doubleValue(), 0.0);
    assertEquals(42F, injector.getInstance(Float.class).floatValue(), 0.0f);
  }

  private static class VisibilityModule extends AbstractModule {

    @SuppressWarnings("unused")
    @Provides
    Integer foo() {
      return 42;
    }

    @SuppressWarnings("unused")
    @Provides
    private Long bar() {
      return 42L;
    }

    @SuppressWarnings("unused")
    @Provides
    protected Double baz() {
      return 42D;
    }

    @SuppressWarnings("unused")
    @Provides
    public Float quux() {
      return 42F;
    }
  }

  public void testProvidesMethodInheritenceHierarchy() {
    try {
      Guice.createInjector(new Sub1Module(), new Sub2Module());
      fail("Expected injector creation failure");
    } catch (CreationException expected) {
      // both of our super class bindings cause errors
      assertContains(
          expected.getMessage(),
          "A binding to java.lang.Long was already configured",
          "A binding to java.lang.Integer was already configured");
    }
  }

  public void testProvidesMethodsDefinedInSuperClass() {
    Injector injector = Guice.createInjector(new Sub1Module());
    assertEquals(42, injector.getInstance(Integer.class).intValue());
    assertEquals(42L, injector.getInstance(Long.class).longValue());
    assertEquals(42D, injector.getInstance(Double.class).doubleValue(), 0.0);
  }

  private static class BaseModule extends AbstractModule {

    @Provides
    Integer foo() {
      return 42;
    }

    @Provides
    Long bar() {
      return 42L;
    }
  }

  private static class Sub1Module extends BaseModule {
    @Provides
    Double baz() {
      return 42D;
    }
  }

  private static class Sub2Module extends BaseModule {
    @Provides
    Float quux() {
      return 42F;
    }
  }

  /*if[AOP]*/
  public void testShareFastClass() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    CallerInspecterModule module = new CallerInspecterModule();
    Guice.createInjector(Stage.PRODUCTION, module);
    assertEquals(module.fooCallerClass, module.barCallerClass);
    assertTrue(module.fooCallerClass.contains("$$FastClassByGuice$$"));
  }

  private static class CallerInspecterModule extends AbstractModule {
    // start them off as unequal
    String barCallerClass = "not_set_bar";
    String fooCallerClass = "not_set_foo";

    @Provides
    @Singleton
    Integer foo() {
      this.fooCallerClass = new Exception().getStackTrace()[1].getClassName();
      return 42;
    }

    @Provides
    @Singleton
    Long bar() {
      this.barCallerClass = new Exception().getStackTrace()[1].getClassName();
      return 42L;
    }
  }

  public void testShareFastClassWithSuperClass() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    CallerInspecterSubClassModule module = new CallerInspecterSubClassModule();
    Guice.createInjector(Stage.PRODUCTION, module);
    assertEquals(
        "Expected provider methods in the same class to share fastclass classes",
        module.fooCallerClass,
        module.barCallerClass);
    assertFalse(
        "Did not expect provider methods in the subclasses to share fastclass classes "
            + "with their parent classes",
        module.bazCallerClass.equals(module.barCallerClass));
  }

  private static class CallerInspecterSubClassModule extends CallerInspecterModule {
    String bazCallerClass;

    @Override
    protected void configure() {}

    @Provides
    @Singleton
    Double baz() {
      this.bazCallerClass = new Exception().getStackTrace()[1].getClassName();
      return 42D;
    }
  }
  /*end[AOP]*/

  static class SuperClassModule extends AbstractModule {

    @Provides
    Number providerMethod() {
      return 1D;
    }

    @Provides
    @Named("rawlist")
    @SuppressWarnings("rawtypes") // Testing rawtypes.
    List rawProvider(@Named("list") List<String> f) {
      return f;
    }

    @Provides
    @Named("unrawlist")
    @SuppressWarnings({"unchecked", "rawtypes"}) // Testing rawtypes
    List<String> rawParameterProvider(@Named("rawlist") List f) {
      return f;
    }

    @Provides
    @Named("list")
    List<String> annotatedGenericProviderMethod() {
      return new ArrayList<String>();
    }

    @Provides
    @Named("collection")
    Collection<String> annotatedGenericParameterProviderMethod(@Named("list") List<String> foo) {
      return foo;
    }

    @Provides
    private String privateProviderMethod() {
      return "hello";
    }
  }

  public void testOverrideProviderMethod_overrideHasProvides() {
    class SubClassModule extends SuperClassModule {
      @Override
      @Provides
      Number providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_overrideHasProvides_withNewAnnotation() {
    class SubClassModule extends SuperClassModule {
      @Override
      @Provides
      @Named("foo")
      Number providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_overrideDoesntHaveProvides() {
    class SubClassModule extends SuperClassModule {
      @Override
      Number providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_overrideDoesntHaveProvides_withNewAnnotation() {
    class SubClassModule extends SuperClassModule {
      @Override
      @Named("foo")
      Number providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_covariantOverrideDoesntHaveProvides() {
    class SubClassModule extends SuperClassModule {
      @Override
      Double providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_covariantOverrideHasProvides() {
    class SubClassModule extends SuperClassModule {
      @Override
      @Provides
      Double providerMethod() {
        return 2D;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".providerMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".providerMethod()");
    }
  }

  public void testOverrideProviderMethod_fakeOverridePrivateMethod() {
    class SubClassModule extends SuperClassModule {
      // not actually an override, just looks like it
      String privateProviderMethod() {
        return "sub";
      }
    }
    assertEquals("hello", Guice.createInjector(new SubClassModule()).getInstance(String.class));
  }

  public void testOverrideProviderMethod_subclassRawTypes_returnType() {
    class SubClassModule extends SuperClassModule {
      @Override
      @SuppressWarnings({"unchecked", "rawtypes"}) // Testing rawtypes.
      List annotatedGenericProviderMethod() {
        return super.annotatedGenericProviderMethod();
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: "
              + SuperClassModule.class.getName()
              + ".annotatedGenericProviderMethod()",
          "overridden by: " + SubClassModule.class.getName() + ".annotatedGenericProviderMethod()");
    }
  }

  public void testOverrideProviderMethod_subclassRawTypes_parameterType() {
    class SubClassModule extends SuperClassModule {
      @SuppressWarnings({"unchecked", "rawtypes"}) // Testing rawtypes
      @Override
      Collection<String> annotatedGenericParameterProviderMethod(List foo) {
        return super.annotatedGenericParameterProviderMethod(foo);
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: "
              + SuperClassModule.class.getName()
              + ".annotatedGenericParameterProviderMethod()",
          "overridden by: "
              + SubClassModule.class.getName()
              + ".annotatedGenericParameterProviderMethod()");
    }
  }

  public void testOverrideProviderMethod_superclassRawTypes_returnType() {
    class SubClassModule extends SuperClassModule {
      // remove the rawtype from the override
      @Override
      List<String> rawProvider(List<String> f) {
        return f;
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + SuperClassModule.class.getName() + ".rawProvider()",
          "overridden by: " + SubClassModule.class.getName() + ".rawProvider()");
    }
  }

  abstract static class GenericSuperModule<T> extends AbstractModule {
    @Provides
    String provide(T thing) {
      return thing.toString();
    }
  }

  // This is a tricky case where signatures don't match, but it is an override (facilitated via a
  // bridge method)
  public void testOverrideProviderMethod_erasureBasedOverrides() {
    class SubClassModule extends GenericSuperModule<Integer> {
      @Override
      String provide(Integer thing) {
        return thing.toString();
      }

      @Override
      protected void configure() {
        bind(Integer.class).toInstance(3);
      }
    }
    try {
      Guice.createInjector(new SubClassModule());
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "Overriding @Provides methods is not allowed.",
          "@Provides method: " + GenericSuperModule.class.getName() + ".provide()",
          "overridden by: " + SubClassModule.class.getName() + ".provide()");
    }
  }

  static class RestrictedSuper extends AbstractModule {
    @Provides
    public String provideFoo() {
      return "foo";
    }

  }

  public static class ExposedSub extends RestrictedSuper {}

  public void testOverrideProviderMethod_increasedVisibility() {
    // ensure we don't detect the synthetic provideFoo method in ExposedSub as an override (it is,
    // but since it is synthetic it would be annoying to throw an error on it).
    assertEquals("foo", Guice.createInjector(new ExposedSub()).getInstance(String.class));
  }

  interface ProviderInterface<T> {
    T getT();
  }

  static class ModuleImpl extends AbstractModule implements ProviderInterface<String> {

    @Override
    @Provides
    public String getT() {
      return "string";
    }

    @Provides
    public Object getObject() {
      return new Object();
    }
    /* javac will synthesize a bridge method for getT with the types erased, equivalent to:
     * @Provides public Object getT() { ... }
     */
  }

  public void testIgnoreSyntheticBridgeMethods() {
    Guice.createInjector(new ModuleImpl());
  }

  public void testScopedProviderMethodThrowsException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              @Provides
              @Singleton
              int provideInt() {
                throw new RuntimeException("boom");
              }
            });
    Provider<Integer> intProvider = injector.getProvider(Integer.class);
    try {
      intProvider.get();
      fail();
    } catch (ProvisionException pe) {
      // by default assertContains asserts that the last item doesn't repeat... which is the main
      // thing we are testing for
      assertContains(pe.getMessage(), "java.lang.RuntimeException: boom", "provideInt");
    }
  }

  public void testNullability() throws Exception {
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toProvider(Providers.<String>of(null));
          }

          @SuppressWarnings("unused")
          @Provides
          Integer fail(String foo) {
            return 1;
          }

          @SuppressWarnings("unused")
          @Provides
          Long succeed(@Nullable String foo) {
            return 2L;
          }
        };
    Injector injector = Guice.createInjector(module);
    InjectionPoint fooPoint =
        InjectionPoint.forMethod(
            module.getClass().getDeclaredMethod("fail", String.class),
            TypeLiteral.get(module.getClass()));
    Dependency<?> fooDependency = Iterables.getOnlyElement(fooPoint.getDependencies());

    runNullableTest(injector, fooDependency, module);

    injector.getInstance(Long.class);
  }

  public void testModuleBindings() throws Exception {
    Module module =
        new AbstractModule() {

          @Provides
          Integer fail() {
            return 1;
          }
        };
    // sanity check that the injector works
    Injector injector = Guice.createInjector(module);
    assertEquals(1, injector.getInstance(Integer.class).intValue());
    ProviderInstanceBinding<Integer> injectorBinding =
        (ProviderInstanceBinding<Integer>) injector.getBinding(Integer.class);
    assertEquals(1, injectorBinding.getUserSuppliedProvider().get().intValue());

    ProviderInstanceBinding<?> moduleBinding =
        (ProviderInstanceBinding<?>) Iterables.getOnlyElement(Elements.getElements(module));
    try {
      moduleBinding.getUserSuppliedProvider().get();
      fail();
    } catch (IllegalStateException ise) {
      assertEquals(
          "This Provider cannot be used until the Injector has been created.", ise.getMessage());
    }
  }

  static final class DeduplicateModule extends AbstractModule {
    @Provides
    String provideString() {
      return "";
    }
  }

  public void testDeduplicateProviderMethodsBindings_sameInstance() {
    Module module = new DeduplicateModule();
    Guice.createInjector(Stage.PRODUCTION, module, module);
  }

  public void testDeduplicateProviderMethodsBindings_differentInstances() {
    try {
      Guice.createInjector(Stage.PRODUCTION, new DeduplicateModule(), new DeduplicateModule());
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "A binding to java.lang.String was already configured");
    }
  }

  static final class DeduplicateStaticModule extends AbstractModule {
    @Provides
    static String provideString() {
      return "";
    }
  }

  public void testDeduplicateProviderMethodsBindings_sameInstance_staticMethod() {
    Module module = new DeduplicateStaticModule();
    Guice.createInjector(Stage.PRODUCTION, module, module);
  }

  public void testDeduplicateProviderMethodsBindings_differentInstances_staticMethod() {
    Guice.createInjector(
        Stage.PRODUCTION, new DeduplicateStaticModule(), new DeduplicateStaticModule());
  }

  private void runNullableTest(Injector injector, Dependency<?> dependency, Module module) {
    switch (InternalFlags.getNullableProvidesOption()) {
      case ERROR:
        validateNullableFails(injector, module);
        break;
      case IGNORE:
        validateNullableIgnored(injector);
        break;
      case WARN:
        validateNullableWarns(injector, dependency);
        break;
    }
  }

  private void validateNullableFails(Injector injector, Module module) {
    try {
      injector.getInstance(Integer.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "1) null returned by binding at " + module.getClass().getName() + ".configure(",
          "but the 1st parameter of " + module.getClass().getName() + ".fail(",
          "is not @Nullable",
          "while locating java.lang.String",
          "for the 1st parameter of " + module.getClass().getName() + ".fail(",
          "while locating java.lang.Integer");

      assertEquals(1, expected.getErrorMessages().size());
    }
  }

  private void validateNullableIgnored(Injector injector) {
    injector.getInstance(Integer.class); // no exception
  }

  private void validateNullableWarns(Injector injector, Dependency<?> dependency) {
    final List<LogRecord> logRecords = Lists.newArrayList();
    final Handler fakeHandler =
        new Handler() {
          @Override
          public void publish(LogRecord logRecord) {
            logRecords.add(logRecord);
          }

          @Override
          public void flush() {}

          @Override
          public void close() throws SecurityException {}
        };
    Logger.getLogger(Guice.class.getName()).addHandler(fakeHandler);
    try {
      injector.getInstance(Integer.class); // no exception, but assert it does log.
      LogRecord record = Iterables.getOnlyElement(logRecords);
      assertEquals(
          "Guice injected null into {0} (a {1}), please mark it @Nullable."
              + " Use -Dguice_check_nullable_provides_params=ERROR to turn this into an"
              + " error.",
          record.getMessage());
      assertEquals(Errors.convert(dependency.getKey()), record.getParameters()[1]);
    } finally {
      Logger.getLogger(Guice.class.getName()).removeHandler(fakeHandler);
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Nullable {}
}
