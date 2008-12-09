/**
 * Copyright (C) 2008 Google Inc.
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
import com.google.inject.util.Types;
import static com.google.inject.util.Types.listOf;
import java.util.List;
import junit.framework.TestCase;

/**
 * Demonstrates type reification.
 * 
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class TypeLiteralInjectionTest extends TestCase {

  public void testBindingToRawTypeLiteralIsNotAllowed() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(TypeLiteral.class).toInstance(TypeLiteral.get(String.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Binding to core guice framework type is not allowed: TypeLiteral");
    }
  }

  public void testBindingToParameterizedTypeLiteralIsNotAllowed() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(new TypeLiteral<TypeLiteral<String>>() {})
              .toInstance(TypeLiteral.get(String.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Binding to core guice framework type is not allowed: TypeLiteral");
    }
  }

  public void testInjectTypeLiteralWithRawTypes() {
    C c = Guice.createInjector().getInstance(C.class);
    assertEquals(TypeLiteral.get(String.class), c.string);
    assertEquals(TypeLiteral.get(A.class), c.a);

    try {
      Guice.createInjector().getInstance(B.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), TypeLiteral.class.getName() + "<java.util.List<T>> "
          + "cannot be used as a key; It is not fully specified.");
    }
  }

  public void testInjectTypeLiteralWithClassTypes() {
    B<Integer> b = Guice.createInjector().getInstance(new Key<B<Integer>>() {});
    assertEquals(TypeLiteral.get(String.class), b.string);
    assertEquals(TypeLiteral.get(Integer.class), b.t);
    assertEquals(TypeLiteral.get(listOf(Integer.class)), b.listOfT);
    assertEquals(TypeLiteral.get(listOf(Types.subtypeOf(Integer.class))), b.listOfWildcardT);
  }

  public void testInjectRawTypeLiteral() {
    try {
      Guice.createInjector().getInstance(TypeLiteral.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "Cannot inject a TypeLiteral that has no type parameter");
    }
  }

  static class A<T> {
    @Inject TypeLiteral<String> string;
    @Inject TypeLiteral<List<T>> listOfT;
    @Inject TypeLiteral<List<? extends T>> listOfWildcardT;
  }

  static class B<T> extends A<T> {
    @Inject TypeLiteral<T> t;
  }

  static class C<T> {
    @Inject TypeLiteral<String> string;
    @Inject TypeLiteral<A> a;
    T t;
  }
}
