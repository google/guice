package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.base.Optional;
import com.google.inject.multibindings.OptionalBinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

/** @author jessewilson@google.com (Jesse Wilson) */
public class NullableInjectionPointTest {

  @Test
  public void testInjectNullIntoNotNullableConstructor() {
    try {
      createInjector().getInstance(FooConstructor.class);
      fail("Injecting null should fail with an error");
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "null returned by binding at NullableInjectionPointTest$1.configure",
          "the 1st parameter foo of NullableInjectionPointTest$FooConstructor.<init>(",
          "is not @Nullable");
    }
  }

  @Test
  public void testInjectNullIntoNotNullableMethod() {
    try {
      createInjector().getInstance(FooMethod.class);
      fail("Injecting null should fail with an error");
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "null returned by binding at NullableInjectionPointTest$1.configure",
          "the 1st parameter foo of NullableInjectionPointTest$FooMethod.setFoo(",
          "is not @Nullable");
    }
  }

  @Test
  public void testInjectNullIntoNotNullableField() {
    try {
      createInjector().getInstance(FooField.class);
      fail("Injecting null should fail with an error");
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "null returned by binding at NullableInjectionPointTest$1.configure(",
          " but NullableInjectionPointTest$FooField.foo",
          " is not @Nullable");
    }
  }

  /** Provider.getInstance() is allowed to return null via direct calls to getInstance(). */
  @Test
  public void testGetInstanceOfNull() {
    assertNull(createInjector().getInstance(Foo.class));
  }

  @Test
  public void testInjectNullIntoNullableConstructor() {
    NullableFooConstructor nfc = createInjector().getInstance(NullableFooConstructor.class);
    assertNull(nfc.foo);
  }

  @Test
  public void testInjectNullIntoNullableMethod() {
    NullableFooMethod nfm = createInjector().getInstance(NullableFooMethod.class);
    assertNull(nfm.foo);
  }

  @Test
  public void testInjectNullIntoNullableField() {
    NullableFooField nff = createInjector().getInstance(NullableFooField.class);
    assertNull(nff.foo);
  }

  @Test
  public void testInjectNullIntoCustomNullableConstructor() {
    CustomNullableFooConstructor nfc =
        createInjector().getInstance(CustomNullableFooConstructor.class);
    assertNull(nfc.foo);
  }

  @Test
  public void testInjectNullIntoCustomNullableMethod() {
    CustomNullableFooMethod nfm = createInjector().getInstance(CustomNullableFooMethod.class);
    assertNull(nfm.foo);
  }

  @Test
  public void testInjectNullIntoCustomNullableField() {
    CustomNullableFooField nff = createInjector().getInstance(CustomNullableFooField.class);
    assertNull(nff.foo);
  }

  @Test
  public void testInjectNullIntoTypeUseNullableConstructor() {
    TypeUseNullableFooConstructor nff =
        createInjector().getInstance(TypeUseNullableFooConstructor.class);
    assertNull(nff.foo);
  }

  @Test
  public void testInjectNullIntoTypeUseNullableMethod() {
    TypeUseNullableFooMethod nfm = createInjector().getInstance(TypeUseNullableFooMethod.class);
    assertNull(nfm.foo);
  }

  @Test
  public void testInjectNullIntoTypeUseNullableField() {
    TypeUseNullableFooField nff = createInjector().getInstance(TypeUseNullableFooField.class);
    assertNull(nff.foo);
  }

  private Injector createInjector() {
    return Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Foo.class).toProvider(Providers.<Foo>of(null));
          }
        });
  }

  /** We haven't decided on what the desired behaviour of this test should be... */
  @Test
  public void testBindNullToInstance() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(Foo.class).toInstance(null);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "Binding to null instances is not allowed.",
          "at NullableInjectionPointTest$2.configure(");
    }
  }

  @Test
  public void testBindNullToProvider() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(Providers.<Foo>of(null));
              }
            });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
      fail("Expected ProvisionException");
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(), "null returned by binding at");
    }
  }

  @Test
  public void testBindScopedNull() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(Providers.<Foo>of(null)).in(Scopes.SINGLETON);
              }
            });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
      fail("Expected ProvisionException");
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(), "null returned by binding at");
    }
  }

  @Test
  public void testBindNullAsEagerSingleton() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(Providers.<Foo>of(null)).asEagerSingleton();
              }
            });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(), "null returned by binding at NullableInjectionPointTest");
    }
  }

  /**
   * Tests for a regression where dependency objects were not updated properly and OptionalBinder
   * was rejecting nulls from its dependencies.
   */
  @Test
  public void testBindNullAndLinkFromOptionalBinder() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Foo.class).toProvider(Providers.<Foo>of(null));
                OptionalBinder.newOptionalBinder(binder(), Foo.class);
              }

              @Provides
              @Named("throughProvidesMethod")
              Foo provideFoo(Optional<Foo> foo) {
                return foo.orNull();
              }
            });
    assertNull(injector.getInstance(Key.get(Foo.class, Names.named("throughProvidesMethod"))));
  }

  static class Foo {}

  static class FooConstructor {
    @Inject
    FooConstructor(Foo foo) {}
  }

  static class FooField {
    @Inject Foo foo;
  }

  static class FooMethod {
    @Inject
    void setFoo(Foo foo) {}
  }

  static class NullableFooConstructor {
    Foo foo;

    @Inject
    NullableFooConstructor(@Nullable Foo foo) {
      this.foo = foo;
    }
  }

  static class NullableFooField {
    @Inject @Nullable Foo foo;
  }

  static class NullableFooMethod {
    Foo foo;

    @Inject
    void setFoo(@Nullable Foo foo) {
      this.foo = foo;
    }
  }

  static class CustomNullableFooConstructor {
    Foo foo;

    @Inject
    CustomNullableFooConstructor(@Namespace.Nullable Foo foo) {
      this.foo = foo;
    }
  }

  static class CustomNullableFooField {
    @Inject @Namespace.Nullable Foo foo;
  }

  static class CustomNullableFooMethod {
    Foo foo;

    @Inject
    void setFoo(@Namespace.Nullable Foo foo) {
      this.foo = foo;
    }
  }

  private static class TypeUse {
    @Retention(RUNTIME)
    @Target(TYPE_USE)
    private @interface Nullable {}
  }

  static class TypeUseNullableFooConstructor {
    Foo foo;

    @Inject
    TypeUseNullableFooConstructor(@TypeUse.Nullable Foo foo) {
      this.foo = foo;
    }
  }

  static class TypeUseNullableFooField {
    @Inject @TypeUse.Nullable Foo foo;
  }

  static class TypeUseNullableFooMethod {
    Foo foo;

    @Inject
    void setFoo(@TypeUse.Nullable Foo foo) {
      this.foo = foo;
    }
  }

  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER, ElementType.FIELD})
  @interface Nullable {}

  static interface Namespace {
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @interface Nullable {}
  }
}
