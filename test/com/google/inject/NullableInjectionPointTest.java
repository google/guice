package com.google.inject;

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
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding at");
      assertContains(cause.getMessage(), "FooConstructor");
      assertContains(cause.getMessage(), "is not @Nullable");
    }
  }

  public void testInjectNullIntoNotNullableMethod() {
    try {
      createInjector().getInstance(FooMethod.class);
      fail("Injecting null should fail with an error");
    }
    catch (ProvisionException expected) {
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding at");
      assertContains(cause.getMessage(), "FooMethod.setFoo");
      assertContains(cause.getMessage(), "is not @Nullable");
    }
  }

  public void testInjectNullIntoNotNullableField() {
    try {
      createInjector().getInstance(FooField.class);
      fail("Injecting null should fail with an error");
    }
    catch (ProvisionException expected) {
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding at");
      assertContains(cause.getMessage(), "FooField.foo");
      assertContains(cause.getMessage(), "is not @Nullable");
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
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        try {
          bind(Foo.class).toInstance(null);
          fail();
        }
        catch(NullPointerException expected) {
          assertEquals("Binding to null instances is not allowed. "
              + "Use toProvider(Providers.of(null)) if this is your intended behaviour.", 
              expected.getMessage());
        }
      }
    });

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

    try {
      injector.getInstance(FooField.class);
    }
    catch(ProvisionException expected) {
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding at");
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

    try {
      injector.getInstance(FooField.class);
    }
    catch(ProvisionException expected) {
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding at");
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

    try {
      injector.getInstance(FooField.class);
    }
    catch(ProvisionException expected) {
      NullPointerException cause = (NullPointerException)expected.getCause();
      assertContains(cause.getMessage(), "null returned by binding "
          + "at com.google.inject.NullableInjectionPointTest");
    }
  }

  private void assertContains(String text, String substring) {
    assertTrue(String.format("Expected \"%s\" to contain substring \"%s\"",
        text, substring), text.contains(substring));
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
}
