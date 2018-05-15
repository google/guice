// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import junit.framework.TestCase;

/**
 * Tests relating to modules.
 *
 * @author kevinb
 */
public class ModuleTest extends TestCase {

  static class A implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(X.class);
      binder.install(new B());
      binder.install(new C());
    }
  }

  static class B implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(Y.class);
      binder.install(new D());
    }
  }

  static class C implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(Z.class);
      binder.install(new D());
    }
  }

  static class D implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(W.class);
    }

    @Override
    @SuppressWarnings("EqualsBrokenForNull") // intentionally NPE on null for the test
    public boolean equals(Object obj) {
      return obj.getClass() == D.class; // we're all equal in the eyes of guice
    }

    @Override
    public int hashCode() {
      return D.class.hashCode();
    }
  }

  static class X {}

  static class Y {}

  static class Z {}

  static class W {}

  public void testDiamond() throws Exception {
    Guice.createInjector(new A());
  }
}
