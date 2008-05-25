/**
 * Copyright (C) 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject;

import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ProvisionExceptionTest extends TestCase {

  private static final ProvisionException provisionException
      = new ProvisionException(new RuntimeException(), "User Exception");

  public void testExceptionsCollapsed() {
    try {
      Guice.createInjector().getInstance(A.class);
      fail(); 
    } catch (ProvisionException e) {
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
    } catch (ProvisionException e) {
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
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error in custom provider");
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$D");
    }
  }

  public void testBindToTypeExceptions() {
    try {
      Guice.createInjector().getInstance(D.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$D");
    }
  }

  public void testBindToProviderInstanceExceptions() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(D.class).toProvider(new DProvider());
        }
      }).getInstance(D.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$D");
    }
  }

  /**
   * This test demonstrates that if the user throws a ProvisionException,
   * sometimes we wrap it and sometimes we don't. We should be consistent.
   */
  public void testProvisionExceptionsAreWrappedForBindToType() {
    try {
      Guice.createInjector().getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertNotSame(e, provisionException);
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$F");
      assertSame(provisionException, e.getCause());
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderType() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(F.class).toProvider(FProvider.class);
        }
      }).getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertNotSame(e, provisionException);
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$F");
      assertSame(provisionException, e.getCause());
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderInstance() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(F.class).toProvider(new FProvider());
        }
      }).getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertNotSame(e, provisionException);
      assertContains(e.getMessage(), "while locating "
          + "com.google.inject.ProvisionExceptionTest$F");
      assertSame(provisionException, e.getCause());
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

  static class F {
    @Inject public F() {
      throw provisionException;
    }
  }

  static class FProvider implements Provider<F> {
    public F get() {
      return new F();
    }
  }
}
