/*
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

package com.google.inject.grapher;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ShortNameFactory}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class ShortNameFactoryTest {
  // Helper objects are up here because their line numbers are tested below.
  private static class Obj {
    @Annotated public String field;

    Obj() {}

    void method(String parameter) {}
  }

  private static class ToStringObj {
    @Override
    public String toString() {
      return "I'm a ToStringObj";
    }
  }

  @Retention(RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @BindingAnnotation
  private @interface Annotated {}

  private NameFactory nameFactory;

  @BeforeEach
  protected void setUp() throws Exception {
    nameFactory = new ShortNameFactory();
  }

  @Test
  public void testGetMemberName_field() throws Exception {
    Member field = Obj.class.getDeclaredField("field");
    assertEquals("field", nameFactory.getMemberName(field));
  }

  @Test
  public void testGetMemberName_method() throws Exception {
    Member method = Obj.class.getDeclaredMethod("method", String.class);
    assertEquals("#method(...)", nameFactory.getMemberName(method));
  }

  @Test
  public void testGetMemberName_constructor() throws Exception {
    Member constructor = Obj.class.getDeclaredConstructor();
    assertEquals("<init>", nameFactory.getMemberName(constructor));
  }

  @Test
  public void testGetAnnotationName_annotationType() throws Exception {
    Key<?> key = Key.get(String.class, Annotated.class);
    assertEquals("@Annotated", nameFactory.getAnnotationName(key));
  }

  @Test
  public void testGetAnnotationName_annotationInstance() throws Exception {
    Key<?> key =
        Key.get(String.class, Obj.class.getDeclaredField("field").getDeclaredAnnotations()[0]);
    assertEquals("@Annotated", nameFactory.getAnnotationName(key));
  }

  @Test
  public void testGetAnnotationName_annotationInstanceWithParameters() throws Exception {
    Key<?> key = Key.get(String.class, Names.named("name"));
    assertEquals(
        "@Named(" + Annotations.memberValueString("value", "name") + ")",
        nameFactory.getAnnotationName(key));
  }

  @Test
  public void testGetClassName_key() throws Exception {
    Key<?> key = Key.get(Obj.class);
    assertEquals(
        "ShortNameFactoryTest$Obj",
        nameFactory.getClassName(key),
        "Class name should not have the package");
  }

  @Test
  public void testGetClassName_keyWithTypeParameters() throws Exception {
    Key<?> key = Key.get(new TypeLiteral<Provider<String>>() {});
    assertEquals(
        "Provider<String>",
        nameFactory.getClassName(key),
        "Class name and type values should not have packages");
  }

  /**
   * Tests the case where a provider method is the source of the
   *
   * @throws Exception
   */
  @Test
  public void testGetSourceName_method() throws Exception {
    Member method = Obj.class.getDeclaredMethod("method", String.class);
    assertEquals(
        "ShortNameFactoryTest.java:57",
        nameFactory.getSourceName(method),
        "Method should be identified by its file name and line number");
  }

  @Test
  public void testGetSourceName_stackTraceElement() throws Exception {
    StackTraceElement element =
        (StackTraceElement) StackTraceElements.forMember(Obj.class.getField("field"));
    assertEquals(
        "ShortNameFactoryTest.java:55",
        nameFactory.getSourceName(element),
        "Stack trace element should be identified by its file name and line number");
  }

  @Test
  public void testGetInstanceName_defaultToString() throws Exception {
    assertEquals(
        "ShortNameFactoryTest$Obj",
        nameFactory.getInstanceName(new Obj()),
        "Should use class name instead of Object#toString()");
  }

  @Test
  public void testGetInstanceName_customToString() throws Exception {
    assertEquals(
        "I'm a ToStringObj",
        nameFactory.getInstanceName(new ToStringObj()),
        "Should use class's toString() method since it's defined");
  }

  @Test
  public void testGetInstanceName_string() throws Exception {
    assertEquals(
        "\"My String Instance\"",
        nameFactory.getInstanceName("My String Instance"),
        "String should have quotes to evoke a string literal");
  }

  @Test
  public void testGetInstanceName_providerMethod() throws Exception {
    final List<ProviderMethod<?>> methodHolder = new ArrayList<>(1);

    Injector injector = Guice.createInjector(new ProvidingModule());
    injector
        .getBinding(Integer.class)
        .acceptTargetVisitor(
            new DefaultBindingTargetVisitor<Object, Void>() {
              @Override
              public Void visit(ProviderInstanceBinding<?> binding) {
                methodHolder.add((ProviderMethod) binding.getUserSuppliedProvider());
                return null;
              }
            });

    assertEquals(
        "#provideInteger(String)",
        nameFactory.getInstanceName(methodHolder.get(0)),
        "Method provider should pretty print as the method signature");
  }

  private static class ProvidingModule extends AbstractModule {

    @Provides
    public Integer provideInteger(String string) {
      return Integer.valueOf(string);
    }
  }
}
