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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertNotSerializable;
import static com.google.inject.Asserts.awaitClear;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.util.Types;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
public class KeyTest extends TestCase {

  public void foo(List<String> a, List<String> b) {}

  public void bar(Provider<List<String>> a) {}

  @Foo String baz;
  List<? extends CharSequence> wildcardExtends;

  public void testOfType() {
    Key<Object> k = Key.get(Object.class, Foo.class);
    Key<Integer> ki = k.ofType(Integer.class);
    assertEquals(Integer.class, ki.getRawType());
    assertEquals(Foo.class, ki.getAnnotationType());
  }

  public void testWithAnnotation() {
    Key<Object> k = Key.get(Object.class);
    Key<Object> kf = k.withAnnotation(Foo.class);
    assertNull(k.getAnnotationType());
    assertEquals(Foo.class, kf.getAnnotationType());
  }

  public void testWithAnnotationInstance() throws NoSuchFieldException {
    Foo annotation = getClass().getDeclaredField("baz").getAnnotation(Foo.class);
    Key<Object> k = Key.get(Object.class);
    Key<Object> kf = k.withAnnotation(annotation);
    assertNull(k.getAnnotationType());
    assertNull(k.getAnnotation());
    assertEquals(Foo.class, kf.getAnnotationType());
    assertEquals(annotation, kf.getAnnotation());
  }

  public void testKeyEquality() {
    Key<List<String>> a = new Key<List<String>>(Foo.class) {};
    Key<List<String>> b = Key.get(new TypeLiteral<List<String>>() {}, Foo.class);
    assertEqualsBothWays(a, b);
  }

  public void testProviderKey() throws NoSuchMethodException {
    Key<?> actual =
        Key.get(getClass().getMethod("foo", List.class, List.class).getGenericParameterTypes()[0])
            .providerKey();
    Key<?> expected =
        Key.get(getClass().getMethod("bar", Provider.class).getGenericParameterTypes()[0]);
    assertEqualsBothWays(expected, actual);
    assertEquals(expected.toString(), actual.toString());
  }

  public void testTypeEquality() throws Exception {
    Method m = getClass().getMethod("foo", List.class, List.class);
    Type[] types = m.getGenericParameterTypes();
    assertEquals(types[0], types[1]);
    Key<List<String>> k = new Key<List<String>>() {};
    assertEquals(types[0], k.getTypeLiteral().getType());
    assertFalse(types[0].equals(new Key<List<Integer>>() {}.getTypeLiteral().getType()));
  }

  /**
   * Key canonicalizes {@link int.class} to {@code Integer.class}, and won't expose wrapper types.
   */
  @SuppressWarnings("rawtypes") // Unavoidable because class literal uses raw type
  public void testPrimitivesAndWrappersAreEqual() {
    Class[] primitives =
        new Class[] {
          boolean.class,
          byte.class,
          short.class,
          int.class,
          long.class,
          float.class,
          double.class,
          char.class,
          void.class
        };
    Class[] wrappers =
        new Class[] {
          Boolean.class,
          Byte.class,
          Short.class,
          Integer.class,
          Long.class,
          Float.class,
          Double.class,
          Character.class,
          Void.class
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
    Key<Integer> integerKey2 = Key.get(Integer.class, Named.class);
    Key<Integer> integerKey3 = Key.get(Integer.class, Names.named("int"));

    Class<Integer> intClassLiteral = int.class;
    assertEquals(integerKey, Key.get(intClassLiteral));
    assertEquals(integerKey2, Key.get(intClassLiteral, Named.class));
    assertEquals(integerKey3, Key.get(intClassLiteral, Names.named("int")));

    Type intType = int.class;
    assertEquals(integerKey, Key.get(intType));
    assertEquals(integerKey2, Key.get(intType, Named.class));
    assertEquals(integerKey3, Key.get(intType, Names.named("int")));

    TypeLiteral<Integer> intTypeLiteral = TypeLiteral.get(int.class);
    assertEquals(integerKey, Key.get(intTypeLiteral));
    assertEquals(integerKey2, Key.get(intTypeLiteral, Named.class));
    assertEquals(integerKey3, Key.get(intTypeLiteral, Names.named("int")));
  }

  public void testSerialization() throws IOException, NoSuchFieldException {
    assertNotSerializable(Key.get(B.class));
    assertNotSerializable(Key.get(B.class, Names.named("bee")));
    assertNotSerializable(Key.get(B.class, Named.class));
    assertNotSerializable(Key.get(B[].class));
    assertNotSerializable(Key.get(new TypeLiteral<Map<List<B>, B>>() {}));
    assertNotSerializable(Key.get(new TypeLiteral<List<B[]>>() {}));
    assertNotSerializable(Key.get(Types.listOf(Types.subtypeOf(CharSequence.class))));
  }

  public void testEqualityOfAnnotationTypesAndInstances() throws NoSuchFieldException {
    Foo instance = getClass().getDeclaredField("baz").getAnnotation(Foo.class);
    Key<String> keyWithInstance = Key.get(String.class, instance);
    Key<String> keyWithLiteral = Key.get(String.class, Foo.class);
    assertEqualsBothWays(keyWithInstance, keyWithLiteral);
  }

  public void testNonBindingAnnotationOnKey() {
    try {
      Key.get(String.class, Deprecated.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(
          expected.getMessage(),
          "java.lang.Deprecated is not a binding annotation. ",
          "Please annotate it with @BindingAnnotation.");
    }
  }

  public void testBindingAnnotationWithoutRuntimeRetention() {
    try {
      Key.get(String.class, Bar.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(
          expected.getMessage(),
          Bar.class.getName() + " is not retained at runtime.",
          "Please annotate it with @Retention(RUNTIME).");
    }
  }

  <T> void parameterizedWithVariable(List<T> typeWithVariables) {}

  /** Test for issue 186 */
  public void testCannotCreateKeysWithTypeVariables() throws NoSuchMethodException {
    ParameterizedType listOfTType =
        (ParameterizedType)
            getClass()
                    .getDeclaredMethod("parameterizedWithVariable", List.class)
                    .getGenericParameterTypes()[
                0];

    TypeLiteral<?> listOfT = TypeLiteral.get(listOfTType);
    try {
      Key.get(listOfT);
      fail("Guice should not allow keys for java.util.List<T>");
    } catch (ConfigurationException e) {
      assertContains(
          e.getMessage(), "java.util.List<T> cannot be used as a key; It is not fully specified.");
    }

    TypeVariable<?> tType = (TypeVariable) listOfTType.getActualTypeArguments()[0];
    TypeLiteral<?> t = TypeLiteral.get(tType);
    try {
      Key.get(t);
      fail("Guice should not allow keys for T");
    } catch (ConfigurationException e) {
      assertContains(e.getMessage(), "T cannot be used as a key; It is not fully specified.");
    }
  }

  public void testCannotGetKeyWithUnspecifiedTypeVariables() {
    TypeLiteral<Integer> typeLiteral = KeyTest.createTypeLiteral();
    try {
      Key.get(typeLiteral);
      fail("Guice should not allow keys for T");
    } catch (ConfigurationException e) {
      assertContains(e.getMessage(), "T cannot be used as a key; It is not fully specified.");
    }
  }

  private static <T> TypeLiteral<T> createTypeLiteral() {
    return new TypeLiteral<T>() {};
  }

  public void testCannotCreateKeySubclassesWithUnspecifiedTypeVariables() {
    try {
      KeyTest.<Integer>createKey();
      fail("Guice should not allow keys for T");
    } catch (ConfigurationException e) {
      assertContains(e.getMessage(), "T cannot be used as a key; It is not fully specified.");
    }
  }

  private static <T> Key<T> createKey() {
    return new Key<T>() {};
  }

  interface B {}

  @Retention(RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @BindingAnnotation
  @interface Foo {}

  @SuppressWarnings("InjectScopeOrQualifierAnnotationRetention") // to check failure mode
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @BindingAnnotation
  @interface Bar {}

  static class HasTypeParameters<A, B extends List<A> & Runnable, C extends Runnable> {
    A a;
    B b;
    C c;
  }

  public void testKeysWithDefaultAnnotations() {
    AllDefaults allDefaults = HasAnnotations.class.getAnnotation(AllDefaults.class);
    assertEquals(Key.get(Foo.class, allDefaults), Key.get(Foo.class, AllDefaults.class));

    Marker marker = HasAnnotations.class.getAnnotation(Marker.class);
    assertEquals(Key.get(Foo.class, marker), Key.get(Foo.class, Marker.class));

    Key<?> noDefaults = Key.get(Foo.class, NoDefaults.class);
    assertNull(noDefaults.getAnnotation());
    assertEquals(NoDefaults.class, noDefaults.getAnnotationType());

    Key<?> someDefaults = Key.get(Foo.class, SomeDefaults.class);
    assertNull(someDefaults.getAnnotation());
    assertEquals(SomeDefaults.class, someDefaults.getAnnotationType());
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface AllDefaults {
    int v1() default 1;

    String v2() default "foo";
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface SomeDefaults {
    int v1() default 1;

    String v2() default "foo";

    Class<?> clazz();
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface NoDefaults {
    int value();
  }

  @Retention(RUNTIME)
  @BindingAnnotation
  @interface Marker {}

  @AllDefaults
  @Marker
  static class HasAnnotations {}

  public void testAnonymousClassesDontHoldRefs() {
    final AtomicReference<Provider<List<String>>> stringProvider =
        new AtomicReference<Provider<List<String>>>();
    final AtomicReference<Provider<List<Integer>>> intProvider =
        new AtomicReference<Provider<List<Integer>>>();
    final Object foo =
        new Object() {
          @SuppressWarnings("unused")
          @Inject
          List<String> list;
        };
    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(new Key<List<String>>() {}).toInstance(new ArrayList<String>());
            bind(new TypeLiteral<List<Integer>>() {}).toInstance(new ArrayList<Integer>());

            stringProvider.set(getProvider(new Key<List<String>>() {}));
            intProvider.set(binder().getProvider(Dependency.get(new Key<List<Integer>>() {})));

            binder().requestInjection(new TypeLiteral<Object>() {}, foo);
          }
        };
    WeakReference<Module> moduleRef = new WeakReference<>(module);
    final Injector injector = Guice.createInjector(module);
    module = null;
    awaitClear(moduleRef); // Make sure anonymous keys & typeliterals don't hold the module.

    Runnable runner =
        () -> {
          injector.getInstance(new Key<Typed<String>>() {});
          injector.getInstance(Key.get(new TypeLiteral<Typed<Integer>>() {}));
        };
    WeakReference<Runnable> runnerRef = new WeakReference<>(runner);
    runner.run();
    runner = null;
    awaitClear(runnerRef); // also make sure anonymous keys & typeliterals don't hold for JITs
  }

  static class Typed<T> {}
}
