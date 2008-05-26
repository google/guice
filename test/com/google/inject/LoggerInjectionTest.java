package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import com.google.inject.name.Names;
import com.google.inject.spi.ProviderInstanceBinding;
import junit.framework.TestCase;

import java.util.logging.Logger;

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
  
  public void testLoggerWithoutMember() {
    Injector injector = Guice.createInjector();
    assertNull(injector.getInstance(Logger.class).getName());
    assertNull(injector.getProvider(Logger.class).get().getName());
    assertNull(injector.getBinding(Logger.class).getProvider().get().getName());
    assertNull(((ProviderInstanceBinding<Logger>) injector.getBinding(Logger.class))
        .getProviderInstance().get().getName());
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
