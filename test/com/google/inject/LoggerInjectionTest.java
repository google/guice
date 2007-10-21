package com.google.inject;

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
    Logger loggerWithoutMember = injector.getInstance(Logger.class);
    assertNull(loggerWithoutMember.getName());
  }
}
