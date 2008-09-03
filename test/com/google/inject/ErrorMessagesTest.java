// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
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
    } catch (Exception expected) {
      assertContains(expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "at " + InnerClass.class.getName() + ".class(ErrorMessagesTest.java:");
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception expected) {
      assertContains(expected.getMessage(),
          "Injecting into inner classes is not supported.",
          "at " + LocalClass.class.getName() + ".class(ErrorMessagesTest.java:");
    }
  }

  public void testBindingAnnotationsOnMethodsAndConstructors() {
    try {
      Guice.createInjector().getInstance(B.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          B.class.getName() + ".injectMe() is annotated with @", Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "at " + B.class.getName() + ".injectMe(ErrorMessagesTest.java:");
    }

    try {
      Guice.createInjector().getInstance(C.class);
      fail();
    } catch (ProvisionException expected) {
      assertContains(expected.getMessage(),
          C.class.getName() + ".<init>() is annotated with @", Green.class.getName() + "(), ",
          "but binding annotations should be applied to its parameters instead.",
          "at " + C.class.getName() + ".<init>(ErrorMessagesTest.java:");
    }
  }

  static class B {
    @Inject @Green void injectMe(String greenString) {}
  }

  static class C {
    @Inject @Green C(String greenString) {}
  }

  @Retention(RUNTIME)
  @Target({ FIELD, PARAMETER, CONSTRUCTOR, METHOD })
  @BindingAnnotation
  @interface Green {}


  // TODO(kevinb): many many more

}
