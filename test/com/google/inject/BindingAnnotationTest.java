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

import java.lang.annotation.Retention;
import java.lang.annotation.Annotation;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingAnnotationTest extends TestCase {

  public void testAnnotationWithValueMatchesKeyWithTypeOnly() throws
      CreationException {
    Injector c = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(Blue.class).to("foo");
        bind(Foo.class);
      }
    });

    Foo foo = c.getInstance(Foo.class);

    assertEquals("foo", foo.s);
  }

  public void testAnnotationWithValueThatDoesntMatch() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bindConstant().annotatedWith(createBlue(6)).to("six");
          bind(String.class).toInstance("bar");
          bind(Foo.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertTrue(expected.getMessage().contains("BindingAnnotationTest$Foo.s"));
      assertTrue(expected.getMessage().contains("Binding to java.lang.String"));
      assertTrue(expected.getMessage().contains("BindingAnnotationTest$Blue(value=5) not found"));
    }
  }

  static class Foo {
    @Inject @Blue(5) String s; 
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Blue {
    int value();
  }

  public Blue createBlue(final int value) {
    return new Blue() {
      public int value() {
        return value;
      }

      public Class<? extends Annotation> annotationType() {
        return Blue.class;
      }

      @Override public boolean equals(Object o) {
        return o instanceof Blue
            && ((Blue) o).value() == value;
      }

      @Override public int hashCode() {
        return 127 * "value".hashCode() ^ value;
      }
    };
  }
}