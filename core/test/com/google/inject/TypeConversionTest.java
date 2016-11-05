/*
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

import static com.google.inject.Asserts.asModuleChain;
import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.getDeclaringSourcePart;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.Iterables;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.TypeConverterBinding;
import java.lang.annotation.Retention;
import java.util.Date;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
public class TypeConversionTest extends TestCase {

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface NumericValue {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface BooleanValue {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface EnumValue {}

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface ClassName {}

  public static class Foo {
    @Inject @BooleanValue Boolean booleanField;
    @Inject @BooleanValue boolean primitiveBooleanField;
    @Inject @NumericValue Byte byteField;
    @Inject @NumericValue byte primitiveByteField;
    @Inject @NumericValue Short shortField;
    @Inject @NumericValue short primitiveShortField;
    @Inject @NumericValue Integer integerField;
    @Inject @NumericValue int primitiveIntField;
    @Inject @NumericValue Long longField;
    @Inject @NumericValue long primitiveLongField;
    @Inject @NumericValue Float floatField;
    @Inject @NumericValue float primitiveFloatField;
    @Inject @NumericValue Double doubleField;
    @Inject @NumericValue double primitiveDoubleField;
    @Inject @EnumValue Bar enumField;
    @Inject @ClassName Class<?> classField;
  }

  public enum Bar {
    TEE,
    BAZ,
    BOB
  }

  public void testOneConstantInjection() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(NumericValue.class).to("5");
                bind(Simple.class);
              }
            });

    Simple simple = injector.getInstance(Simple.class);
    assertEquals(5, simple.i);
  }

  static class Simple {
    @Inject @NumericValue int i;
  }

  public void testConstantInjection() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(NumericValue.class).to("5");
                bindConstant().annotatedWith(BooleanValue.class).to("true");
                bindConstant().annotatedWith(EnumValue.class).to("TEE");
                bindConstant().annotatedWith(ClassName.class).to(Foo.class.getName());
              }
            });

    Foo foo = injector.getInstance(Foo.class);

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
        foo.primitiveDoubleField);

    assertEquals(Bar.TEE, foo.enumField);
    assertEquals(Foo.class, foo.classField);
  }

  public void testConstantInjectionWithExplicitBindingsRequired() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                binder().requireExplicitBindings();
                bind(Foo.class);
                bindConstant().annotatedWith(NumericValue.class).to("5");
                bindConstant().annotatedWith(BooleanValue.class).to("true");
                bindConstant().annotatedWith(EnumValue.class).to("TEE");
                bindConstant().annotatedWith(ClassName.class).to(Foo.class.getName());
              }
            });

    Foo foo = injector.getInstance(Foo.class);

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
        foo.primitiveDoubleField);

    assertEquals(Bar.TEE, foo.enumField);
    assertEquals(Foo.class, foo.classField);
  }

  void checkNumbers(Number... ns) {
    for (Number n : ns) {
      assertEquals(5, n.intValue());
    }
  }

  static class OuterErrorModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new InnerErrorModule());
    }
  }

  static class InnerErrorModule extends AbstractModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(NumericValue.class).to("invalid");
    }
  }

  public void testInvalidInteger() throws CreationException {
    Injector injector = Guice.createInjector(new OuterErrorModule());
    try {
      injector.getInstance(InvalidInteger.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          "Error converting 'invalid' (bound at "
              + InnerErrorModule.class.getName()
              + getDeclaringSourcePart(getClass()),
          asModuleChain(OuterErrorModule.class, InnerErrorModule.class),
          "using TypeConverter<Integer> which matches identicalTo(class java.lang.Integer)"
              + " (bound at [unknown source]).",
          "Reason: java.lang.RuntimeException: For input string: \"invalid\"");
    }
  }

  public static class InvalidInteger {
    @Inject @NumericValue Integer integerField;
  }

  public void testInvalidCharacter() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(NumericValue.class).to("invalid");
              }
            });

    try {
      injector.getInstance(InvalidCharacter.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "Error converting 'invalid'");
      assertContains(expected.getMessage(), "bound at " + getClass().getName());
      assertContains(expected.getMessage(), "to java.lang.Character");
    }
  }

  public static class InvalidCharacter {
    @Inject @NumericValue char foo;
  }

  public void testInvalidEnum() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(NumericValue.class).to("invalid");
              }
            });

    try {
      injector.getInstance(InvalidEnum.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "Error converting 'invalid'");
      assertContains(expected.getMessage(), "bound at " + getClass().getName());
      assertContains(expected.getMessage(), "to " + Bar.class.getName());
    }
  }

  public static class InvalidEnum {
    @Inject @NumericValue Bar foo;
  }

  public void testToInstanceIsTreatedLikeConstant() throws CreationException {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("5");
                bind(LongHolder.class);
              }
            });

    assertEquals(5L, (long) injector.getInstance(LongHolder.class).foo);
  }

  static class LongHolder {
    @Inject Long foo;
  }

  public void testCustomTypeConversion() throws CreationException {
    final Date result = new Date();

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                convertToTypes(
                    Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(result));
                bindConstant().annotatedWith(NumericValue.class).to("Today");
                bind(DateHolder.class);
              }
            });

    assertSame(result, injector.getInstance(DateHolder.class).date);

    Binding<Date> binding = injector.getBinding(Key.get(Date.class, NumericValue.class));
    assertTrue(binding instanceof ConvertedConstantBinding<?>);

    TypeConverterBinding converterBinding =
        ((ConvertedConstantBinding<?>) binding).getTypeConverterBinding();
    assertEquals("CustomConverter", converterBinding.getTypeConverter().toString());

    assertTrue(injector.getTypeConverterBindings().contains(converterBinding));
  }

  static class InvalidCustomValueModule extends AbstractModule {
    @Override
    protected void configure() {
      convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), failingTypeConverter());
      bindConstant().annotatedWith(NumericValue.class).to("invalid");
      bind(DateHolder.class);
    }
  }

  public void testInvalidCustomValue() throws CreationException {
    Module module = new InvalidCustomValueModule();
    try {
      Guice.createInjector(module);
      fail();
    } catch (CreationException expected) {
      Throwable cause = Iterables.getOnlyElement(expected.getErrorMessages()).getCause();
      assertTrue(cause instanceof UnsupportedOperationException);
      assertContains(
          expected.getMessage(),
          "1) Error converting 'invalid' (bound at ",
          getClass().getName(),
          getDeclaringSourcePart(getClass()),
          "to java.util.Date",
          "using BrokenConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(),
          getDeclaringSourcePart(getClass()),
          "Reason: java.lang.UnsupportedOperationException: Cannot convert",
          "at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:");
    }
  }

  static class OuterModule extends AbstractModule {
    private final Module converterModule;

    OuterModule(Module converterModule) {
      this.converterModule = converterModule;
    }

    @Override
    protected void configure() {
      install(new InnerModule(converterModule));
    }
  }

  static class InnerModule extends AbstractModule {
    private final Module converterModule;

    InnerModule(Module converterModule) {
      this.converterModule = converterModule;
    }

    @Override
    protected void configure() {
      install(converterModule);
      bindConstant().annotatedWith(NumericValue.class).to("foo");
      bind(DateHolder.class);
    }
  }

  class ConverterNullModule extends AbstractModule {
    @Override
    protected void configure() {
      convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(null));
    }
  }

  public void testNullCustomValue() {
    try {
      Guice.createInjector(new OuterModule(new ConverterNullModule()));
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Received null converting 'foo' (bound at ",
          getClass().getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterModule.class, InnerModule.class),
          "to java.util.Date",
          "using CustomConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterModule.class, InnerModule.class, ConverterNullModule.class),
          "at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          asModuleChain(OuterModule.class, InnerModule.class));
    }
  }

  class ConverterCustomModule extends AbstractModule {
    @Override
    protected void configure() {
      convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(-1));
    }
  }

  public void testCustomValueTypeMismatch() {
    try {
      Guice.createInjector(new OuterModule(new ConverterCustomModule()));
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Type mismatch converting 'foo' (bound at ",
          getClass().getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterModule.class, InnerModule.class),
          "to java.util.Date",
          "using CustomConverter which matches only(java.util.Date) ",
          "(bound at " + getClass().getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterModule.class, InnerModule.class, ConverterCustomModule.class),
          "Converter returned -1.",
          "at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:",
          asModuleChain(OuterModule.class, InnerModule.class));
    }
  }

  public void testStringIsConvertedOnlyOnce() {
    final TypeConverter converter =
        new TypeConverter() {
          boolean converted = false;

          @Override
          public Object convert(String value, TypeLiteral<?> toType) {
            if (converted) {
              throw new AssertionFailedError("converted multiple times!");
            }
            converted = true;
            return new Date();
          }
        };

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), converter);
                bindConstant().annotatedWith(NumericValue.class).to("unused");
              }
            });

    Date first = injector.getInstance(Key.get(Date.class, NumericValue.class));
    Date second = injector.getInstance(Key.get(Date.class, NumericValue.class));
    assertSame(first, second);
  }

  class OuterAmbiguousModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new InnerAmbiguousModule());
    }
  }

  class InnerAmbiguousModule extends AbstractModule {
    @Override
    protected void configure() {
      install(new Ambiguous1Module());
      install(new Ambiguous2Module());
      bindConstant().annotatedWith(NumericValue.class).to("foo");
      bind(DateHolder.class);
    }
  }

  class Ambiguous1Module extends AbstractModule {
    @Override
    protected void configure() {
      convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(new Date()));
    }
  }

  class Ambiguous2Module extends AbstractModule {
    @Override
    protected void configure() {
      convertToTypes(Matchers.only(TypeLiteral.get(Date.class)), mockTypeConverter(new Date()));
    }
  }

  public void testAmbiguousTypeConversion() {
    try {
      Guice.createInjector(new OuterAmbiguousModule());
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "1) Multiple converters can convert 'foo' (bound at ",
          getClass().getName(),
          getDeclaringSourcePart(getClass()),
          asModuleChain(OuterAmbiguousModule.class, InnerAmbiguousModule.class),
          "to java.util.Date:",
          "CustomConverter which matches only(java.util.Date) (bound at "
              + Ambiguous1Module.class.getName()
              + getDeclaringSourcePart(getClass()),
          asModuleChain(
              OuterAmbiguousModule.class, InnerAmbiguousModule.class, Ambiguous1Module.class),
          "and",
          "CustomConverter which matches only(java.util.Date) (bound at "
              + Ambiguous2Module.class.getName()
              + getDeclaringSourcePart(getClass()),
          asModuleChain(
              OuterAmbiguousModule.class, InnerAmbiguousModule.class, Ambiguous2Module.class),
          "Please adjust your type converter configuration to avoid overlapping matches.",
          "at " + DateHolder.class.getName() + ".date(TypeConversionTest.java:");
    }
  }

  TypeConverter mockTypeConverter(final Object result) {
    return new TypeConverter() {
      @Override
      public Object convert(String value, TypeLiteral<?> toType) {
        return result;
      }

      @Override
      public String toString() {
        return "CustomConverter";
      }
    };
  }

  private static TypeConverter failingTypeConverter() {
    return new TypeConverter() {
      @Override
      public Object convert(String value, TypeLiteral<?> toType) {
        throw new UnsupportedOperationException("Cannot convert");
      }

      @Override
      public String toString() {
        return "BrokenConverter";
      }
    };
  }

  static class DateHolder {
    @Inject @NumericValue Date date;
  }

  public void testCannotConvertUnannotatedBindings() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("55");
              }
            });

    try {
      injector.getInstance(Integer.class);
      fail("Converted an unannotated String to an Integer");
    } catch (ConfigurationException expected) {
      Asserts.assertContains(
          expected.getMessage(), "Could not find a suitable constructor in java.lang.Integer.");
    }
  }
}
