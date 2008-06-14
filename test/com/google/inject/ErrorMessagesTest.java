// Copyright 2007 Google Inc. All Rights Reserved.

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import com.google.inject.util.Providers;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;
import junit.framework.TestCase;

/**
 * Tests the error messages produced by Guice.
 *
 * @author Kevin Bourrillion
 */
public class ErrorMessagesTest extends TestCase {

  private ParameterizedType parameterizedWithVariable;
  private ParameterizedType parameterizedWithWildcard;
  private TypeVariable typeVariable;
  private WildcardType wildcardType;

  <T> void parameterizedWithVariable(List<T> typeWithVariables) {}
  <T> void parameterizedWithWildcard(List<? extends Comparable> typeWithWildcard) {}

  @Override protected void setUp() throws Exception {
    parameterizedWithVariable = (ParameterizedType) getClass()
        .getDeclaredMethod("parameterizedWithVariable", List.class).getGenericParameterTypes()[0];
    parameterizedWithWildcard = (ParameterizedType) getClass()
        .getDeclaredMethod("parameterizedWithWildcard", List.class).getGenericParameterTypes()[0];
    typeVariable = (TypeVariable) parameterizedWithVariable.getActualTypeArguments()[0];
    wildcardType = (WildcardType) parameterizedWithWildcard.getActualTypeArguments()[0];
  }

  private class InnerClass {}

  public void testInjectInnerClass() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(InnerClass.class);
      fail();
    } catch (Exception expected) {
      // TODO(kevinb): why does the source come out as unknown??
      assertContains(expected.getMessage(), "Injecting into inner classes is not supported.");
    }
  }

  public void testInjectLocalClass() throws Exception {
    class LocalClass {}

    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(LocalClass.class);
      fail();
    } catch (Exception expected) {
      // TODO(kevinb): why does the source come out as unknown??
      assertContains(expected.getMessage(), "Injecting into inner classes is not supported.");
    }
  }

  public void testExplicitBindingOfAnAbstractClass() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(AbstractClass.class);
        }
      });
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "Injecting into abstract types is not supported.");
    }
  }

  public void testGetInstanceOfAnAbstractClass() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(AbstractClass.class);
      fail();
    } catch(CreationException expected) {
      assertContains(expected.getMessage(), "Injecting into abstract types is not supported.");
    }
  }

  static abstract class AbstractClass {
    @Inject AbstractClass() { }
  }

  public void testBindDisallowedTypes() throws NoSuchMethodException {
    Type[] types = new Type[] {
        parameterizedWithVariable,
        parameterizedWithWildcard,
        typeVariable,
        wildcardType,
    };

    for (Type type : types) {
      @SuppressWarnings("unchecked") final
      Key<Object> key = (Key<Object>) Key.get(type);

      try {
        Guice.createInjector(new AbstractModule() {
          protected void configure() {
            bind(key).toProvider(Providers.of(null));
          }
        });
        fail("Guice should not allow bindings to " + type);
      } catch (CreationException e) {
        assertContains(e.getMessage(), "Cannot bind types that have type variables");
      }
    }
  }

  public void testScopingAnnotationsOnAbstractTypes() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(A.class).to(AImpl.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Scope annotations on abstract types are not supported.");
    }
  }

  /** Demonstrates issue 64, when setAccessible() fails. */
  public void testGetUninjectableClass() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(Class.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Failed to inject java.lang.Class");
      assertTrue(expected.getCause() instanceof SecurityException);
    }
  }

  @Singleton
  interface A {}
  class AImpl implements A {}

  public void testBindingAnnotationsOnMethodsAndConstructors() {
    try {
      Guice.createInjector().getInstance(B.class);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Binding annotations on injected methods are not supported. "
              + "Annotate the parameter instead?");
    }

    try {
      Guice.createInjector().getInstance(C.class);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "Binding annotations on injected constructors are not supported. "
              + "Annotate the parameter instead?");
    }
  }

  static class B {
    @Inject @Green void injectMe(String greenString) {}
  }

  static class C {
    @Inject @Green C(String greenString) {}
  }

  @Retention(RUNTIME)
  @Target({ FIELD, PARAMETER, CONSTRUCTOR, METHOD })
  @BindingAnnotation
  @interface Green {}


  // TODO(kevinb): many many more

}
