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

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertNotSerializable;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableSet;
import static com.google.inject.internal.util.Iterables.getOnlyElement;

import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import junit.framework.Assert;
import junit.framework.TestCase;
import com.google.inject.spi.InjectionPoint.Signature;

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

    InjectionPoint injectionPoint = new InjectionPoint(typeLiteral, fooField, false);
    assertSame(fooField, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".foo", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeLiteral, fooField, false));
    assertNotSerializable(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=a)]@"
        + getClass().getName() + ".foo", dependency.toString());
    assertEquals(fooField, dependency.getInjectionPoint().getMember());
    assertEquals(-1, dependency.getParameterIndex());
    Assert.assertEquals(Key.get(String.class, named("a")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertNotSerializable(dependency);
    assertEqualsBothWays(dependency,
        getOnlyElement(new InjectionPoint(typeLiteral, fooField, false).getDependencies()));
  }

  public void testMethodInjectionPoint() throws Exception {
    TypeLiteral<?> typeLiteral = TypeLiteral.get(getClass());

    Method barMethod = getClass().getMethod("bar", String.class);
    InjectionPoint injectionPoint = new InjectionPoint(typeLiteral, barMethod, false);
    assertSame(barMethod, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".bar()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeLiteral, barMethod, false));
    assertNotSerializable(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=b)]@"
        + getClass().getName() + ".bar()[0]", dependency.toString());
    assertEquals(barMethod, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, named("b")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertNotSerializable(dependency);
    assertEqualsBothWays(dependency,
        getOnlyElement(new InjectionPoint(typeLiteral, barMethod, false).getDependencies()));
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
    assertNotSerializable(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=c)]@"
        + Constructable.class.getName() + ".<init>()[0]", dependency.toString());
    assertEquals(constructor, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, named("c")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertNotSerializable(dependency);
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
    assertNotSerializable(dependency);
    assertEqualsBothWays(dependency, Dependency.get(Key.get(String.class, named("d"))));
  }

  public void testForConstructor() throws NoSuchMethodException {
    Constructor<HashSet> constructor = HashSet.class.getConstructor();
    TypeLiteral<HashSet<String>> hashSet = new TypeLiteral<HashSet<String>>() {};

    InjectionPoint injectionPoint = InjectionPoint.forConstructor(constructor, hashSet);
    assertSame(constructor, injectionPoint.getMember());
    assertEquals(ImmutableList.<Dependency>of(), injectionPoint.getDependencies());
    assertFalse(injectionPoint.isOptional());

    try {
      InjectionPoint.forConstructor(constructor, new TypeLiteral<LinkedHashSet<String>>() {});
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "java.util.LinkedHashSet<java.lang.String>",
          " does not define java.util.HashSet.<init>()",
          "  while locating java.util.LinkedHashSet<java.lang.String>");
    }

    try {
      InjectionPoint.forConstructor((Constructor) constructor, new TypeLiteral<Set<String>>() {});
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "java.util.Set<java.lang.String>",
          " does not define java.util.HashSet.<init>()",
          "  while locating java.util.Set<java.lang.String>");
    }
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
        new InjectionPoint(type, instanceMethod, false),
        new InjectionPoint(type, instanceField, false)),
        InjectionPoint.forInstanceMethodsAndFields(HasInjections.class));
  }

  public void testAddForStaticMethodsAndFields() throws Exception {
    Method staticMethod = HasInjections.class.getMethod("staticMethod", String.class);
    Field staticField = HasInjections.class.getField("staticField");

    Set<InjectionPoint> injectionPoints = InjectionPoint.forStaticMethodsAndFields(
        HasInjections.class);
    assertEquals(ImmutableSet.of(
        new InjectionPoint(TypeLiteral.get(HasInjections.class), staticMethod, false),
        new InjectionPoint(TypeLiteral.get(HasInjections.class), staticField, false)),
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

  public void testSignature() throws Exception {
    Signature fooA = new Signature(Foo.class.getDeclaredMethod(
        "a", String.class, int.class));
    Signature fooB = new Signature(Foo.class.getDeclaredMethod("b"));
    Signature barA = new Signature(Bar.class.getDeclaredMethod(
        "a", String.class, int.class));
    Signature barB = new Signature(Bar.class.getDeclaredMethod("b"));

    assertEquals(fooA.hashCode(), barA.hashCode());
    assertEquals(fooB.hashCode(), barB.hashCode());
    assertEquals(fooA, barA);
    assertEquals(fooB, barB);
  }

  static class Foo {
    void a(String s, int i) {}
    int b() {
      return 0;
    }
  }
  static class Bar {
    public void a(String s, int i) {}
    void b() {}
  }
  
  public void testOverrideBehavior() {
	  Set<InjectionPoint> points;
	  
	  points = InjectionPoint.forInstanceMethodsAndFields(Super.class);
	  assertEquals(points.toString(), 6, points.size());
	  assertPoints(points, Super.class, "atInject", "gInject", "privateAtAndPublicG",
	      "privateGAndPublicAt", "atFirstThenG", "gFirstThenAt");
	  
	  points = InjectionPoint.forInstanceMethodsAndFields(Sub.class);
    assertEquals(points.toString(), 7, points.size());
    // Superclass will always have is private members injected,
    // and 'gInject' was last @Injected in Super, so that remains the owner
    assertPoints(points, Super.class, "privateAtAndPublicG", "privateGAndPublicAt", "gInject");
    // Subclass also has the "private" methods, but they do not override
    // the superclass' methods, and it now owns the inject2 methods.
    assertPoints(points, Sub.class, "privateAtAndPublicG", "privateGAndPublicAt",
        "atFirstThenG", "gFirstThenAt");
    
    points = InjectionPoint.forInstanceMethodsAndFields(SubSub.class);
    assertEquals(points.toString(), 6, points.size());
    // Superclass still has all the injection points it did before..
    assertPoints(points, Super.class, "privateAtAndPublicG", "privateGAndPublicAt", "gInject");
    // Subclass is missing the privateGAndPublicAt because it first became public with
    // javax.inject.Inject and was overrode without an annotation, which means it
    // disappears.  (It was guice @Inject in Super, but it was private there, so it doesn't
    // effect the annotations of the subclasses.)
    assertPoints(points, Sub.class, "privateAtAndPublicG", "atFirstThenG", "gFirstThenAt");    
  }
  
  private void assertPoints(Iterable<InjectionPoint> points, Class clazz, String... methodNames) {
    Set<String> methods = new HashSet<String>();
    for(InjectionPoint point : points) {
      if(point.getDeclaringType().getRawType() == clazz) {
        methods.add(point.getMember().getName());
      }
    }
    assertEquals(points.toString(), ImmutableSet.of(methodNames), methods);
  }
  
  static class Super {
	  @javax.inject.Inject public void atInject() {}
	  @com.google.inject.Inject public void gInject() {}
	  
	  @javax.inject.Inject private void privateAtAndPublicG() {}
	  @com.google.inject.Inject private void privateGAndPublicAt() {}
	  
	  @javax.inject.Inject public void atFirstThenG() {}
    @com.google.inject.Inject public void gFirstThenAt() {}
  }
  
  static class Sub extends Super {
    public void atInject() {}
    public void gInject() {}
    
    @com.google.inject.Inject public void privateAtAndPublicG() {}
    @javax.inject.Inject public void privateGAndPublicAt() {}
    
    @com.google.inject.Inject public void atFirstThenG() {}
    @javax.inject.Inject public void gFirstThenAt() {}
  }
  
  static class SubSub extends Sub {
    public void privateAtAndPublicG() {}
    public void privateGAndPublicAt() {}
    
    public void atFirstThenG() {}
    public void gFirstThenAt() {}
  }
}
