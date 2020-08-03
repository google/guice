package com.google.inject.spi;

import com.google.inject.AbstractModule;
import com.google.inject.Asserts;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Stage;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

public class ToolStageInjectorTest extends TestCase {

  @Override
  protected void setUp() throws Exception {
    Foo.s = null;
    Foo.sm = null;
  }

  public void testToolStageInjectorRestrictions() {
    Injector injector = Guice.createInjector(Stage.TOOL);
    try {
      injector.injectMembers(new Object());
      fail("Non-SPI Injector methods must throw an exception in the TOOL stage.");
    } catch (UnsupportedOperationException expected) {
    }

    try {
      injector.getInstance(Injector.class);
      fail("Non-SPI Injector methods must throw an exception in the TOOL stage.");
    } catch (UnsupportedOperationException expected) {
    }

    try {
      injector.getInstance(Key.get(Injector.class));
      fail("Non-SPI Injector methods must throw an exception in the TOOL stage.");
    } catch (UnsupportedOperationException expected) {
    }

    try {
      injector.getProvider(Injector.class);
      fail("Non-SPI Injector methods must throw an exception in the TOOL stage.");
    } catch (UnsupportedOperationException expected) {
    }

    try {
      injector.getProvider(Key.get(Injector.class));
      fail("Non-SPI Injector methods must throw an exception in the TOOL stage.");
    } catch (UnsupportedOperationException expected) {
    }
  }

  public void testToolStageDoesntInjectInstances() {
    final Foo foo = new Foo();
    Guice.createInjector(
        Stage.TOOL,
        new AbstractModule() {
          @Override
          protected void configure() {
            requestStaticInjection(Foo.class);
            requestInjection(foo);
          }
        });
    assertNull(Foo.s);
    assertNull(Foo.sm);
    assertNull(foo.f);
    assertNull(foo.m);
  }

  public void testToolStageDoesntInjectProviders() {
    final Foo foo = new Foo();
    Guice.createInjector(
        Stage.TOOL,
        new AbstractModule() {
          @Override
          protected void configure() {
            requestStaticInjection(Foo.class);
            bind(Object.class).toProvider(foo);
          }
        });
    assertNull(Foo.s);
    assertNull(Foo.sm);
    assertNull(foo.f);
    assertNull(foo.m);
  }

  public void testToolStageWarnsOfMissingObjectGraph() {
    final Bar bar = new Bar();
    try {
      Guice.createInjector(
          Stage.TOOL,
          new AbstractModule() {
            @Override
            protected void configure() {
              requestStaticInjection(Bar.class);
              requestInjection(bar);
            }
          });
      fail("expected exception");
    } catch (CreationException expected) {
      Asserts.assertContains(
          expected.toString(),
          "No implementation for java.util.Collection<java.lang.String> was bound.",
          "No implementation for java.util.Map<java.lang.String, java.lang.String> was bound.",
          "No implementation for java.util.List<java.lang.String> was bound.",
          "No implementation for java.util.Set<java.lang.String> was bound.");
    }
  }

  public void testToolStageInjectsTooledMethods() {
    final Tooled tooled = new Tooled();
    Guice.createInjector(
        Stage.TOOL,
        new AbstractModule() {
          @Override
          protected void configure() {
            requestStaticInjection(Tooled.class);
            bind(Object.class).toProvider(tooled);
          }
        });
    assertNull(Tooled.s);
    assertNotNull(Tooled.sm);
    assertNull(tooled.f);
    assertNotNull(tooled.m);
  }

  private static class Bar {
    @SuppressWarnings("unused")
    @Inject
    private static List<String> list;

    @SuppressWarnings("unused")
    @Inject
    private Set<String> set;

    @SuppressWarnings("unused")
    @Inject
    void method(Collection<String> c) {}

    @SuppressWarnings("unused")
    @Inject
    static void staticMethod(Map<String, String> map) {}
  }

  private static class Foo implements Provider<Object> {
    @Inject private static S s;
    @Inject private F f;
    private M m;

    @SuppressWarnings("unused")
    @Inject
    void method(M m) {
      this.m = m;
    }

    private static SM sm;

    @SuppressWarnings("unused")
    @Inject
    static void staticMethod(SM sm) {
      Tooled.sm = sm;
    }

    @Override
    public Object get() {
      return null;
    }
  }

  private static class Tooled implements Provider<Object> {
    @Inject private static S s;
    @Inject private F f;
    private M m;

    @Toolable
    @SuppressWarnings("unused")
    @Inject
    void method(M m) {
      this.m = m;
    }

    private static SM sm;

    @Toolable
    @SuppressWarnings("unused")
    @Inject
    static void staticMethod(SM sm) {
      Tooled.sm = sm;
    }

    @Override
    public Object get() {
      return null;
    }
  }

  private static class S {}

  private static class F {}

  private static class M {}

  private static class SM {}
}
