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

package com.google.inject.grapher;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.AbstractModule;
import com.google.inject.BindingAnnotation;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.ProviderInstanceBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Member;

import junit.framework.TestCase;

/**
 * Tests for {@link ShortNameFactory}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class ShortNameFactoryTest extends TestCase {
  // Helper objects are up here because their line numbers are tested below.
  private static class Obj {
    @Annotated
    public String field;
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
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  private @interface Annotated {}

  private NameFactory nameFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    nameFactory = new ShortNameFactory();
  }

  public void testGetMemberName_field() throws Exception {
    Member field = Obj.class.getDeclaredField("field");
    assertEquals("field", nameFactory.getMemberName(field));
  }

  public void testGetMemberName_method() throws Exception {
    Member method = Obj.class.getDeclaredMethod("method", String.class);
    assertEquals("#method(...)", nameFactory.getMemberName(method));
  }

  public void testGetMemberName_constructor() throws Exception {
    Member constructor = Obj.class.getDeclaredConstructor();
    assertEquals("<init>", nameFactory.getMemberName(constructor));
  }

  public void testGetAnnotationName_annotationType() throws Exception {
    Key<?> key = Key.get(String.class, Annotated.class);
    assertEquals("@Annotated", nameFactory.getAnnotationName(key));
  }

  public void testGetAnnotationName_annotationInstance() throws Exception {
    Key<?> key = Key.get(String.class,
        Obj.class.getDeclaredField("field").getDeclaredAnnotations()[0]);
    assertEquals("@Annotated", nameFactory.getAnnotationName(key));
  }

  public void testGetAnnotationName_annotationInstanceWithParameters() throws Exception {
    Key<?> key = Key.get(String.class, Names.named("name"));
    assertEquals("@Named(value=name)", nameFactory.getAnnotationName(key));
  }

  public void testGetClassName_key() throws Exception {
    Key<?> key = Key.get(Obj.class);
    assertEquals("Class name should not have the package",
        "ShortNameFactoryTest$Obj", nameFactory.getClassName(key));
  }
  
  public void testGetClassName_keyWithTypeParameters() throws Exception {
    Key<?> key = Key.get(new TypeLiteral<Provider<String>>() {});
    assertEquals("Class name and type values should not have packages",
        "Provider<String>", nameFactory.getClassName(key));
  }

  /**
   * Tests the case where a provider method is the source of the 
   * @throws Exception
   */
  public void testGetSourceName_method() throws Exception {
    Member method = Obj.class.getDeclaredMethod("method", String.class);
    assertEquals("Method should be identified by its file name and line number",
        "ShortNameFactoryTest.java:53", nameFactory.getSourceName(method));
  }

  public void testGetSourceName_stackTraceElement() throws Exception {
    StackTraceElement element = 
        (StackTraceElement) StackTraceElements.forMember(Obj.class.getField("field"));
    assertEquals("Stack trace element should be identified by its file name and line number",
        "ShortNameFactoryTest.java:52", nameFactory.getSourceName(element));
  }

  public void testGetInstanceName_defaultToString() throws Exception {
    assertEquals("Should use class name instead of Object#toString()",
        "ShortNameFactoryTest$Obj", nameFactory.getInstanceName(new Obj()));
  }
  
  public void testGetInstanceName_customToString() throws Exception {
    assertEquals("Should use class's toString() method since it's defined",
        "I'm a ToStringObj", nameFactory.getInstanceName(new ToStringObj()));
  }
  
  public void testGetInstanceName_string() throws Exception {
    assertEquals("String should have quotes to evoke a string literal",
        "\"My String Instance\"", nameFactory.getInstanceName("My String Instance"));
  }
  
  public void testGetInstanceName_providerMethod() throws Exception {
    final ProviderMethod<?>[] methodHolder = new ProviderMethod[1];
    
    Injector injector = Guice.createInjector(new ProvidingModule());
    injector.getBinding(Integer.class).acceptTargetVisitor(
        new DefaultBindingTargetVisitor<Object, Void>() {
          @SuppressWarnings("unchecked") @Override
          public Void visit(ProviderInstanceBinding<?> binding) {
            methodHolder[0] = (ProviderMethod) binding.getProviderInstance();
            return null;
          }
        });
    
    assertEquals("Method provider should pretty print as the method signature",
        "#provideInteger(String)", nameFactory.getInstanceName(methodHolder[0]));
  }
  
  private static class ProvidingModule extends AbstractModule {
    @Override
    protected void configure() {}
    
    @Provides
    public Integer provideInteger(String string) {
      return Integer.valueOf(string);
    }
  }
}
