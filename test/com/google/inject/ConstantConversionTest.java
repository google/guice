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
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ConstantConversionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation @interface NumericValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface BooleanValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface EnumValue {}

  @Retention(RUNTIME)
  @BindingAnnotation @interface ClassName {}

  public static class Foo {
    @Inject @NumericValue Integer integerField;
    @Inject @NumericValue int primitiveIntField;
    @Inject @NumericValue Long longField;
    @Inject @NumericValue long primitiveLongField;
    @Inject @BooleanValue Boolean booleanField;
    @Inject @BooleanValue boolean primitiveBooleanField;
    @Inject @NumericValue Byte byteField;
    @Inject @NumericValue byte primitiveByteField;
    @Inject @NumericValue Short shortField;
    @Inject @NumericValue short primitiveShortField;
    @Inject @NumericValue Float floatField;
    @Inject @NumericValue float primitiveFloatField;
    @Inject @NumericValue Double doubleField;
    @Inject @NumericValue short primitiveDoubleField;
    @Inject @EnumValue Bar enumField;
    @Inject @ClassName Class<?> classField;
  }

  public enum Bar {
    TEE, BAZ, BOB;
  }

  public void testOneConstantInjection() throws CreationException {
    BinderImpl builder = new BinderImpl();
    builder.bindConstant().annotatedWith(NumericValue.class).to("5");
    builder.bind(Simple.class);
    Injector injector = builder.createInjector();
    Simple simple = injector.getInstance(Simple.class);
    assertEquals(5, simple.i);
  }

  static class Simple {
    @Inject @NumericValue int i;
  }

  public void testConstantInjection() throws CreationException {
    BinderImpl b = new BinderImpl();
    b.bindConstant().annotatedWith(NumericValue.class).to("5");
    b.bindConstant().annotatedWith(BooleanValue.class).to("true");
    b.bindConstant().annotatedWith(EnumValue.class).to("TEE");
    b.bindConstant().annotatedWith(ClassName.class).to(Foo.class.getName());
    Injector c = b.createInjector();
    Foo foo = c.getInstance(Foo.class);

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

  public void testInvalidInteger() throws CreationException {
    BinderImpl b = new BinderImpl();
    b.bindConstant().annotatedWith(NumericValue.class).to("invalid");
    Injector c = b.createInjector();
    try {
      c.getInstance(InvalidInteger.class);
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidInteger {
    @Inject @NumericValue Integer integerField;
  }

  public void testInvalidCharacter() throws CreationException {
    BinderImpl b = new BinderImpl();
    b.bindConstant().annotatedWith(NumericValue.class).to("invalid");
    Injector c = b.createInjector();
    try {
      c.getInstance(InvalidCharacter.class);
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidCharacter {
    @Inject @NumericValue char foo;
  }

  public void testInvalidEnum() throws CreationException {
    BinderImpl b = new BinderImpl();
    b.bindConstant().annotatedWith(NumericValue.class).to("invalid");
    Injector c = b.createInjector();
    try {
      c.getInstance(InvalidEnum.class);
      fail();
    } catch (ConfigurationException e) { /* expected */ }
  }

  public static class InvalidEnum {
    @Inject @NumericValue Bar foo;
  }

  public void testToInstanceIsTreatedLikeConstant() throws CreationException {
    BinderImpl b = new BinderImpl();
    b.bind(String.class).toInstance("5");
    b.bind(LongHolder.class);
    Injector c = b.createInjector();
  }

  static class LongHolder {
    @Inject
    LongHolder(long foo) {}
  }
}
