/*
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
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static com.google.inject.Asserts.reserialize;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Throwables;
import com.google.inject.spi.Message;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
@SuppressWarnings("UnusedDeclaration")
public class ProvisionExceptionTest extends TestCase {

  public void testExceptionsCollapsed() {
    try {
      Guice.createInjector().getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(
          e.getMessage(),
          "Error injecting constructor",
          "for the 1st parameter of com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for the 1st parameter of com.google.inject.ProvisionExceptionTest$A");
    }
  }

  /**
   * There's a pass-through of user code in the scope. We want exceptions thrown by Guice to be
   * limited to a single exception, even if it passes through user code.
   */
  public void testExceptionsCollapsedWithScopes() {
    try {
      Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  bind(B.class).in(Scopes.SINGLETON);
                }
              })
          .getInstance(A.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertFalse(e.getMessage().contains("custom provider"));
      assertContains(
          e.getMessage(),
          "Error injecting constructor",
          "for the 1st parameter of com.google.inject.ProvisionExceptionTest$C.setD",
          "for field at com.google.inject.ProvisionExceptionTest$B.c",
          "for the 1st parameter of com.google.inject.ProvisionExceptionTest$A");
    }
  }

  public void testMethodInjectionExceptions() {
    try {
      Guice.createInjector().getInstance(E.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(
          e.getMessage(),
          "Error injecting method",
          "at " + E.class.getName() + ".setObject(ProvisionExceptionTest.java:");
    }
  }

  public void testBindToProviderInstanceExceptions() {
    try {
      Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  bind(D.class).toProvider(new DProvider());
                }
              })
          .getInstance(D.class);
      fail();
    } catch (ProvisionException e) {
      assertTrue(e.getCause() instanceof UnsupportedOperationException);
      assertContains(
          e.getMessage(),
          "1) Error in custom provider, java.lang.UnsupportedOperationException",
          "at " + ProvisionExceptionTest.class.getName(),
          getDeclaringSourcePart(getClass()));
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
      assertContains(
          e.getMessage(),
          "1) User Exception",
          "at " + F.class.getName() + ".<init>(ProvisionExceptionTest.java:");
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderType() {
    try {
      Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  bind(F.class).toProvider(FProvider.class);
                }
              })
          .getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(
          e.getMessage(),
          "1) User Exception",
          "while locating ",
          FProvider.class.getName(),
          "while locating ",
          F.class.getName());
    }
  }

  public void testProvisionExceptionsAreWrappedForBindToProviderInstance() {
    try {
      Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  bind(F.class).toProvider(new FProvider());
                }
              })
          .getInstance(F.class);
      fail();
    } catch (ProvisionException e) {
      assertContains(
          e.getMessage(),
          "1) User Exception",
          "at " + ProvisionExceptionTest.class.getName(),
          getDeclaringSourcePart(getClass()));
    }
  }

  public void testProvisionExceptionIsSerializable() throws IOException {
    try {
      Guice.createInjector().getInstance(A.class);
      fail();
    } catch (ProvisionException expected) {
      ProvisionException reserialized = reserialize(expected);
      assertContains(
          reserialized.getMessage(),
          "1) Error injecting constructor, java.lang.UnsupportedOperationException",
          "at com.google.inject.ProvisionExceptionTest$RealD.<init>()",
          "at Key[type=com.google.inject.ProvisionExceptionTest$RealD, annotation=[none]]",
          "@com.google.inject.ProvisionExceptionTest$C.setD()[0]",
          "at Key[type=com.google.inject.ProvisionExceptionTest$C, annotation=[none]]",
          "@com.google.inject.ProvisionExceptionTest$B.c",
          "at Key[type=com.google.inject.ProvisionExceptionTest$B, annotation=[none]]",
          "@com.google.inject.ProvisionExceptionTest$A.<init>()[0]",
          "at Key[type=com.google.inject.ProvisionExceptionTest$A, annotation=[none]]");
    }
  }

  // The only way to trigger an exception with _multiple_ user controlled throwables is by
  // triggering errors during injector creation.
  public void testMultipleCauses() {
    try {
      Guice.createInjector(
          Stage.PRODUCTION,
          new AbstractModule() {
            @Provides
            @Singleton
            String injectFirst() {
              throw new IllegalArgumentException(new UnsupportedOperationException("Unsupported"));
            }

            @Provides
            @Singleton
            Object injectSecond() {
              throw new NullPointerException("can't inject second either");
            }
          });
      fail();
    } catch (CreationException e) {
      assertContains(
          e.getMessage(),
          "1) Error in custom provider, java.lang.IllegalArgumentException",
          "Caused by: java.lang.IllegalArgumentException: java.lang.UnsupportedOperationException",
          "Caused by: java.lang.UnsupportedOperationException: Unsupported",
          "2) Error in custom provider, java.lang.NullPointerException: can't inject second either",
          "Caused by: java.lang.NullPointerException: can't inject second either",
          "2 errors");
    }
  }

  public void testInjectInnerClass() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(InnerClass.class);
      fail();
    } catch (Exception expected) {
      assertContains(
          expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "while locating " + InnerClass.class.getName());
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception expected) {
      assertContains(
          expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "while locating " + LocalClass.class.getName());
    }
  }

  public void testBindingAnnotationsOnMethodsAndConstructors() {
    try {
      Injector injector = Guice.createInjector();
      injector.getInstance(MethodWithBindingAnnotation.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          MethodWithBindingAnnotation.class.getName() + ".injectMe() is annotated with @",
          Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "while locating " + MethodWithBindingAnnotation.class.getName());
    }

    try {
      Guice.createInjector().getInstance(ConstructorWithBindingAnnotation.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          ConstructorWithBindingAnnotation.class.getName() + ".<init>() is annotated with @",
          Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "at " + ConstructorWithBindingAnnotation.class.getName() + ".class",
          "while locating " + ConstructorWithBindingAnnotation.class.getName());
    }
  }

  public void testBindingAnnotationWarningForScala() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).annotatedWith(Green.class).toInstance("lime!");
              }
            });
    injector.getInstance(LikeScala.class);
  }

  public void testLinkedBindings() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(D.class).to(RealD.class);
              }
            });

    try {
      injector.getInstance(D.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "at " + RealD.class.getName() + ".<init>(ProvisionExceptionTest.java:",
          "while locating " + RealD.class.getName(),
          "while locating " + D.class.getName());
    }
  }

  public void testProviderKeyBindings() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(D.class).toProvider(DProvider.class);
              }
            });

    try {
      injector.getInstance(D.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(
          expected.getMessage(),
          "while locating " + DProvider.class.getName(),
          "while locating " + D.class.getName());
    }
  }

  public void testDuplicateCausesCollapsed() {
    final RuntimeException sharedException = new RuntimeException("fail");
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              addError(sharedException);
              addError(sharedException);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertEquals(sharedException, ce.getCause());
      assertEquals(2, ce.getErrorMessages().size());
      for (Message message : ce.getErrorMessages()) {
        assertEquals(sharedException, message.getCause());
      }
    }
  }

  public void testMultipleDuplicates() {
    final RuntimeException exception1 = new RuntimeException("fail");
    final RuntimeException exception2 = new RuntimeException("abort");
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              addError(exception1);
              addError(exception1);
              addError(exception2);
              addError(exception2);
            }
          });
      fail();
    } catch (CreationException ce) {
      assertNull(ce.getCause());
      assertEquals(4, ce.getErrorMessages().size());

      String e1 = Throwables.getStackTraceAsString(exception1);
      String e2 = Throwables.getStackTraceAsString(exception2);
      assertContains(
          ce.getMessage(),
          "\n1) ",
          e1,
          "\n2) ",
          "(same stack trace as error #1)",
          "\n3) ",
          e2,
          "\n4) ",
          "(same stack trace as error #3)");
    }
  }

  @SuppressWarnings("ClassCanBeStatic")
  private class InnerClass {}

  static class A {
    @Inject
    A(B b) {}
  }

  static class B {
    @Inject C c;
  }

  static class C {
    @Inject
    void setD(RealD d) {}
  }

  static class E {
    @Inject
    void setObject(Object o) {
      throw new UnsupportedOperationException();
    }
  }

  static class MethodWithBindingAnnotation {
    @Inject
    @Green
    void injectMe(String greenString) {}
  }

  static class ConstructorWithBindingAnnotation {
    // Suppress compiler errors by the error-prone checker InjectedConstructorAnnotations,
    // which catches injected constructors with binding annotations.
    @SuppressWarnings("InjectedConstructorAnnotations")
    @Inject
    @Green
    ConstructorWithBindingAnnotation(String greenString) {}
  }

  /**
   * In Scala, fields automatically get accessor methods with the same name. So we don't do
   * misplaced-binding annotation detection if the offending method has a matching field.
   */
  static class LikeScala {
    @Inject @Green String green;

    @Inject
    @Green
    String green() {
      return green;
    }
  }

  @Retention(RUNTIME)
  @Target({FIELD, PARAMETER, CONSTRUCTOR, METHOD})
  @BindingAnnotation
  @interface Green {}

  interface D {}

  static class RealD implements D {
    @Inject
    RealD() {
      throw new UnsupportedOperationException();
    }
  }

  static class DProvider implements Provider<D> {
    @Override
    public D get() {
      throw new UnsupportedOperationException();
    }
  }

  static class F {
    @Inject
    public F() {
      throw new ProvisionException("User Exception", new RuntimeException());
    }
  }

  static class FProvider implements Provider<F> {
    @Override
    public F get() {
      return new F();
    }
  }
}
