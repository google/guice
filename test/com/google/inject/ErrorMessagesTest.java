// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

/**
 * Tests the error messages produced by Guice.
 *
 * @author Kevin Bourrillion
 */
public class ErrorMessagesTest extends TestCase {

  private class InnerClass {}

  public void testInjectInnerClass() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(InnerClass.class);
      fail();
    } catch (Exception e) {
      // TODO(kevinb): why does the source come out as unknown??
      assertTrue(e.getMessage().contains(
          "Injecting into inner classes is not supported."));
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception e) {
      // TODO(kevinb): why does the source come out as unknown??
      assertTrue(e.getMessage().contains(
          "Injecting into inner classes is not supported."));
    }
  }

  // TODO(kevinb): many many more

}
