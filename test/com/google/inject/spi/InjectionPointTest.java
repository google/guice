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

import com.google.common.collect.ImmutableSet;
import static com.google.common.collect.Iterables.getOnlyElement;
import com.google.common.collect.Sets;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.TypeResolver;
import com.google.inject.name.Named;
import static com.google.inject.name.Names.named;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
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
    TypeResolver typeResolver = new TypeResolver(getClass());
    Field fooField = getClass().getField("foo");

    InjectionPoint injectionPoint = new InjectionPoint(typeResolver, fooField);
    assertSame(fooField, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".foo", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeResolver, fooField));
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
        getOnlyElement(new InjectionPoint(typeResolver, fooField).getDependencies()));
  }

  public void testMethodInjectionPoint() throws Exception {
    TypeResolver typeResolver = new TypeResolver(getClass());

    Method barMethod = getClass().getMethod("bar", String.class);
    InjectionPoint injectionPoint = new InjectionPoint(typeResolver, barMethod);
    assertSame(barMethod, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(getClass().getName() + ".bar()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeResolver, barMethod));
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
        getOnlyElement(new InjectionPoint(typeResolver, barMethod).getDependencies()));
  }

  public void testConstructorInjectionPoint() throws NoSuchMethodException, IOException,
      ErrorsException {
    TypeResolver typeResolver = new TypeResolver(Constructable.class);

    Constructor<?> constructor = Constructable.class.getConstructor(String.class);
    InjectionPoint injectionPoint = new InjectionPoint(typeResolver, constructor);
    assertSame(constructor, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals(Constructable.class.getName() + ".<init>()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, new InjectionPoint(typeResolver, constructor));
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
        getOnlyElement(new InjectionPoint(typeResolver, constructor).getDependencies()));
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

    Set<InjectionPoint> sink = Sets.newHashSet();
    InjectionPoint.addForInstanceMethodsAndFields(HasInjections.class, sink);
    assertEquals(ImmutableSet.of(
        new InjectionPoint(new TypeResolver(HasInjections.class), instanceMethod),
        new InjectionPoint(new TypeResolver(HasInjections.class), instanceField)),
        sink);
  }

  public void testAddForStaticMethodsAndFields() throws Exception {
    Method staticMethod = HasInjections.class.getMethod("staticMethod", String.class);
    Field staticField = HasInjections.class.getField("staticField");

    Set<InjectionPoint> sink = Sets.newHashSet();
    InjectionPoint.addForStaticMethodsAndFields(HasInjections.class, sink);
    assertEquals(ImmutableSet.of(
        new InjectionPoint(new TypeResolver(HasInjections.class), staticMethod),
        new InjectionPoint(new TypeResolver(HasInjections.class), staticField)),
        sink);
  }

  static class HasInjections {
    @Inject public static void staticMethod(@Named("a") String a) {}
    @Inject @Named("c") public static String staticField;
    @Inject public void instanceMethod(@Named("d") String d) {}
    @Inject @Named("f") public String instanceField;
  }

  public void testAddForParameterizedInjections() {
    Set<InjectionPoint> sink = Sets.newHashSet();
    Type type = new TypeLiteral<ParameterizedInjections<String>>() {}.getType();

    InjectionPoint constructor = InjectionPoint.forConstructorOf(type);
    assertEquals(new Key<Map<String, String>>() {},
        getOnlyElement(constructor.getDependencies()).getKey());

    InjectionPoint.addForInstanceMethodsAndFields(type, sink);
    InjectionPoint field = getOnlyElement(sink);
    assertEquals(new Key<Set<String>>() {}, getOnlyElement(field.getDependencies()).getKey());
  }

  static class ParameterizedInjections<T> {
    @Inject Set<T> setOfTees;
    @Inject public ParameterizedInjections(Map<T, T> map) {}
  }
}
