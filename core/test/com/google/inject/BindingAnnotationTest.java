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
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class BindingAnnotationTest extends TestCase {

  public void testAnnotationWithValueMatchesKeyWithTypeOnly() throws CreationException {
    Injector c = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bindConstant().annotatedWith(Blue.class).to("foo");
        bind(BlueFoo.class);
      }
    });

    BlueFoo foo = c.getInstance(BlueFoo.class);

    assertEquals("foo", foo.s);
  }

  public void testRequireExactAnnotationsDisablesFallback() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExactBindingAnnotations();
          bindConstant().annotatedWith(Blue.class).to("foo");
          bind(BlueFoo.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.lang.String annotated with",
          "BindingAnnotationTest$Blue(value=5) was bound",
          "at " + BindingAnnotationTest.class.getName(),
          getDeclaringSourcePart(getClass()));
    }
  }
  
  public void testRequireExactAnnotationsDoesntBreakIfDefaultsExist() {
       Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExactBindingAnnotations();
          bindConstant().annotatedWith(Red.class).to("foo");
          bind(RedFoo.class);
        }
      }).getInstance(RedFoo.class);      
  }

  public void testRequireExactAnnotationsRequireAllOptionals() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          binder().requireExactBindingAnnotations();
          bindConstant().annotatedWith(Color.class).to("foo");
          bind(ColorFoo.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.lang.String annotated with",
          "BindingAnnotationTest$Color",
          "at " + BindingAnnotationTest.class.getName(),
          getDeclaringSourcePart(getClass()));
    }
  }

  public void testAnnotationWithValueThatDoesntMatch() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bindConstant().annotatedWith(createBlue(6)).to("six");
          bind(String.class).toInstance("bar");
          bind(BlueFoo.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "No implementation for java.lang.String annotated with",
          "BindingAnnotationTest$Blue(value=5) was bound",
          "at " + BindingAnnotationTest.class.getName(),
          getDeclaringSourcePart(getClass()));
    }
  }

  static class BlueFoo {
    @Inject @Blue(5) String s; 
  }
  
  static class RedFoo {
    @Inject @Red String s;
  }
  
  static class ColorFoo {
    @Inject @Color(b=2) String s;
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Blue {
    int value();
  }
  
  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Red {
    int r() default 42;
    int g() default 42;
    int b() default 42;
  }
  
  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Color {
    int r() default 0;
    int g() default 0;
    int b();
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
