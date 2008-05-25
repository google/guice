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

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class KeyTest extends TestCase {

  public void foo(List<String> a, List<String> b) {}
  public void bar(Provider<List<String>> a) {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface Foo {}

  public void testOfType() {
    Key<Object> k = Key.get(Object.class, Foo.class);
    Key<Integer> ki = k.ofType(int.class);
    assertEquals(int.class, ki.getRawType());
    assertEquals(Foo.class, ki.getAnnotationType());
  }

  public void testKeyEquality() {
    Key<List<String>> a = new Key<List<String>>(Foo.class) {};
    Key<List<String>> b = Key.get(new TypeLiteral<List<String>>() {}, Foo.class);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  public void testProviderKey() throws NoSuchMethodException {
    Key<?> actual = Key.get(getClass().getMethod("foo", List.class, List.class)
        .getGenericParameterTypes()[0]).providerKey();
    Key<?> expected = Key.get(getClass().getMethod("bar", Provider.class)
        .getGenericParameterTypes()[0]);
    assertTrue(expected.equals(actual));
    assertTrue(actual.equals(expected));
    assertEquals(expected.hashCode(), actual.hashCode());
    assertEquals(expected.toString(), actual.toString());
  }

  public void testTypeEquality() throws Exception {
    Method m = getClass().getMethod("foo", List.class, List.class);
    Type[] types = m.getGenericParameterTypes();
    assertEquals(types[0], types[1]);
    Key<List<String>> k = new Key<List<String>>() {};
    assertEquals(types[0], k.getTypeLiteral().getType());
    assertFalse(types[0].equals(
        new Key<List<Integer>>() {}.getTypeLiteral().getType()));
  }
}
