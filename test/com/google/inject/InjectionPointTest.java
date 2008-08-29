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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.Asserts.assertEqualsBothWays;
import static com.google.inject.Asserts.assertSimilarWhenReserialized;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
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

  public void testFieldInjectionPoint() throws NoSuchFieldException, IOException, ErrorsException {
    Field fooField = getClass().getField("foo");

    InjectionPoint injectionPoint = InjectionPoint.get(fooField);
    assertSame(fooField, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals("com.google.inject.InjectionPointTest.foo", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, InjectionPoint.get(fooField));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=a)]"
        + "@com.google.inject.InjectionPointTest.foo", dependency.toString());
    assertEquals(fooField, dependency.getInjectionPoint().getMember());
    assertEquals(-1, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("a")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
  }

  public void testMethodInjectionPoint() throws NoSuchMethodException, IOException, ErrorsException {
    Method barMethod = getClass().getMethod("bar", String.class);
    InjectionPoint injectionPoint = InjectionPoint.get(barMethod);
    assertSame(barMethod, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals("com.google.inject.InjectionPointTest.bar()", injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, InjectionPoint.get(barMethod));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=b)]"
        + "@com.google.inject.InjectionPointTest.bar()[0]", dependency.toString());
    assertEquals(barMethod, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("b")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
  }

  public void testConstructorInjectionPoint() throws NoSuchMethodException, IOException,
      ErrorsException {
    Constructor<?> constructor = Constructable.class.getConstructor(String.class);
    InjectionPoint injectionPoint = InjectionPoint.get(constructor, new Errors());
    assertSame(constructor, injectionPoint.getMember());
    assertFalse(injectionPoint.isOptional());
    assertEquals("com.google.inject.InjectionPointTest$Constructable.<init>()",
        injectionPoint.toString());
    assertEqualsBothWays(injectionPoint, InjectionPoint.get(constructor, new Errors()));
    assertSimilarWhenReserialized(injectionPoint);

    Dependency<?> dependency = getOnlyElement(injectionPoint.getDependencies());
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=c)]"
        + "@com.google.inject.InjectionPointTest$Constructable.<init>()[0]", dependency.toString());
    assertEquals(constructor, dependency.getInjectionPoint().getMember());
    assertEquals(0, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("c")), dependency.getKey());
    assertEquals(false, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
  }
  
  public void testUnattachedDependency() throws IOException {
    Dependency<String> dependency = Dependency.get(Key.get(String.class, Names.named("d")));
    assertEquals("Key[type=java.lang.String, annotation=@com.google.inject.name.Named(value=d)]",
        dependency.toString());
    assertNull(dependency.getInjectionPoint());
    assertEquals(-1, dependency.getParameterIndex());
    assertEquals(Key.get(String.class, Names.named("d")), dependency.getKey());
    assertEquals(true, dependency.isNullable());
    assertSimilarWhenReserialized(dependency);
  }
}
