package com.google.inject.spi;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.Module;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import junit.framework.TestCase;

/** Tests for {@link ElementSource}. */
public class ElementSourceTest extends TestCase {

  private static final StackTraceElement BINDER_INSTALL =
      new StackTraceElement(
          "com.google.inject.spi.Elements$RecordingBinder",
          "install",
          "Unknown Source",
          234 /* line number*/);

  public void testGetCallStack_IntegrationTest() throws Exception {
    List<Element> elements = Elements.getElements(new A());
    for (Element element : elements) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        Class<? extends Annotation> annotationType = binding.getKey().getAnnotationType();
        if (annotationType != null && annotationType.equals(SampleAnnotation.class)) {
          ElementSource elementSource = (ElementSource) binding.getSource();
          List<String> moduleClassNames = elementSource.getModuleClassNames();
          // Check module class names
          // Module C
          assertEquals("com.google.inject.spi.ElementSourceTest$C", moduleClassNames.get(0));
          // Module B
          assertEquals("com.google.inject.spi.ElementSourceTest$B", moduleClassNames.get(1));
          // Module A
          assertEquals("com.google.inject.spi.ElementSourceTest$A", moduleClassNames.get(2));
          return;
        }
      }
    }
    fail("The test should not reach this line.");
  }

  private ModuleSource createModuleSource() {
    // First module
    ModuleSource moduleSource = new ModuleSource(A.class, /* permitMap = */ null);
    // Second module
    moduleSource = moduleSource.createChild(B.class);
    // Third module
    return moduleSource.createChild(C.class);
  }

  private static class A extends AbstractModule {
    @Override
    public void configure() {
      install(new B());
    }
  }

  private static class B implements Module {
    @Override
    public void configure(Binder binder) {
      binder.install(new C());
    }
  }

  @Retention(RUNTIME)
  @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
  @BindingAnnotation
  @interface SampleAnnotation {}

  private static class C extends AbstractModule {
    @Override
    public void configure() {
      bind(String.class).annotatedWith(SampleAnnotation.class).toInstance("the value");
    }
  }
}
