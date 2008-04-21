package com.google.inject;

import junit.framework.TestCase;
import java.util.logging.Logger;

import com.google.inject.spi.ProviderInstanceBinding;

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
}
