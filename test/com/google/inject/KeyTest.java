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
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class KeyTest extends TestCase {

  public void foo(List<String> a, List<String> b) {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface Foo {}

  public void testOfType() {
    Key<Object> k = Key.get(Object.class, Foo.class);
    Key<Integer> ki = k.ofType(int.class);
    assertEquals(int.class, ki.getRawType());
    assertEquals(Foo.class, ki.getAnnotationType());
  }

  public void testEquality() {
    assertEquals(
      new Key<List<String>>(Foo.class) {},
      Key.get(new TypeLiteral<List<String>>() {}, Foo.class)
    );
  }

  public void testTypeEquality() throws Exception {
    Method m = getClass().getMethod("foo", List.class, List.class);
    Type[] types = m.getGenericParameterTypes();
    assertEquals(types[0], types[1]);
    Key<List<String>> k = new Key<List<String>>() {};
    assertEquals(types[0], k.getType().getType());
    assertFalse(types[0].equals(
        new Key<List<Integer>>() {}.getType().getType()));
  }
}
