package com.google.inject.assistedinject.subpkg;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that run in a subpackage, to make sure tests aren't passing because they're run in the same
 * package as the assistedinject code.
 *
 * <p>See https://github.com/google/guice/issues/904
 */
@RunWith(JUnit4.class)
public final class SubpackageTest {
  private static final double JAVA_VERSION =
      Double.parseDouble(StandardSystemProperty.JAVA_SPECIFICATION_VERSION.value());

  private static final MethodHandles.Lookup LOOKUPS = MethodHandles.lookup();

  private final Logger loggerToWatch = Logger.getLogger(AssistedInject.class.getName());

  private final List<LogRecord> logRecords = Lists.newArrayList();
  private final Handler fakeHandler =
      new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
          logRecords.add(logRecord);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @Before
  public void setUp() throws Exception {
    loggerToWatch.addHandler(fakeHandler);
    setAllowPrivateLookupFallback(true);
    setAllowMethodHandleWorkaround(true);
  }

  @After
  public void tearDown() throws Exception {
    loggerToWatch.removeHandler(fakeHandler);
    setAllowPrivateLookupFallback(true);
    setAllowMethodHandleWorkaround(true);
  }

  public abstract static class AbstractAssisted {
    interface Factory<O extends AbstractAssisted, I extends CharSequence> {
      O create(I string);
    }
  }

  static class ConcreteAssisted extends AbstractAssisted {
    @Inject
    ConcreteAssisted(@SuppressWarnings("unused") @Assisted String string) {}
  }

  static class ConcreteAssistedWithOverride extends AbstractAssisted {
    @AssistedInject
    ConcreteAssistedWithOverride(@SuppressWarnings("unused") @Assisted String string) {}

    @AssistedInject
    ConcreteAssistedWithOverride(@SuppressWarnings("unused") @Assisted StringBuilder sb) {}

    interface Factory extends AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> {
      @Override
      ConcreteAssistedWithOverride create(String string);
    }

    interface Factory2 extends AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> {
      @Override
      ConcreteAssistedWithOverride create(String string);

      ConcreteAssistedWithOverride create(StringBuilder sb);
    }
  }

  static class ConcreteAssistedWithoutOverride extends AbstractAssisted {
    @Inject
    ConcreteAssistedWithoutOverride(@SuppressWarnings("unused") @Assisted String string) {}

    interface Factory extends AbstractAssisted.Factory<ConcreteAssistedWithoutOverride, String> {}
  }

  public static class Public extends AbstractAssisted {
    @AssistedInject
    Public(@SuppressWarnings("unused") @Assisted String string) {}

    @AssistedInject
    Public(@SuppressWarnings("unused") @Assisted StringBuilder sb) {}

    public interface Factory extends AbstractAssisted.Factory<Public, String> {
      @Override
      Public create(String string);

      Public create(StringBuilder sb);
    }
  }

  @Test
  public void testNoPrivateFallbackOrWorkaround() throws Exception {
    setAllowMethodHandleWorkaround(false);
    setAllowPrivateLookupFallback(false);

    if (JAVA_VERSION > 1.8) {
      // Above 1.8 will fail, because they can't access private details w/o the workarounds.
      try {
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(
                    new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory.class));
              }
            });
        fail("Expected CreationException");
      } catch (CreationException ce) {
        assertThat(Iterables.getOnlyElement(ce.getErrorMessages()).getMessage())
            .contains("Please call FactoryModuleBuilder.withLookups");
      }
      LogRecord record = Iterables.getOnlyElement(logRecords);
      assertThat(record.getMessage()).contains("Please pass a `MethodHandles.lookup()`");
    } else {
      // 1.8 & below will succeed, because that's the only way they can work.
      Injector injector =
          Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  install(
                      new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory.class));
                }
              });
      LogRecord record = Iterables.getOnlyElement(logRecords);
      assertThat(record.getMessage()).contains("Please pass a `MethodHandles.lookup()`");

      ConcreteAssistedWithOverride.Factory factory =
          injector.getInstance(ConcreteAssistedWithOverride.Factory.class);
      factory.create("foo");
      AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factoryAbstract = factory;
      factoryAbstract.create("foo");
    }
  }

  @Test
  public void testPrivateFallbackOnly() throws Exception {
    // Private fallback only works on JDKs below 17. On 17+ it's disabled.
    assumeTrue(JAVA_VERSION < 17);

    setAllowMethodHandleWorkaround(false);

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(
                    new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory.class));
              }
            });
    LogRecord record = Iterables.getOnlyElement(logRecords);
    assertThat(record.getMessage()).contains("Please pass a `MethodHandles.lookup()`");

    ConcreteAssistedWithOverride.Factory factory =
        injector.getInstance(ConcreteAssistedWithOverride.Factory.class);
    factory.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factoryAbstract = factory;
    factoryAbstract.create("foo");
  }

  @Test
  public void testHandleWorkaroundOnly() throws Exception {
    setAllowPrivateLookupFallback(false);

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(
                    new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory.class));
              }
            });
    LogRecord record = Iterables.getOnlyElement(logRecords);
    assertThat(record.getMessage()).contains("Please pass a `MethodHandles.lookup()`");

    ConcreteAssistedWithOverride.Factory factory =
        injector.getInstance(ConcreteAssistedWithOverride.Factory.class);
    factory.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factoryAbstract = factory;
    factoryAbstract.create("foo");
  }

  @Test
  public void testGeneratedDefaultMethodsForwardCorrectly() throws Exception {
    // This test requires above java 1.8.
    // 1.8's reflection capability is tested via "testReflectionFallbackWorks".
    assumeTrue(JAVA_VERSION > 1.8);

    final Key<AbstractAssisted.Factory<ConcreteAssisted, String>> concreteKey =
        new Key<AbstractAssisted.Factory<ConcreteAssisted, String>>() {};
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(
                    new FactoryModuleBuilder()
                        .withLookups(LOOKUPS)
                        .build(ConcreteAssistedWithOverride.Factory.class));
                install(
                    new FactoryModuleBuilder()
                        .withLookups(LOOKUPS)
                        .build(ConcreteAssistedWithOverride.Factory2.class));
                install(
                    new FactoryModuleBuilder()
                        .build(ConcreteAssistedWithoutOverride.Factory.class));
                install(new FactoryModuleBuilder().build(Public.Factory.class));
                install(new FactoryModuleBuilder().build(concreteKey));
              }
            });
    assertThat(logRecords).isEmpty();

    ConcreteAssistedWithOverride.Factory factory1 =
        injector.getInstance(ConcreteAssistedWithOverride.Factory.class);
    factory1.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factory1Abstract = factory1;
    factory1Abstract.create("foo");

    ConcreteAssistedWithOverride.Factory2 factory2 =
        injector.getInstance(ConcreteAssistedWithOverride.Factory2.class);
    factory2.create("foo");
    factory2.create(new StringBuilder("foo"));
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factory2Abstract = factory2;
    factory2Abstract.create("foo");

    ConcreteAssistedWithoutOverride.Factory factory3 =
        injector.getInstance(ConcreteAssistedWithoutOverride.Factory.class);
    factory3.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithoutOverride, String> factory3Abstract = factory3;
    factory3Abstract.create("foo");

    Public.Factory factory4 = injector.getInstance(Public.Factory.class);
    factory4.create("foo");
    factory4.create(new StringBuilder("foo"));
    AbstractAssisted.Factory<Public, String> factory4Abstract = factory4;
    factory4Abstract.create("foo");

    AbstractAssisted.Factory<ConcreteAssisted, String> factory5 = injector.getInstance(concreteKey);
    factory5.create("foo");
  }

  private static void setAllowPrivateLookupFallback(boolean allowed) throws Exception {
    Class<?> factoryProvider2 = Class.forName("com.google.inject.assistedinject.FactoryProvider2");
    Field field = factoryProvider2.getDeclaredField("allowPrivateLookupFallback");
    field.setAccessible(true);
    field.setBoolean(null, allowed);
  }

  private static void setAllowMethodHandleWorkaround(boolean allowed) throws Exception {
    Class<?> factoryProvider2 = Class.forName("com.google.inject.assistedinject.FactoryProvider2");
    Field field = factoryProvider2.getDeclaredField("allowMethodHandleWorkaround");
    field.setAccessible(true);
    field.setBoolean(null, allowed);
  }
}
