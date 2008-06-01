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

import static com.google.inject.Asserts.assertEqualWhenReserialized;
import static com.google.inject.Asserts.assertEqualsBothWays;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.TestCase;

import java.io.IOException;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

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
    assertEqualsBothWays(a, b);
  }

  public void testProviderKey() throws NoSuchMethodException {
    Key<?> actual = Key.get(getClass().getMethod("foo", List.class, List.class)
        .getGenericParameterTypes()[0]).providerKey();
    Key<?> expected = Key.get(getClass().getMethod("bar", Provider.class)
        .getGenericParameterTypes()[0]);
    assertEqualsBothWays(expected, actual);
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


  /**
   * Key canonicalizes {@link int.class} to {@code Integer.class}, and
   * won't expose wrapper types.
   */
  public void testPrimitivesAndWrappersAreEqual() {
    Class[] primitives = new Class[] {
        boolean.class, byte.class, short.class, int.class, long.class,
        float.class, double.class, char.class, void.class
    };
    Class[] wrappers = new Class[] {
        Boolean.class, Byte.class, Short.class, Integer.class, Long.class,
        Float.class, Double.class, Character.class, Void.class
    };

    for (int t = 0; t < primitives.length; t++) {
      @SuppressWarnings("unchecked")
      Key primitiveKey = Key.get(primitives[t]);
      @SuppressWarnings("unchecked")
      Key wrapperKey = Key.get(wrappers[t]);

      assertEquals(primitiveKey, wrapperKey);
      assertEquals(wrappers[t], primitiveKey.getRawType());
      assertEquals(wrappers[t], wrapperKey.getRawType());
      assertEquals(wrappers[t], primitiveKey.getTypeLiteral().getType());
      assertEquals(wrappers[t], wrapperKey.getTypeLiteral().getType());
    }
    
    Key<Integer> integerKey = Key.get(Integer.class);

    Class<Integer> intClassLiteral = int.class;
    assertEquals(integerKey, Key.get(intClassLiteral));
    assertEquals(integerKey, Key.get(intClassLiteral, Named.class));
    assertEquals(integerKey, Key.get(intClassLiteral, Names.named("int")));

    Type intType = int.class;
    assertEquals(integerKey, Key.get(intType));
    assertEquals(integerKey, Key.get(intType, Named.class));
    assertEquals(integerKey, Key.get(intType, Names.named("int")));

    TypeLiteral<Integer> intTypeLiteral = TypeLiteral.get(int.class);
    assertEquals(integerKey, Key.get(intTypeLiteral));
    assertEquals(integerKey, Key.get(intTypeLiteral, Named.class));
    assertEquals(integerKey, Key.get(intTypeLiteral, Names.named("int")));
  }

  public void testSerialization() throws IOException {
    assertEqualWhenReserialized(Key.get(B.class));
    assertEqualWhenReserialized(Key.get(B.class, Names.named("bee")));
    assertEqualWhenReserialized(Key.get(B.class, Named.class));
    assertEqualWhenReserialized(Key.get(B[].class));
    assertEqualWhenReserialized(Key.get(new TypeLiteral<Map<List<B>, B>>() {}));
    assertEqualWhenReserialized(Key.get(new TypeLiteral<List<B[]>>() {}));
    assertEqualWhenReserialized(new Key<List<B[]>>() {});
  }

  interface B {}
}
