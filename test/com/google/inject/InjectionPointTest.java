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

import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionPoint;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class InjectionPointTest extends TestCase {

  public @Inject @Named("a") String foo;
  public @Inject void bar(@Named("b") String param) {}

  public static class Constructable {
    @Inject public Constructable(@Named("c") String param) {}
  }

  public void testFieldInjectionPoint() throws NoSuchFieldException, IOException {
    Field fooField = getClass().getField("foo");
    InjectionPoint<String> injectionPoint
        = InjectionPoint.newInstance(fooField, false, Key.get(String.class, Names.named("a")));

    assertEquals("InjectionPoint["
        + "key=Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=a)], "
        + "allowsNull=false, "
        + "member=com.google.inject.InjectionPointTest.foo]", injectionPoint.toString());
    assertEquals(fooField, injectionPoint.getMember());
    assertEquals(-1, injectionPoint.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("a")), injectionPoint.getKey());
    assertEquals(false, injectionPoint.allowsNull());
    Asserts.assertSimilarWhenReserialized(injectionPoint);
  }

  public void testMethodInjectionPoint() throws NoSuchMethodException, IOException {
    Method barMethod = getClass().getMethod("bar", String.class);
    InjectionPoint<String> injectionPoint
        = InjectionPoint.newInstance(barMethod, 0, false, Key.get(String.class, Names.named("b")));

    assertEquals("InjectionPoint["
        + "key=Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=b)], "
        + "allowsNull=false, "
        + "member=com.google.inject.InjectionPointTest.bar(), "
        + "parameterIndex=0]", injectionPoint.toString());
    assertEquals(barMethod, injectionPoint.getMember());
    assertEquals(0, injectionPoint.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("b")), injectionPoint.getKey());
    assertEquals(false, injectionPoint.allowsNull());
    Asserts.assertSimilarWhenReserialized(injectionPoint);
  }

  public void testConstructorInjectionPoint() throws NoSuchMethodException, IOException {
    Constructor<?> constructor = Constructable.class.getConstructor(String.class);
    InjectionPoint<String> injectionPoint
        = InjectionPoint.newInstance(constructor, 0, true, Key.get(String.class, Names.named("c")));

    assertEquals("InjectionPoint["
        + "key=Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=c)], "
        + "allowsNull=true, "
        + "member=com.google.inject.InjectionPointTest$Constructable.<init>(), "
        + "parameterIndex=0]", injectionPoint.toString());
    assertEquals(constructor, injectionPoint.getMember());
    assertEquals(0, injectionPoint.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("c")), injectionPoint.getKey());
    assertEquals(true, injectionPoint.allowsNull());
    Asserts.assertSimilarWhenReserialized(injectionPoint);
  }
  
  public void testPlainKeyInjectionPoint() throws IOException {
    InjectionPoint<String> injectionPoint
        = InjectionPoint.newInstance(Key.get(String.class, Names.named("d")));

    assertEquals("InjectionPoint["
        + "key=Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=d)], "
        + "allowsNull=true]", injectionPoint.toString());
    assertNull(injectionPoint.getMember());
    assertEquals(-1, injectionPoint.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("d")), injectionPoint.getKey());
    assertEquals(true, injectionPoint.allowsNull());
    Asserts.assertSimilarWhenReserialized(injectionPoint);
  }
}
