package com.google.inject.spi;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.BindingAnnotation;
import com.google.inject.Module;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

/**
 * Tests for {@link ElementSource}.
 */
public class ElementSourceTest extends TestCase {

  private static final StackTraceElement BINDER_INSTALL = 
      new StackTraceElement("com.google.inject.spi.Elements$RecordingBinder", "install", 
          "Unknown Source", 234 /* line number*/);
  
  public void testCallStackSize() {
    ModuleSource moduleSource = createModuleSource();
    StackTraceElement[] bindingCallStack = new StackTraceElement[3];
    bindingCallStack[0] = new StackTraceElement(
        "com.google.inject.spi.Elements$RecordingBinder", "bind", "Unknown Source", 200);
    bindingCallStack[1] = new StackTraceElement(
        "com.google.inject.spi.Elements$RecordingBinder", "bind", "Unknown Source", 100);
    bindingCallStack[2] = new StackTraceElement(
        "com.google.inject.spi.moduleSourceTest$C", "configure", "Unknown Source", 100);
    ElementSource elementSource = new ElementSource(
        null /* No original element source */, "" /* Don't care */, moduleSource, bindingCallStack);
    assertEquals(10 /* call stack size */, elementSource.getStackTrace().length);
  }  

  public void testSourceIsNotElementSource() {
    List<Element> elements = Elements.getElements(new A());
    for (Element element : elements) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        Class<? extends Annotation> annotationType = binding.getKey().getAnnotationType();
        if (annotationType != null && annotationType.equals(SampleAnnotation.class)) {
          assertFalse("Source can not be an ElementSource.", 
              binding.getSource() instanceof ElementSource);
        }
      }
    }
  }  
  
  // TODO(salmanmir): uncomment this test when the above test is removed.
//  public void testGetCallStack_integrationTest() {
//    List<Element> elements = Elements.getElements(new A());
//    for (Element element : elements) {
//      if (element instanceof Binding) {
//        Binding<?> binding = (Binding<?>) element;
//        Class<? extends Annotation> annotationType = binding.getKey().getAnnotationType();
//        if (annotationType != null && annotationType.equals(SampleAnnotation.class)) {
//          ElementSource elementSource = (ElementSource) binding.getSource();
//          StackTraceElement[] callStack = elementSource.getStackTrace();
//          // check call stack size
//          int skippedCallStackSize = new Throwable().getStackTrace().length - 1;
//          assertEquals(skippedCallStackSize + 15, elementSource.getStackTrace().length);
//          assertEquals("com.google.inject.spi.Elements$RecordingBinder",
//              callStack[0].getClassName());
//          assertEquals("com.google.inject.spi.Elements$RecordingBinder",
//              callStack[1].getClassName());
//          assertEquals("com.google.inject.AbstractModule",
//              callStack[2].getClassName());
//          // Module C
//          assertEquals("com.google.inject.spi.ElementSourceTest$C",
//              callStack[3].getClassName());
//          assertEquals("configure",
//              callStack[3].getMethodName());
//          assertEquals("Unknown Source",
//              callStack[3].getFileName());
//          assertEquals("com.google.inject.AbstractModule",
//              callStack[4].getClassName());
//          assertEquals("com.google.inject.spi.Elements$RecordingBinder",
//              callStack[5].getClassName());
//          // Module B
//          assertEquals("com.google.inject.spi.ElementSourceTest$B",
//              callStack[6].getClassName());
//          assertEquals("com.google.inject.spi.Elements$RecordingBinder",
//              callStack[7].getClassName());
//          // Module A
//          assertEquals("com.google.inject.AbstractModule",
//              callStack[8].getClassName());
//          assertEquals("com.google.inject.spi.ElementSourceTest$A",
//              callStack[9].getClassName());
//          assertEquals("com.google.inject.AbstractModule",
//              callStack[10].getClassName());
//          assertEquals("com.google.inject.spi.Elements$RecordingBinder",
//              callStack[11].getClassName());
//          assertEquals("com.google.inject.spi.Elements",
//              callStack[12].getClassName());
//          assertEquals("com.google.inject.spi.Elements",
//              callStack[13].getClassName());
//          assertEquals("com.google.inject.spi.ElementSourceTest",
//              callStack[14].getClassName());
//          return;
//        }
//      }
//    }
//  fail("The test should not reach this line.");
//  }  

  private ModuleSource createModuleSource() {
    // First module
    StackTraceElement[] partialCallStack = new StackTraceElement[1];
    partialCallStack[0] = BINDER_INSTALL;    
    ModuleSource moduleSource = new ModuleSource(new A(), partialCallStack);
    // Second module 
    partialCallStack = new StackTraceElement[2];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] = new StackTraceElement(
        "com.google.inject.spi.moduleSourceTest$A", "configure", "Unknown Source", 100);
    moduleSource = moduleSource.createChild(new B(), partialCallStack);    
    // Third module
    partialCallStack = new StackTraceElement[4];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] = new StackTraceElement("class1", "method1", "Class1.java", 1);
    partialCallStack[2] = new StackTraceElement("class2", "method2", "Class2.java", 2);
    partialCallStack[3] = new StackTraceElement(
        "com.google.inject.spi.moduleSourceTest$B", "configure", "Unknown Source", 200);
    return moduleSource.createChild(new C(), partialCallStack);
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
  @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD })
  @BindingAnnotation
  @interface SampleAnnotation { }

  private static class C extends AbstractModule {
    @Override
    public void configure() {
      bind(String.class).annotatedWith(SampleAnnotation.class).toInstance("the value");
    }
  }  
}
