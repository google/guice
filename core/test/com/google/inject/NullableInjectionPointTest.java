package com.google.inject;

import static com.google.inject.Asserts.assertContains;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.inject.internal.util.Nullable;

import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class NullableInjectionPointTest extends TestCase {

  public void testInjectNullIntoNotNullableConstructor() {
    try {
      createInjector().getInstance(FooConstructor.class);
      fail("Injecting null should fail with an error");
    }
    catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "null returned by binding at " + getClass().getName(),
          "parameter 0 of " + FooConstructor.class.getName() + ".<init>() is not @Nullable");
    }
  }

  public void testInjectNullIntoNotNullableMethod() {
    try {
      createInjector().getInstance(FooMethod.class);
      fail("Injecting null should fail with an error");
    }
    catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "null returned by binding at " + getClass().getName(),
          "parameter 0 of " + FooMethod.class.getName() + ".setFoo() is not @Nullable");
    }
  }

  public void testInjectNullIntoNotNullableField() {
    try {
      createInjector().getInstance(FooField.class);
      fail("Injecting null should fail with an error");
    }
    catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          "null returned by binding at " + getClass().getName(),
          " but " + FooField.class.getName() + ".foo is not @Nullable");
    }
  }

  /**
   * Provider.getInstance() is allowed to return null via direct calls to
   * getInstance().
   */
  public void testGetInstanceOfNull() {
    assertNull(createInjector().getInstance(Foo.class));
  }

  public void testInjectNullIntoNullableConstructor() {
    NullableFooConstructor nfc
        = createInjector().getInstance(NullableFooConstructor.class);
    assertNull(nfc.foo);
  }

  public void testInjectNullIntoNullableMethod() {
    NullableFooMethod nfm
        = createInjector().getInstance(NullableFooMethod.class);
    assertNull(nfm.foo);
  }

  public void testInjectNullIntoNullableField() {
    NullableFooField nff
        = createInjector().getInstance(NullableFooField.class);
    assertNull(nff.foo);
  }
  
  public void testInjectNullIntoCustomNullableConstructor() {
    CustomNullableFooConstructor nfc
        = createInjector().getInstance(CustomNullableFooConstructor.class);
    assertNull(nfc.foo);
  }

  public void testInjectNullIntoCustomNullableMethod() {
    CustomNullableFooMethod nfm
        = createInjector().getInstance(CustomNullableFooMethod.class);
    assertNull(nfm.foo);
  }

  public void testInjectNullIntoCustomNullableField() {
    CustomNullableFooField nff
        = createInjector().getInstance(CustomNullableFooField.class);
    assertNull(nff.foo);
  }  

  private Injector createInjector() {
    return Guice.createInjector(
        new AbstractModule() {
          protected void configure() {
            bind(Foo.class).toProvider(new Provider<Foo>() {
              public Foo get() {
                return null;
              }
            });
          }
        });
  }

  /**
   * We haven't decided on what the desired behaviour of this test should be...
   */
  public void testBindNullToInstance() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Foo.class).toInstance(null);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Binding to null instances is not allowed.",
          "at " + getClass().getName(), ".configure(NullableInjectionPointTest.java:");
    }
  }

  public void testBindNullToProvider() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class).toProvider(new Provider<Foo>() {
          public Foo get() {
            return null;
          }
        });
      }
    });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
    }
    catch(ProvisionException expected) {
      assertContains(expected.getMessage(), "null returned by binding at");
    }
  }

  public void testBindScopedNull() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class).toProvider(new Provider<Foo>() {
          public Foo get() {
            return null;
          }
        }).in(Scopes.SINGLETON);
      }
    });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
    }
    catch(ProvisionException expected) {
      assertContains(expected.getMessage(), "null returned by binding at");
    }
  }

  public void testBindNullAsEagerSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Foo.class).toProvider(new Provider<Foo>() {
          public Foo get() {
            return null;
          }
        }).asEagerSingleton();
      }
    });
    assertNull(injector.getInstance(NullableFooField.class).foo);
    assertNull(injector.getInstance(CustomNullableFooField.class).foo);

    try {
      injector.getInstance(FooField.class);
      fail();
    } catch(ProvisionException expected) {
      assertContains(expected.getMessage(), "null returned by binding "
          + "at com.google.inject.NullableInjectionPointTest");
    }
  }

  static class Foo { }

  static class FooConstructor {
    @Inject FooConstructor(Foo foo) { }
  }
  static class FooField {
    @Inject Foo foo;
  }
  static class FooMethod {
    @Inject
    void setFoo(Foo foo) { }
  }

  static class NullableFooConstructor {
    Foo foo;
    @Inject NullableFooConstructor(@Nullable Foo foo) {
      this.foo = foo;
    }
  }
  static class NullableFooField {
    @Inject @Nullable Foo foo;
  }
  static class NullableFooMethod {
    Foo foo;
    @Inject void setFoo(@Nullable Foo foo) {
      this.foo = foo;
    }
  }
  
  static class CustomNullableFooConstructor {
    Foo foo;
    @Inject CustomNullableFooConstructor(@Namespace.Nullable Foo foo) {
      this.foo = foo;
    }
  }
  
  static class CustomNullableFooField {
    @Inject @Namespace.Nullable Foo foo;
  }
  static class CustomNullableFooMethod {
    Foo foo;
    @Inject void setFoo(@Namespace.Nullable Foo foo) {
      this.foo = foo;
    }
  }
  
  static interface Namespace {
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @interface Nullable { }
  }
}
