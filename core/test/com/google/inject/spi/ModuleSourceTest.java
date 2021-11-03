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
  }

  private void checkSizeTwo(ModuleSource moduleSource) {
    assertEquals(2, moduleSource.size());
  }

  private void checkSizeThree(ModuleSource moduleSource) {
    assertEquals(3, moduleSource.size());
  }

  private ModuleSource createWithSizeOne() {
    return new ModuleSource(A.class, /* permitMap = */ null);
  }

  private ModuleSource createWithSizeTwo() {
    ModuleSource moduleSource = createWithSizeOne();
    return moduleSource.createChild(B.class);
  }

  private ModuleSource createWithSizeThree() {
    ModuleSource moduleSource = createWithSizeTwo();
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

  private static class C extends AbstractModule {
  }
}
