package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import com.google.inject.name.Names;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 * Test built-in injection of loggers.
 *
 * @author jessewilson
 */
public class LoggerInjectionTest extends TestCase {

  @Inject Logger logger;

  public void testLoggerWithMember() {
    Injector injector = Guice.createInjector();
    injector.injectMembers(this);
    assertEquals("com.google.inject.LoggerInjectionTest", logger.getName());
  }
  
  public void testLoggerInConstructor() {
    Injector injector = Guice.createInjector();
    Foo foo = injector.getInstance(Foo.class);
    assertEquals("com.google.inject.LoggerInjectionTest$Foo", foo.logger.getName());
  }
  
  private static class Foo {
    Logger logger;
    @SuppressWarnings("unused")
    @Inject Foo(Logger logger) {
      this.logger = logger;
    }
  }
  
  public void testLoggerWithoutMember() {
    Injector injector = Guice.createInjector();
    assertNull(injector.getInstance(Logger.class).getName());
    assertNull(injector.getProvider(Logger.class).get().getName());
    assertNull(injector.getBinding(Logger.class).getProvider().get().getName());
    assertEquals("Provider<Logger>", injector.getProvider(Logger.class).toString());
  }

  public void testCanBindAnnotatedLogger() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Logger.class)
            .annotatedWith(Names.named("anonymous"))
            .toInstance(Logger.getAnonymousLogger());
      }
    });

    assertNull(injector.getInstance(Key.get(Logger.class, Names.named("anonymous"))).getName());
  }
  
  public void testCannotBindLogger() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Logger.class).toInstance(Logger.getAnonymousLogger());
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "A binding to java.util.logging.Logger was already configured");
    }
  }
}
