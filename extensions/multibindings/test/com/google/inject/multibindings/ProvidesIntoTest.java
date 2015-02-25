/**
 * Copyright (C) 2015 Google Inc.
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

package com.google.inject.multibindings;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.multibindings.ProvidesIntoOptional.Type;
import com.google.inject.name.Named;

import junit.framework.TestCase;

import java.lang.annotation.Retention;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Tests the various @ProvidesInto annotations.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class ProvidesIntoTest extends TestCase {

  public void testAnnotation() throws Exception {
    Injector injector = Guice.createInjector(MultibindingsScanner.asModule(), new AbstractModule() {
      @Override protected void configure() {}

      @ProvidesIntoSet
      @Named("foo")
      String setFoo() { return "foo"; }

      @ProvidesIntoSet
      @Named("foo")
      String setFoo2() { return "foo2"; }

      @ProvidesIntoSet
      @Named("bar")
      String setBar() { return "bar"; }

      @ProvidesIntoSet
      @Named("bar")
      String setBar2() { return "bar2"; }

      @ProvidesIntoSet
      String setNoAnnotation() { return "na"; }

      @ProvidesIntoSet
      String setNoAnnotation2() { return "na2"; }

      @ProvidesIntoMap
      @StringMapKey("fooKey")
      @Named("foo")
      String mapFoo() { return "foo"; }

      @ProvidesIntoMap
      @StringMapKey("foo2Key")
      @Named("foo")
      String mapFoo2() { return "foo2"; }

      @ProvidesIntoMap
      @ClassMapKey(String.class)
      @Named("bar")
      String mapBar() { return "bar"; }

      @ProvidesIntoMap
      @ClassMapKey(Number.class)
      @Named("bar")
      String mapBar2() { return "bar2"; }

      @ProvidesIntoMap
      @TestEnumKey(TestEnum.A)
      String mapNoAnnotation() { return "na"; }

      @ProvidesIntoMap
      @TestEnumKey(TestEnum.B)
      String mapNoAnnotation2() { return "na2"; }

      @ProvidesIntoMap
      @WrappedKey(number = 1)
      Number wrapped1() { return 11; }

      @ProvidesIntoMap
      @WrappedKey(number = 2)
      Number wrapped2() { return 22; }

      @ProvidesIntoOptional(ProvidesIntoOptional.Type.DEFAULT)
      @Named("foo")
      String optionalDefaultFoo() { return "foo"; }

      @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
      @Named("foo")
      String optionalActualFoo() { return "foo2"; }

      @ProvidesIntoOptional(ProvidesIntoOptional.Type.DEFAULT)
      @Named("bar")
      String optionalDefaultBar() { return "bar"; }

      @ProvidesIntoOptional(ProvidesIntoOptional.Type.ACTUAL)
      String optionalActualBar() { return "na2"; }
    });

    Set<String> fooSet = injector.getInstance(new Key<Set<String>>(named("foo")) {});
    assertEquals(ImmutableSet.of("foo", "foo2"), fooSet);

    Set<String> barSet = injector.getInstance(new Key<Set<String>>(named("bar")) {});
    assertEquals(ImmutableSet.of("bar", "bar2"), barSet);

    Set<String> noAnnotationSet = injector.getInstance(new Key<Set<String>>() {});
    assertEquals(ImmutableSet.of("na", "na2"), noAnnotationSet);

    Map<String, String> fooMap =
        injector.getInstance(new Key<Map<String, String>>(named("foo")) {});
    assertEquals(ImmutableMap.of("fooKey", "foo", "foo2Key", "foo2"), fooMap);

    Map<Class<?>, String> barMap =
        injector.getInstance(new Key<Map<Class<?>, String>>(named("bar")) {});
    assertEquals(ImmutableMap.of(String.class, "bar", Number.class, "bar2"), barMap);

    Map<TestEnum, String> noAnnotationMap =
        injector.getInstance(new Key<Map<TestEnum, String>>() {});
    assertEquals(ImmutableMap.of(TestEnum.A, "na", TestEnum.B, "na2"), noAnnotationMap);

    Map<WrappedKey, Number> wrappedMap =
        injector.getInstance(new Key<Map<WrappedKey, Number>>() {});
    assertEquals(ImmutableMap.of(wrappedKeyFor(1), 11, wrappedKeyFor(2), 22), wrappedMap);

    Optional<String> fooOptional =
        injector.getInstance(new Key<Optional<String>>(named("foo")) {});
    assertEquals("foo2", fooOptional.get());

    Optional<String> barOptional =
        injector.getInstance(new Key<Optional<String>>(named("bar")) {});
    assertEquals("bar", barOptional.get());

    Optional<String> noAnnotationOptional =
        injector.getInstance(new Key<Optional<String>>() {});
    assertEquals("na2", noAnnotationOptional.get());
  }

  enum TestEnum {
    A, B
  }

  @MapKey(unwrapValue = true)
  @Retention(RUNTIME)
  @interface TestEnumKey {
    TestEnum value();
  }

  @MapKey(unwrapValue = false)
  @Retention(RUNTIME)
  @interface WrappedKey {
    int number();
  }
  
  @SuppressWarnings("unused") @WrappedKey(number=1) private static Object wrappedKey1Holder;
  @SuppressWarnings("unused") @WrappedKey(number=2) private static Object wrappedKey2Holder;
  WrappedKey wrappedKeyFor(int number) throws Exception {
    Field field;
    switch (number) {
      case 1:
        field = ProvidesIntoTest.class.getDeclaredField("wrappedKey1Holder");
        break;
      case 2:
        field = ProvidesIntoTest.class.getDeclaredField("wrappedKey2Holder");
        break;
      default:
        throw new IllegalArgumentException("only 1 or 2 supported");
    }
    return field.getAnnotation(WrappedKey.class);
  }
  
  public void testDoubleScannerIsIgnored() {
    Injector injector = Guice.createInjector(
        MultibindingsScanner.asModule(),
        MultibindingsScanner.asModule(),
        new AbstractModule() {
          @Override protected void configure() {}
          @ProvidesIntoSet String provideFoo() { return "foo"; }
        }
    );
    assertEquals(ImmutableSet.of("foo"), injector.getInstance(new Key<Set<String>>() {}));
  }
  
  @MapKey(unwrapValue = true)
  @Retention(RUNTIME)
  @interface ArrayUnwrappedKey {
    int[] value();
  }
  
  public void testArrayKeys_unwrapValuesTrue() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}
      @ProvidesIntoMap @ArrayUnwrappedKey({1, 2}) String provideFoo() { return "foo"; }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(),
          "Array types are not allowed in a MapKey with unwrapValue=true: "
              + ArrayUnwrappedKey.class.getName(),
          "at " + m.getClass().getName() + ".provideFoo(");
    }    
  }

  @MapKey(unwrapValue = false)
  @Retention(RUNTIME)
  @interface ArrayWrappedKey {
    int[] number();
  }
  
  @SuppressWarnings("unused") @ArrayWrappedKey(number={1, 2}) private static Object arrayWrappedKeyHolder12;
  @SuppressWarnings("unused") @ArrayWrappedKey(number={3, 4}) private static Object arrayWrappedKeyHolder34;
  ArrayWrappedKey arrayWrappedKeyFor(int number) throws Exception {
    Field field;
    switch (number) {
      case 12:
        field = ProvidesIntoTest.class.getDeclaredField("arrayWrappedKeyHolder12");
        break;
      case 34:
        field = ProvidesIntoTest.class.getDeclaredField("arrayWrappedKeyHolder34");
        break;
      default:
        throw new IllegalArgumentException("only 1 or 2 supported");
    }
    return field.getAnnotation(ArrayWrappedKey.class);
  }
  
  public void testArrayKeys_unwrapValuesFalse() throws Exception {
    Module m = new AbstractModule() {
      @Override protected void configure() {}
      @ProvidesIntoMap @ArrayWrappedKey(number = {1, 2}) String provideFoo() { return "foo"; }
      @ProvidesIntoMap @ArrayWrappedKey(number = {3, 4}) String provideBar() { return "bar"; }
    };
    Injector injector = Guice.createInjector(MultibindingsScanner.asModule(), m);
    Map<ArrayWrappedKey, String> map =
        injector.getInstance(new Key<Map<ArrayWrappedKey, String>>() {});
    ArrayWrappedKey key12 = arrayWrappedKeyFor(12);
    ArrayWrappedKey key34 = arrayWrappedKeyFor(34);
    assertEquals("foo", map.get(key12));
    assertEquals("bar", map.get(key34));
    assertEquals(2, map.size());
  }
  
  public void testProvidesIntoSetWithMapKey() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}
      @ProvidesIntoSet @TestEnumKey(TestEnum.A) String provideFoo() { return "foo"; }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(), "Found a MapKey annotation on non map binding at "
          + m.getClass().getName() + ".provideFoo");
    }
  }
  
  public void testProvidesIntoOptionalWithMapKey() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}

      @ProvidesIntoOptional(Type.ACTUAL)
      @TestEnumKey(TestEnum.A)
      String provideFoo() {
        return "foo";
      }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(), "Found a MapKey annotation on non map binding at "
          + m.getClass().getName() + ".provideFoo");
    }
  }
  
  public void testProvidesIntoMapWithoutMapKey() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}
      @ProvidesIntoMap String provideFoo() { return "foo"; }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(), "No MapKey found for map binding at "
          + m.getClass().getName() + ".provideFoo");
    }
  }
  
  @MapKey(unwrapValue = true)
  @Retention(RUNTIME)
  @interface TestEnumKey2 {
    TestEnum value();
  }
  
  public void testMoreThanOneMapKeyAnnotation() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}

      @ProvidesIntoMap
      @TestEnumKey(TestEnum.A)
      @TestEnumKey2(TestEnum.B)
      String provideFoo() {
        return "foo";
      }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(), "Found more than one MapKey annotations on "
          + m.getClass().getName() + ".provideFoo");
    }    
  }
  
  @MapKey(unwrapValue = true)
  @Retention(RUNTIME)
  @interface MissingValueMethod {
  }
  
  public void testMapKeyMissingValueMethod() {
    Module m = new AbstractModule() {
      @Override protected void configure() {}

      @ProvidesIntoMap
      @MissingValueMethod
      String provideFoo() {
        return "foo";
      }
    };
    try {
      Guice.createInjector(MultibindingsScanner.asModule(), m);
      fail();
    } catch (CreationException ce) {
      assertEquals(1, ce.getErrorMessages().size());
      assertContains(ce.getMessage(), "No 'value' method in MapKey with unwrapValue=true: "
          + MissingValueMethod.class.getName());
    }    
  }
}
