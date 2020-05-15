package com.google.inject.spi;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import junit.framework.TestCase;

/** Tests for {@link ModuleSource}. */
public class ModuleSourceTest extends TestCase {

  private static final StackTraceElement BINDER_INSTALL =
      new StackTraceElement(
          "com.google.inject.spi.Elements$RecordingBinder",
          "install",
          "Unknown Source",
          235 /* line number*/);

  public void testOneModule() {
    ModuleSource moduleSource = createWithSizeOne();
    checkSizeOne(moduleSource);
  }

  public void testTwoModules() {
    ModuleSource moduleSource = createWithSizeTwo();
    checkSizeTwo(moduleSource);
    moduleSource = moduleSource.getParent();
    checkSizeOne(moduleSource);
  }

  public void testThreeModules() {
    ModuleSource moduleSource = createWithSizeThree();
    checkSizeThree(moduleSource);
    moduleSource = moduleSource.getParent();
    checkSizeTwo(moduleSource);
    moduleSource = moduleSource.getParent();
    checkSizeOne(moduleSource);
  }

  private void checkSizeOne(ModuleSource moduleSource) {
    assertEquals(1, moduleSource.size());
    assertEquals(1, moduleSource.getStackTraceSize());
    // Check call stack
    StackTraceElement[] callStack = moduleSource.getStackTrace();
    assertEquals(BINDER_INSTALL, callStack[0]);
  }

  private void checkSizeTwo(ModuleSource moduleSource) {
    assertEquals(2, moduleSource.size());
    assertEquals(3, moduleSource.getStackTraceSize());
    // Check call stack
    StackTraceElement[] callStack = moduleSource.getStackTrace();
    assertEquals(BINDER_INSTALL, callStack[0]);
    assertEquals(
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$A", "configure", "Unknown Source", 100),
        callStack[1]);
    assertEquals(BINDER_INSTALL, callStack[2]);
  }

  private void checkSizeThree(ModuleSource moduleSource) {
    assertEquals(3, moduleSource.size());
    assertEquals(7, moduleSource.getStackTraceSize());
    // Check call stack
    StackTraceElement[] callStack = moduleSource.getStackTrace();
    assertEquals(BINDER_INSTALL, callStack[0]);
    assertEquals(new StackTraceElement("class1", "method1", "Unknown Source", 1), callStack[1]);
    assertEquals(new StackTraceElement("class2", "method2", "Unknown Source", 2), callStack[2]);
    assertEquals(
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$B", "configure", "Unknown Source", 200),
        callStack[3]);
    assertEquals(BINDER_INSTALL, callStack[4]);

    assertEquals(
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$A", "configure", "Unknown Source", 100),
        callStack[5]);
    assertEquals(BINDER_INSTALL, callStack[6]);
  }

  private ModuleSource createWithSizeOne() {
    StackTraceElement[] partialCallStack = new StackTraceElement[1];
    partialCallStack[0] = BINDER_INSTALL;
    return new ModuleSource(A.class, partialCallStack, /* permitMap = */ null);
  }

  private ModuleSource createWithSizeTwo() {
    ModuleSource moduleSource = createWithSizeOne();
    StackTraceElement[] partialCallStack = new StackTraceElement[2];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] =
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$A", "configure", "moduleSourceTest.java", 100);
    return moduleSource.createChild(B.class, partialCallStack);
  }

  private ModuleSource createWithSizeThree() {
    ModuleSource moduleSource = createWithSizeTwo();
    StackTraceElement[] partialCallStack = new StackTraceElement[4];
    partialCallStack[0] = BINDER_INSTALL;
    partialCallStack[1] = new StackTraceElement("class1", "method1", "Class1.java", 1);
    partialCallStack[2] = new StackTraceElement("class2", "method2", "Class2.java", 2);
    partialCallStack[3] =
        new StackTraceElement(
            "com.google.inject.spi.moduleSourceTest$B", "configure", "moduleSourceTest.java", 200);
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

  private static class C extends AbstractModule {
  }
}
