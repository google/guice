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

import java.util.Map;
import java.util.HashMap;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ConstantConversionTest extends TestCase {

  public static class Foo {
    @Inject @Named("#") Integer integerField;
    @Inject @Named("#") int primitiveIntField;
    @Inject @Named("#") Long longField;
    @Inject @Named("#") long primitiveLongField;
    @Inject @Named("boolean") Boolean booleanField;
    @Inject @Named("boolean") boolean primitiveBooleanField;
    @Inject @Named("#") Byte byteField;
    @Inject @Named("#") byte primitiveByteField;
    @Inject @Named("#") Short shortField;
    @Inject @Named("#") short primitiveShortField;
    @Inject @Named("#") Float floatField;
    @Inject @Named("#") float primitiveFloatField;
    @Inject @Named("#") Double doubleField;
    @Inject @Named("#") short primitiveDoubleField;
    @Inject @Named("enum") Bar enumField;
    @Inject @Named("class") Class<?> classField;
  }

  public enum Bar {
    TEE, BAZ, BOB;
  }

  @Named("foo")
  public void testNamed() throws NoSuchMethodException {
    Named named = getClass().getMethod("testNamed").getAnnotation(Named.class);
    assertEquals(named, new NamedImpl("foo"));
    assertEquals(new NamedImpl("foo"), named);

    assertEquals(Key.get(String.class, new NamedImpl("foo")),
        Key.get(String.class, named));
  }

  public void testOneConstantInjection() throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind("#").to("5");
    builder.bind(Simple.class);
    Container container = builder.create(false);
    Simple simple = container.getFactory(Simple.class).get();
    assertEquals(5, simple.i);
  }

  static class Simple {
    @Inject @Named("#") int i;
  }

  public void testConstantInjection() throws ContainerCreationException {
    Map<String, String> properties = new HashMap<String, String>() {{
      put("#", "5");
      put("boolean", "true");
      put("enum", "TEE");
      put("class", Foo.class.getName());
    }};

    ContainerBuilder b = new ContainerBuilder();
    b.bindProperties(properties);
    Container c = b.create(false);
    Foo foo = c.getFactory(Foo.class).get();

    checkNumbers(
      foo.integerField,
      foo.primitiveIntField,
      foo.longField,
      foo.primitiveLongField,
      foo.byteField,
      foo.primitiveByteField,
      foo.shortField,
      foo.primitiveShortField,
      foo.floatField,
      foo.primitiveFloatField,
      foo.doubleField,
      foo.primitiveDoubleField
    );

    assertEquals(Bar.TEE, foo.enumField);
    assertEquals(Foo.class, foo.classField);
  }

  void checkNumbers(Number... ns) {
    for (Number n : ns) {
      assertEquals(5, n.intValue());
    }
  }

  public void testInvalidInteger() throws ContainerCreationException {
    ContainerBuilder b = new ContainerBuilder();
    b.bind("#").to("invalid");
    Container c = b.create(false);
    try {
      c.getFactory(InvalidInteger.class).get();
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidInteger {
    @Inject @Named("#") Integer integerField;
  }

  public void testInvalidCharacter() throws ContainerCreationException {
    ContainerBuilder b = new ContainerBuilder();
    b.bind("foo").to("invalid");
    Container c = b.create(false);
    try {
      c.getFactory(InvalidCharacter.class).get();
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidCharacter {
    @Inject @Named("foo") char foo;
  }

  public void testInvalidEnum() throws ContainerCreationException {
    ContainerBuilder b = new ContainerBuilder();
    b.bind("foo").to("invalid");
    Container c = b.create(false);
    try {
      c.getFactory(InvalidEnum.class).get();
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidEnum {
    @Inject @Named("foo") Bar foo;
  }
}
