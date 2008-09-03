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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import java.io.IOException;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
@SuppressWarnings("UnusedDeclaration")
public class ProvisionExceptionTest extends TestCase {

  public void testExceptionsCollapsed() {
    try {
      Guice.createInjector().getInstance(A.class);
      fail(); 
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting constructor",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$A");
    }
  }

  /**
   * There's a pass-through of user code in the scope. We want exceptions thrown by Guice to be
   * limited to a single exception, even if it passes through user code.
   */
  public void testExceptionsCollapsedWithScopes() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(B.class).in(Scopes.SINGLETON);
        }
      }).getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertFalse(e.getMessage().contains("custom provider"));
      assertContains(e.getMessage(), "Error injecting constructor",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for parameter 0 at com.google.inject.ProvisionExceptionTest$A");
    }
  }

  public void testMethodInjectionExceptions() {
    try {
      Guice.createInjector().getInstance(E.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(), "Error injecting method",
          "at " + E.class.getName() + ".setObject(ProvisionExceptionTest.java:");
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
      assertContains(e.getMessage(),
          "1) Error in custom provider, java.lang.UnsupportedOperationException",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
    }
  }

  public void testBindToTypeExceptions() {
    try {
      Guice.createInjector().getInstance(D.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(e.getMessage(),
          "1) Error injecting constructor, java.lang.UnsupportedOperationException",
          "at " + D.class.getName() + ".<init>(ProvisionExceptionTest.java:");
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
      assertContains(e.getMessage(),
          "1) Error in custom provider, java.lang.UnsupportedOperationException",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
    }
  }

  /**
   * This test demonstrates that if the user throws a ProvisionException, we wrap it to add context.
   */
  public void testProvisionExceptionsAreWrappedForBindToType() {
    try {
      Guice.createInjector().getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(e.getMessage(),
          "1) Error injecting constructor, com.google.inject.ProvisionException: User Exception",
          "at " + F.class.getName() + ".<init>(ProvisionExceptionTest.java:");
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
      assertContains(e.getMessage(),
          "1) Error in custom provider, com.google.inject.ProvisionException: User Exception",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
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
      assertContains(e.getMessage(),
          "1) Error in custom provider, com.google.inject.ProvisionException: User Exception",
          "at " + ProvisionExceptionTest.class.getName(), ".configure(ProvisionExceptionTest.java");
    }
  }

  public void testProvisionExceptionIsSerializable() throws IOException {
    try {
      Guice.createInjector().getInstance(A.class);
      fail();
    } catch (ProvisionException expected) {
      assertSimilarWhenReserialized(expected);
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

  static class F {
    @Inject public F() {
      throw new ProvisionException("User Exception", new RuntimeException());
    }
  }

  static class FProvider implements Provider<F> {
    public F get() {
      return new F();
    }
  }
}
