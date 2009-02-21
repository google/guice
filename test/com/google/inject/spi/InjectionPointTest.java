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

package com.google.inject.spi;

import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ImmutableSet;
import static com.google.inject.internal.Iterables.getOnlyElement;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import junit.framework.Assert;
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

  public void testFieldInjectionPoint() throws NoSuchFieldException, IOException, ErrorsException {
    TypeLiteral<?> typeLiteral = TypeLiteral.get(getClass());
    Field fooField = getClass().getField("foo");

    InjectionPoint injectionPoint = new InjectionPoint(typeLiteral, fooField);
    assertSame(fooField, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".foo", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeLiteral, fooField));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=a)]@"
        + getClass().getName() + ".foo", dependency.toString());
    assertEquals(fooField, dependency.getInjectionPoint().getMember());
    assertEquals(-1, dependency.getParameterIndex());
    Assert.assertEquals(Key.get(String.class, named("a")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
    assertEqualsBothWays(dependency,
        getOnlyElement(new InjectionPoint(typeLiteral, fooField).getDependencies()));
  }

  public void testMethodInjectionPoint() throws Exception {
    TypeLiteral<?> typeLiteral = TypeLiteral.get(getClass());

    Method barMethod = getClass().getMethod("bar", String.class);
    InjectionPoint injectionPoint = new InjectionPoint(typeLiteral, barMethod);
    assertSame(barMethod, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".bar()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeLiteral, barMethod));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=b)]@"
        + getClass().getName() + ".bar()[0]", dependency.toString());
    assertEquals(barMethod, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, named("b")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
    assertEqualsBothWays(dependency,
        getOnlyElement(new InjectionPoint(typeLiteral, barMethod).getDependencies()));
  }

  public void testConstructorInjectionPoint() throws NoSuchMethodException, IOException,
      ErrorsException {
    TypeLiteral<?> typeLiteral = TypeLiteral.get(Constructable.class);

    Constructor<?> constructor = Constructable.class.getConstructor(String.class);
    InjectionPoint injectionPoint = new InjectionPoint(typeLiteral, constructor);
    assertSame(constructor, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(Constructable.class.getName() + ".<init>()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeLiteral, constructor));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=c)]@"
        + Constructable.class.getName() + ".<init>()[0]", dependency.toString());
    assertEquals(constructor, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, named("c")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
    assertEqualsBothWays(dependency,
        getOnlyElement(new InjectionPoint(typeLiteral, constructor).getDependencies()));
  }
  
  public void testUnattachedDependency() throws IOException {
    Dependency<String> dependency = Dependency.get(Key.get(String.class, named("d")));
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=d)]",
        dependency.toString());
    assertNull(dependency.getInjectionPoint());
    assertEquals(-1, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, named("d")), dependency.getKey());
    assertEquals(true, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
    assertEqualsBothWays(dependency, Dependency.get(Key.get(String.class, named("d"))));
  }
  
  public void testForConstructorOf() {
    InjectionPoint injectionPoint = InjectionPoint.forConstructorOf(Constructable.class);
    assertEquals(Constructable.class.getName() + ".<init>()", injectionPoint.toString());
  }

  public void testAddForInstanceMethodsAndFields() throws Exception {
    Method instanceMethod = HasInjections.class.getMethod("instanceMethod", String.class);
    Field instanceField = HasInjections.class.getField("instanceField");

    TypeLiteral<HasInjections> type = TypeLiteral.get(HasInjections.class);
    assertEquals(ImmutableSet.of(
        new InjectionPoint(type, instanceMethod),
        new InjectionPoint(type, instanceField)),
        InjectionPoint.forInstanceMethodsAndFields(HasInjections.class));
  }

  public void testAddForStaticMethodsAndFields() throws Exception {
    Method staticMethod = HasInjections.class.getMethod("staticMethod", String.class);
    Field staticField = HasInjections.class.getField("staticField");

    Set<InjectionPoint> injectionPoints = InjectionPoint.forStaticMethodsAndFields(
        HasInjections.class);
    assertEquals(ImmutableSet.of(
        new InjectionPoint(TypeLiteral.get(HasInjections.class), staticMethod),
        new InjectionPoint(TypeLiteral.get(HasInjections.class), staticField)),
        injectionPoints);
  }

  static class HasInjections {
    @Inject public static void staticMethod(@Named("a") String a) {}
    @Inject @Named("c") public static String staticField;
    @Inject public void instanceMethod(@Named("d") String d) {}
    @Inject @Named("f") public String instanceField;
  }

  public void testAddForParameterizedInjections() {
    TypeLiteral<?> type = new TypeLiteral<ParameterizedInjections<String>>() {};

    InjectionPoint constructor = InjectionPoint.forConstructorOf(type);
    assertEquals(new Key<Map<String, String>>() {},
        getOnlyElement(constructor.getDependencies()).getKey());

    InjectionPoint field = getOnlyElement(InjectionPoint.forInstanceMethodsAndFields(type));
    assertEquals(new Key<Set<String>>() {}, getOnlyElement(field.getDependencies()).getKey());
  }

  static class ParameterizedInjections<T> {
    @Inject Set<T> setOfTees;
    @Inject public ParameterizedInjections(Map<T, T> map) {}
  }
}
