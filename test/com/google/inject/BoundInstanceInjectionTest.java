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

import com.google.inject.name.Named;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BoundInstanceInjectionTest extends TestCase {

  public void testInstancesAreInjected() throws CreationException {
    final O o = new O();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(O.class).toInstance(o);
        bind(int.class).toInstance(5);
      }
    });

    assertEquals(5, o.fromMethod);
  }

  static class O {
    int fromMethod;
    @Inject
    void setInt(int i) {
      this.fromMethod = i;
    }
  }

  public void testProvidersAreInjected() throws CreationException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(O.class).toProvider(new Provider<O>() {
          @Inject int i;
          public O get() {
            O o = new O();
            o.setInt(i);
            return o;
          }
        });
        bind(int.class).toInstance(5);
      }
    });

    assertEquals(5, injector.getInstance(O.class).fromMethod);
  }

  public void testMalformedInstance() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Object.class).toInstance(new MalformedInjectable());
        }
      });
      fail();
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), MalformedInjectable.class.getName(),
          ".doublyAnnotated() has more than one ", "annotation annotated with @BindingAnnotation: ",
          Named.class.getName() + " and " + Another.class.getName());
    }
  }

  public void testMalformedProvider() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(String.class).toProvider(new MalformedProvider());
        }
      });
      fail();
    } catch (CreationException expected) {
      Asserts.assertContains(expected.getMessage(), MalformedProvider.class.getName(),
          ".doublyAnnotated() has more than one ", "annotation annotated with @BindingAnnotation: ",
          Named.class.getName() + " and " + Another.class.getName());
    }
  }

  static class MalformedInjectable {
    @Inject void doublyAnnotated(@Named("a") @Another String unused) {}
  }

  static class MalformedProvider implements Provider<String> {
    @Inject void doublyAnnotated(@Named("a") @Another String s) {}

    public String get() {
      return "a";
    }
  }

  @BindingAnnotation @Target({ FIELD, PARAMETER, METHOD }) @Retention(RUNTIME)
  public @interface Another {}
}
