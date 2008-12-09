/**
 * Copyright (C) 2006 Google Inc.
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
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class RequestInjectionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface ForField {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface ForMethod {}

  protected void setUp() throws Exception {
    super.setUp();
    HasInjections.staticField = 0;
    HasInjections.staticMethod = null;
  }

  public void testInjectMembers() {
    final HasInjections hi = new HasInjections();

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(ForMethod.class).to("test");
        bindConstant().annotatedWith(ForField.class).to(5);
        requestInjection(hi);
      }
    });

    assertEquals("test", hi.instanceMethod);
    assertEquals(5, hi.instanceField);
    assertNull(HasInjections.staticMethod);
    assertEquals(0, HasInjections.staticField);
  }

  public void testInjectStatics() throws CreationException {
    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(ForMethod.class).to("test");
        bindConstant().annotatedWith(ForField.class).to(5);
        requestStaticInjection(HasInjections.class);
      }
    });

    assertEquals("test", HasInjections.staticMethod);
    assertEquals(5, HasInjections.staticField);
  }
  
  public void testInjectMembersAndStatics() {
    final HasInjections hi = new HasInjections();

    Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(ForMethod.class).to("test");
        bindConstant().annotatedWith(ForField.class).to(5);
        requestStaticInjection(HasInjections.class);
        requestInjection(hi);
      }
    });

    assertEquals("test", hi.instanceMethod);
    assertEquals(5, hi.instanceField);
    assertEquals("test", HasInjections.staticMethod);
    assertEquals(5, HasInjections.staticField);
  }

  public void testValidationErrorOnInjectedMembers() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          requestInjection(new NeedsRunnable());
        }
      });
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for java.lang.Runnable was bound",
          "at " + NeedsRunnable.class.getName(), ".runnable(RequestInjectionTest.java:");
    }
  }

  public void testInjectionErrorOnInjectedMembers() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Runnable.class).toProvider(new Provider<Runnable>() {
            public Runnable get() {
              throw new UnsupportedOperationException();
            }
          });
          requestInjection(new NeedsRunnable());
        }
      });
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error in custom provider, java.lang.UnsupportedOperationException",
          "for field at " + NeedsRunnable.class.getName() + ".runnable(RequestInjectionTest.java:",
          "at " + getClass().getName(), ".configure(RequestInjectionTest.java:");
    }
  }

  public void testUserExceptionWhileInjectingInstance() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          requestInjection(new BlowsUpOnInject());
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error injecting method, java.lang.UnsupportedOperationException: Pop",
          "at " + BlowsUpOnInject.class.getName() + ".injectInstance(RequestInjectionTest.java:");
    }
  }

  public void testUserExceptionWhileInjectingStatically() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          requestStaticInjection(BlowsUpOnInject.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Error injecting method, java.lang.UnsupportedOperationException: Snap",
          "at " + BlowsUpOnInject.class.getName() + ".injectStatically(RequestInjectionTest.java:");
    }
  }

  static class NeedsRunnable {
    @Inject Runnable runnable;
  }

  static class HasInjections {

    @Inject @ForField static int staticField;
    @Inject @ForField int instanceField;

    static String staticMethod;
    String instanceMethod;

    @Inject static void setStaticMethod(@ForMethod String staticMethod) {
      HasInjections.staticMethod = staticMethod;
    }

    @Inject void setInstanceS(@ForMethod String instanceS) {
      this.instanceMethod = instanceS;
    }
  }

  static class BlowsUpOnInject {
    @Inject void injectInstance() {
      throw new UnsupportedOperationException("Pop");
    }

    @Inject static void injectStatically() {
      throw new UnsupportedOperationException("Snap");
    }
  }
}
