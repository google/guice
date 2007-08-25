package com.google.inject;

import junit.framework.TestCase;

/**
 * @author jessewilson
 */
public class ProvisionExceptionTest extends TestCase {

  public void testExceptionsCollapsed() {
    try {
      Guice.createInjector().getInstance(A.class);
      fail(); 
    }
    catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting constructor");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$D");
      assertContains(e.getMessage(), "for parameter 0 at "
          + "com.google.inject.ProvisionExceptionTest$C.setD");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$C");
      assertContains(e.getMessage(), "for field at "
          + "com.google.inject.ProvisionExceptionTest$B.c");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$B");
      assertContains(e.getMessage(), "for parameter 0 at "
          + "com.google.inject.ProvisionExceptionTest$A");
    }
  }

  public void testMethodInjectionExceptions() {
    try {
      Guice.createInjector().getInstance(E.class);
      fail();
    }
    catch (ProvisionException e) {
      e.printStackTrace();
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting method");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$E");
    }
  }

  public void testProviderExceptions() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(D.class).toProvider(DProvider.class);
        }
      }).getInstance(D.class);
      fail();
    }
    catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error in custom provider");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$D");
    }
  }

  static class A {
    @Inject
    A(B b) { }
  }
  static class B {
    @Inject C c;
  }
  static class C {
    @Inject
    void setD(D d) { }
  }
  static class D {
    D() {
      throw new UnsupportedOperationException();
    }
  }
  static class E {
    @Inject void setObject(Object o) {
      throw new UnsupportedOperationException();
    }
  }
  static class DProvider implements Provider<D> {
    public D get() {
      return new D();
    }
  }

  private void assertContains(String text, String substring) {
    assertTrue(String.format("Expected \"%s\" to contain substring \"%s\"",
        text, substring), text.contains(substring));
  }
}
