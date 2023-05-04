/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.name;

import static com.google.inject.Asserts.assertContains;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.testing.EqualsTester;
import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.internal.Annotations;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.util.Properties;
import jakarta.inject.Qualifier;
import junit.framework.TestCase;

/**
 * Tests that {@code jakarta.inject.Named}, {@code jakarta.inject.Named} and {@code
 * com.google.inject.name.Named} are completely interchangeable: bindings for one can be used to
 * inject the other.
 *
 * @author cgdecker@gmail.com (Colin Decker)
 */
public class NamedEquivalanceTest extends TestCase {

  private static final Module GUICE_BINDING_MODULE = moduleWithAnnotation(Names.named("foo"));
  private static final Module JSR330_BINDING_MODULE = moduleWithAnnotation(new JsrNamed("foo"));
  private static final Module JAKARTA_BINDING_MODULE =
      moduleWithAnnotation(new JakartaNamed("foo"));
  private static final Module GUICE_PROVIDER_METHOD_MODULE = getGuiceBindingProviderMethodModule();
  private static final Module JSR330_PROVIDER_METHOD_MODULE =
      getJsr330BindingProviderMethodModule();
  private static final Module JAKARTA_PROVIDER_METHOD_MODULE =
      getJakartaBindingProviderMethodModule();

  @Qualifier
  @Retention(RUNTIME)
  @interface NotNamed {}

  public void testKeysCreatedWithDifferentTypesAreEqual() {
    new EqualsTester()
        .addEqualityGroup(
            keyForAnnotation(new GuiceNamed("foo")),
            keyForAnnotation(new JsrNamed("foo")),
            keyForAnnotation(new JakartaNamed("foo")))
        .addEqualityGroup(
            keyForAnnotation(new GuiceNamed("bar")),
            keyForAnnotation(new JsrNamed("bar")),
            keyForAnnotation(new JakartaNamed("bar")))
        .testEquals();

    new EqualsTester()
        .addEqualityGroup(
            keyForAnnotationType(com.google.inject.name.Named.class),
            keyForAnnotationType(jakarta.inject.Named.class),
            keyForAnnotationType(jakarta.inject.Named.class))
        .addEqualityGroup(keyForAnnotationType(NotNamed.class));
  }

  private static Key<String> keyForAnnotation(Annotation annotation) {
    return Key.get(String.class, annotation);
  }

  private static Key<String> keyForAnnotationType(Class<? extends Annotation> annotationType) {
    return Key.get(String.class, annotationType);
  }

  public void testBindingWithNamesCanInjectAllTypes() {
    assertInjectionsSucceed(GUICE_BINDING_MODULE);
  }

  public void testBindingWithJsr330AnnotationCanInjectAllTypes() {
    assertInjectionsSucceed(JSR330_BINDING_MODULE);
  }

  public void testBindingWithJakartannotationCanInjectAllTypes() {
    assertInjectionsSucceed(JAKARTA_BINDING_MODULE);
  }

  public void testBindingWithGuiceNamedAnnotatedProviderMethodCanInjectAllTypes() {
    assertInjectionsSucceed(GUICE_PROVIDER_METHOD_MODULE);
  }

  public void testBindingWithJsr330NamedAnnotatedProviderMethodCanInjectAllTypes() {
    assertInjectionsSucceed(JSR330_PROVIDER_METHOD_MODULE);
  }

  public void testBindingWithJakartaNamedAnnotatedProviderMethodCanInjectAllTypes() {
    assertInjectionsSucceed(JAKARTA_PROVIDER_METHOD_MODULE);
  }

  public void testBindingDifferentTypesWithSameValueIsIgnored() {
    assertDuplicateBinding(
        false, GUICE_BINDING_MODULE, JSR330_BINDING_MODULE, JAKARTA_BINDING_MODULE);
  }

  public void testBindingDifferentTypesWithSameValueIsAnErrorWithProviderMethods() {
    assertDuplicateBinding(true, GUICE_PROVIDER_METHOD_MODULE, JSR330_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, GUICE_PROVIDER_METHOD_MODULE, JAKARTA_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JSR330_PROVIDER_METHOD_MODULE, GUICE_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JSR330_PROVIDER_METHOD_MODULE, JAKARTA_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JAKARTA_PROVIDER_METHOD_MODULE, JSR330_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JAKARTA_PROVIDER_METHOD_MODULE, GUICE_PROVIDER_METHOD_MODULE);
  }

  public void testBindingDifferentTypesWithSameValueIsAnErrorMixed() {
    assertDuplicateBinding(true, GUICE_BINDING_MODULE, JSR330_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, GUICE_BINDING_MODULE, JAKARTA_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JSR330_BINDING_MODULE, GUICE_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JSR330_BINDING_MODULE, JAKARTA_PROVIDER_METHOD_MODULE);
    assertDuplicateBinding(true, JAKARTA_BINDING_MODULE, GUICE_PROVIDER_METHOD_MODULE);
  }

  public void testMissingBindingForGuiceNamedUsesSameTypeInErrorMessage() {
    assertMissingBindingErrorMessageUsesType(GuiceNamedClient.class);
  }

  public void testMissingBindingForJsr330NamedUsesSameTypeInErrorMessage() {
    assertMissingBindingErrorMessageUsesType(Jsr330NamedClient.class);
  }

  public void testMissingBindingForJakartaamedUsesSameTypeInErrorMessage() {
    assertMissingBindingErrorMessageUsesType(Jsr330NamedClient.class);
  }

  public void testBindPropertiesWorksWithJsr330() {
    assertInjectionsSucceed(
        new AbstractModule() {
          @Override
          protected void configure() {
            Properties properties = new Properties();
            properties.put("foo", "bar");
            Names.bindProperties(binder(), properties);
          }
        });
  }

  private static void assertMissingBindingErrorMessageUsesType(Class<?> clientType) {
    try {
      Guice.createInjector().getInstance(clientType);
      fail("should have thrown ConfigurationException");
    } catch (ConfigurationException e) {
      assertContains(
          e.getMessage(),
          "No implementation for String annotated with "
              + "@Named("
              + Annotations.memberValueString("value", "foo")
              + ") was bound.");
    }
  }

  private static void assertDuplicateBinding(boolean fails, Module... modules) {
    try {
      Guice.createInjector(modules);
      if (fails) {
        fail("should have thrown CreationException");
      }
    } catch (CreationException e) {
      if (fails) {
        assertContains(
            e.getMessage(),
            "String annotated with @Named("
                + Annotations.memberValueString("value", "foo")
                + ") was bound multiple times.");
      } else {
        throw e;
      }
    }
  }

  private static Module moduleWithAnnotation(final Annotation annotation) {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bindConstant().annotatedWith(annotation).to("bar");
      }
    };
  }

  private static void assertInjectionsSucceed(Module module) {
    Injector injector = Guice.createInjector(module);
    assertInjected(
        injector.getInstance(GuiceNamedClient.class),
        injector.getInstance(Jsr330NamedClient.class),
        injector.getInstance(JakartaNamedClient.class));
  }

  private static void assertInjected(
      GuiceNamedClient guiceClient,
      Jsr330NamedClient jsr330Client,
      JakartaNamedClient jakartaClient) {
    assertEquals("bar", guiceClient.foo);
    assertEquals("bar", jsr330Client.foo);
    assertEquals("bar", jakartaClient.foo);
  }

  private static Module getJakartaBindingProviderMethodModule() {
    return new AbstractModule() {

      @SuppressWarnings("unused")
      @Provides
      @jakarta.inject.Named("foo")
      String provideFoo() {
        return "bar";
      }
    };
  }

  private static Module getJsr330BindingProviderMethodModule() {
    return new AbstractModule() {

      @SuppressWarnings("unused")
      @Provides
      @jakarta.inject.Named("foo")
      String provideFoo() {
        return "bar";
      }
    };
  }

  private static Module getGuiceBindingProviderMethodModule() {
    return new AbstractModule() {

      @SuppressWarnings("unused")
      @Provides
      @Named("foo")
      String provideFoo() {
        return "bar";
      }
    };
  }

  private static class GuiceNamedClient {
    @Inject
    @Named("foo")
    String foo;
  }

  private static class Jsr330NamedClient {
    @Inject
    @jakarta.inject.Named("foo")
    String foo;
  }

  private static class JakartaNamedClient {
    @Inject
    @jakarta.inject.Named("foo")
    String foo;
  }

  private static class JakartaNamed implements jakarta.inject.Named, Serializable {
    private final String value;

    public JakartaNamed(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return this.value;
    }

    @Override
    public int hashCode() {
      // This is specified in java.lang.Annotation.
      return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof jakarta.inject.Named)) {
        return false;
      }

      jakarta.inject.Named other = (jakarta.inject.Named) o;
      return value.equals(other.value());
    }

    @Override
    public String toString() {
      return "@"
          + jakarta.inject.Named.class.getName()
          + "(value="
          + Annotations.memberValueString("value", value)
          + ")";
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return jakarta.inject.Named.class;
    }

    private static final long serialVersionUID = 0;
  }

  private static class JsrNamed implements jakarta.inject.Named, Serializable {
    private final String value;

    public JsrNamed(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return this.value;
    }

    @Override
    public int hashCode() {
      // This is specified in java.lang.Annotation.
      return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof jakarta.inject.Named)) {
        return false;
      }

      jakarta.inject.Named other = (jakarta.inject.Named) o;
      return value.equals(other.value());
    }

    @Override
    public String toString() {
      return "@"
          + jakarta.inject.Named.class.getName()
          + "(value="
          + Annotations.memberValueString("value", value)
          + ")";
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return jakarta.inject.Named.class;
    }

    private static final long serialVersionUID = 0;
  }

  private static class GuiceNamed implements com.google.inject.name.Named, Serializable {
    private final String value;

    public GuiceNamed(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return this.value;
    }

    @Override
    public int hashCode() {
      // This is specified in java.lang.Annotation.
      return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof com.google.inject.name.Named)) {
        return false;
      }

      com.google.inject.name.Named other = (com.google.inject.name.Named) o;
      return value.equals(other.value());
    }

    @Override
    public String toString() {
      return "@"
          + com.google.inject.name.Named.class.getName()
          + "(value="
          + Annotations.memberValueString("value", value)
          + ")";
    }

    @Override
    public Class<? extends Annotation> annotationType() {
      return com.google.inject.name.Named.class;
    }

    private static final long serialVersionUID = 0;
  }
}
