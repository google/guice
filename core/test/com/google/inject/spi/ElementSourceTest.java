package com.google.inject.spi;

import static com.google.inject.internal.InternalFlags.getIncludeStackTraceOption;
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

  public void testCallStackSize() {
    ModuleSource moduleSource = createModuleSource();
    StackTraceElement[] bindingCallStack = new StackTraceElement[3];
    bindingCallStack[0] =
        new StackTraceElement(
            "com.google.inject.spi.Elements$RecordingBinder", "bind", "Unknown Source", 200);
    bindingCallStack[1] =
        new StackTraceElement(
            "com.google.inject.spi.Elements$RecordingBinder", "bind", "Unknown Source", 100);
    bindingCallStack[2] =
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$C", "configure", "Unknown Source", 100);
    ElementSource elementSource =
        new ElementSource(
            /* originalSource = */ null,
            /* trustedOriginalSource = */ false,
            /* declaringSource = */ "",
            moduleSource,
            bindingCallStack,
            /* scanner = */ null);
    assertEquals(10 /* call stack size */, elementSource.getStackTrace().length);
  }

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
          StackTraceElement[] callStack = elementSource.getStackTrace();
          switch (getIncludeStackTraceOption()) {
            case OFF:
              // Check declaring source
              StackTraceElement stackTraceElement =
                  (StackTraceElement) elementSource.getDeclaringSource();
              assertEquals(
                  new StackTraceElement(
                      "com.google.inject.spi.ElementSourceTest$C", "configure", null, -1),
                  stackTraceElement);
              // Check call stack
              assertEquals(0, callStack.length);
              return;
            case ONLY_FOR_DECLARING_SOURCE:
              // Check call stack
              assertEquals(0, callStack.length);
              return;
            case COMPLETE:
              // Check call stack
              int skippedCallStackSize = new Throwable().getStackTrace().length - 1;
              assertEquals(skippedCallStackSize + 15, elementSource.getStackTrace().length);
              assertEquals(
                  "com.google.inject.spi.Elements$RecordingBinder", callStack[0].getClassName());
              assertEquals(
                  "com.google.inject.spi.Elements$RecordingBinder", callStack[1].getClassName());
              assertEquals("com.google.inject.AbstractModule", callStack[2].getClassName());
              // Module C
              assertEquals(
                  "com.google.inject.spi.ElementSourceTest$C", callStack[3].getClassName());
              assertEquals("configure", callStack[3].getMethodName());
              assertEquals("Unknown Source", callStack[3].getFileName());
              assertEquals("com.google.inject.AbstractModule", callStack[4].getClassName());
              assertEquals(
                  "com.google.inject.spi.Elements$RecordingBinder", callStack[5].getClassName());
              // Module B
              assertEquals(
                  "com.google.inject.spi.ElementSourceTest$B", callStack[6].getClassName());
              assertEquals(
                  "com.google.inject.spi.Elements$RecordingBinder", callStack[7].getClassName());
              // Module A
              assertEquals("com.google.inject.AbstractModule", callStack[8].getClassName());
              assertEquals(
                  "com.google.inject.spi.ElementSourceTest$A", callStack[9].getClassName());
              assertEquals("com.google.inject.AbstractModule", callStack[10].getClassName());
              assertEquals(
                  "com.google.inject.spi.Elements$RecordingBinder", callStack[11].getClassName());
              assertEquals("com.google.inject.spi.Elements", callStack[12].getClassName());
              assertEquals("com.google.inject.spi.Elements", callStack[13].getClassName());
              assertEquals("com.google.inject.spi.ElementSourceTest", callStack[14].getClassName());
              // Check modules index
              List<Integer> indexes = elementSource.getModuleConfigurePositionsInStackTrace();
              assertEquals(4, (int) indexes.get(0));
              assertEquals(6, (int) indexes.get(1));
              assertEquals(10, (int) indexes.get(2));
              return;
          }
        }
      }
    }
    fail("The test should not reach this line.");
  }

  private ModuleSource createModuleSource() {
    // First module
    StackTraceElement[] partialCallStack = new StackTraceElement[1];
    partialCallStack[0] = BINDER_INSTALL;
    ModuleSource moduleSource = new ModuleSource(A.class, partialCallStack, /* permitMap = */ null);
    // Second module
    partialCallStack = new StackTraceElement[2];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] =
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$A", "configure", "Unknown Source", 100);
    moduleSource = moduleSource.createChild(B.class, partialCallStack);
    // Third module
    partialCallStack = new StackTraceElement[4];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] = new StackTraceElement("class1", "method1", "Class1.java", 1);
    partialCallStack[2] = new StackTraceElement("class2", "method2", "Class2.java", 2);
    partialCallStack[3] =
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$B", "configure", "Unknown Source", 200);
    return moduleSource.createChild(C.class, partialCallStack);
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
