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

package com.googlecode.guice;

import static com.google.inject.Asserts.getClassPathUrls;
import static com.google.inject.matcher.Matchers.any;

import com.google.common.testing.GcFinalization;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.CustomClassLoadingOption;
import com.googlecode.guice.PackageVisibilityTestModule.PublicUserOfPackagePrivate;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import javax.inject.Inject;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This test is in a separate package so we can test package-level visibility with confidence.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class BytecodeGenTest extends TestCase {

  private final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

  private final Module interceptorModule =
      new AbstractModule() {
        @Override
        protected void configure() {
          bindInterceptor(
              any(),
              any(),
              new MethodInterceptor() {
                @Override
                public Object invoke(MethodInvocation chain) throws Throwable {
                  return chain.proceed() + " WORLD";
                }
              });
        }
      };

  private final Module noopInterceptorModule =
      new AbstractModule() {
        @Override
        protected void configure() {
          bindInterceptor(
              any(),
              any(),
              new MethodInterceptor() {
                @Override
                public Object invoke(MethodInvocation chain) throws Throwable {
                  return chain.proceed();
                }
              });
        }
      };

  public void testPackageVisibility() {
    Injector injector = Guice.createInjector(new PackageVisibilityTestModule());
    injector.getInstance(PublicUserOfPackagePrivate.class); // This must pass.
  }

  public void testInterceptedPackageVisibility() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    Injector injector = Guice.createInjector(interceptorModule, new PackageVisibilityTestModule());
    injector.getInstance(PublicUserOfPackagePrivate.class); // This must pass.
  }

  public void testEnhancerNaming() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    Injector injector = Guice.createInjector(interceptorModule, new PackageVisibilityTestModule());
    PublicUserOfPackagePrivate pupp = injector.getInstance(PublicUserOfPackagePrivate.class);
    assertTrue(
        pupp.getClass()
            .getName()
            .startsWith(PublicUserOfPackagePrivate.class.getName() + "$$EnhancerByGuice$$"));
  }

  // TODO(sameb): Figure out how to test FastClass naming tests.

  /** Custom URL classloader with basic visibility rules */
  static class TestVisibilityClassLoader extends URLClassLoader {

    final boolean hideInternals;

    TestVisibilityClassLoader(boolean hideInternals) {
      this(TestVisibilityClassLoader.class.getClassLoader(), hideInternals);
    }

    TestVisibilityClassLoader(ClassLoader classloader, boolean hideInternals) {
      super(getClassPathUrls(), classloader);
      this.hideInternals = hideInternals;
    }

    /**
     * Classic parent-delegating classloaders are meant to override findClass. However,
     * non-delegating classloaders (as used in OSGi) instead override loadClass to provide support
     * for "class-space" separation.
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
        throws ClassNotFoundException {

      synchronized (this) {
        // check our local cache to avoid duplicates
        final Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
          return clazz;
        }
      }

      if (name.startsWith("java.")) {

        // standard bootdelegation of java.*
        return super.loadClass(name, resolve);

      } else if (!name.contains(".internal.")) {

        /*
         * load public and test classes directly from the classpath - we don't
         * delegate to our parent because then the loaded classes would also be
         * able to see private internal Guice classes, as they are also loaded
         * by the parent classloader.
         */
        final Class<?> clazz = findClass(name);
        if (resolve) {
          resolveClass(clazz);
        }
        return clazz;
      }

      // hide internal non-test classes
      if (hideInternals) {
        throw new ClassNotFoundException();
      }
      return super.loadClass(name, resolve);
    }
  }

  /** as loaded by another class loader */
  private Class<ProxyTest> proxyTestClass;

  private Class<ProxyTestImpl> realClass;
  private Module testModule;

  @Override
  @SuppressWarnings("unchecked")
  protected void setUp() throws Exception {
    super.setUp();

    ClassLoader testClassLoader = new TestVisibilityClassLoader(true);
    proxyTestClass = (Class<ProxyTest>) testClassLoader.loadClass(ProxyTest.class.getName());
    realClass = (Class<ProxyTestImpl>) testClassLoader.loadClass(ProxyTestImpl.class.getName());

    testModule =
        new AbstractModule() {
          @Override
          public void configure() {
            bind(proxyTestClass).to(realClass);
          }
        };
  }

  interface ProxyTest {
    String sayHello();
  }

  /**
   * Note: this class must be marked as public or protected so that the Guice custom classloader
   * will intercept it. Private and implementation classes are not intercepted by the custom
   * classloader.
   */
  public static class ProxyTestImpl implements ProxyTest {

    static {
      //System.out.println(ProxyTestImpl.class.getClassLoader());
    }

    @Override
    public String sayHello() {
      return "HELLO";
    }
  }

  public void testProxyClassLoading() throws Exception {
    Object testObject =
        Guice.createInjector(interceptorModule, testModule).getInstance(proxyTestClass);

    // verify method interception still works
    Method m = realClass.getMethod("sayHello");
    assertEquals("HELLO WORLD", m.invoke(testObject));
  }

  public void testSystemClassLoaderIsUsedIfProxiedClassUsesIt() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    ProxyTest testProxy =
        Guice.createInjector(
                interceptorModule,
                new Module() {
                  @Override
                  public void configure(Binder binder) {
                    binder.bind(ProxyTest.class).to(ProxyTestImpl.class);
                  }
                })
            .getInstance(ProxyTest.class);

    if (ProxyTest.class.getClassLoader() == systemClassLoader) {
      assertSame(testProxy.getClass().getClassLoader(), systemClassLoader);
    } else {
      assertNotSame(testProxy.getClass().getClassLoader(), systemClassLoader);
    }
  }

  public void testProxyClassUnloading() {
    Object testObject =
        Guice.createInjector(interceptorModule, testModule).getInstance(proxyTestClass);
    assertNotNull(testObject.getClass().getClassLoader());
    assertNotSame(testObject.getClass().getClassLoader(), systemClassLoader);

    // take a weak reference to the generated proxy class
    WeakReference<Class<?>> clazzRef = new WeakReference<Class<?>>(testObject.getClass());

    assertNotNull(clazzRef.get());

    // null the proxy
    testObject = null;

    // null the host class
    proxyTestClass = null;
    realClass = null;

    /*
     * this should be enough to queue the weak reference
     * unless something is holding onto it accidentally.
     */
    GcFinalization.awaitClear(clazzRef);

    // This test could be somewhat flaky when the GC isn't working.
    // If it fails, run the test again to make sure it's failing reliably.
    assertNull("Proxy class was not unloaded.", clazzRef.get());
  }

  public void testProxyingPackagePrivateMethods() {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    Injector injector = Guice.createInjector(interceptorModule);
    assertEquals("HI WORLD", injector.getInstance(PackageClassPackageMethod.class).sayHi());
    assertEquals("HI WORLD", injector.getInstance(PublicClassPackageMethod.class).sayHi());
    assertEquals("HI WORLD", injector.getInstance(ProtectedClassProtectedMethod.class).sayHi());
  }

  static class PackageClassPackageMethod {
    String sayHi() {
      return "HI";
    }
  }

  public static class PublicClassPackageMethod {
    String sayHi() {
      return "HI";
    }
  }

  protected static class ProtectedClassProtectedMethod {
    protected String sayHi() {
      return "HI";
    }
  }

  static class Hidden {}

  public static class HiddenMethodReturn {
    public Hidden method() {
      return new Hidden();
    }
  }

  public static class HiddenMethodParameter {
    public void method(Hidden h) {}
  }

  public void testClassLoaderBridging() throws Exception {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    ClassLoader testClassLoader = new TestVisibilityClassLoader(false);

    Class<?> hiddenMethodReturnClass =
        testClassLoader.loadClass(HiddenMethodReturn.class.getName());
    Class<?> hiddenMethodParameterClass =
        testClassLoader.loadClass(HiddenMethodParameter.class.getName());

    Injector injector = Guice.createInjector(noopInterceptorModule);

    Class<?> hiddenClass = testClassLoader.loadClass(Hidden.class.getName());
    Constructor<?> ctor = hiddenClass.getDeclaredConstructor();

    ctor.setAccessible(true);

    // don't use bridging for proxies with private parameters
    Object o1 = injector.getInstance(hiddenMethodParameterClass);
    o1.getClass().getDeclaredMethod("method", hiddenClass).invoke(o1, ctor.newInstance());

    // don't use bridging for proxies with private return types
    Object o2 = injector.getInstance(hiddenMethodReturnClass);
    o2.getClass().getDeclaredMethod("method").invoke(o2);
  }

  // This tests for a situation where an osgi bundle contains a different version of guice.
  public void testFastClassWithDifferentVersionsOfGuice() throws Throwable {
    // Test relies on package access which CHILD loading doesn't have
    if (InternalFlags.getCustomClassLoadingOption() == CustomClassLoadingOption.CHILD) {
      return;
    }
    Injector injector = Guice.createInjector();
    // These classes are all in the same classloader as guice itself, so other than the private one
    // they can all be fast class invoked
    injector.getInstance(PublicInject.class).assertIsFastClassInvoked();
    injector.getInstance(ProtectedInject.class).assertIsFastClassInvoked();
    injector.getInstance(PackagePrivateInject.class).assertIsFastClassInvoked();
    injector.getInstance(PrivateInject.class).assertIsReflectionInvoked();

    // This classloader loads the test types in a loader that has a different version of guice;
    // we can still use fastclass because the generated class is now fully decoupled from guice
    MultipleVersionsOfGuiceClassLoader fakeLoader = new MultipleVersionsOfGuiceClassLoader();
    injector
        .getInstance(fakeLoader.loadLogCreatorType(PublicInject.class))
        .assertIsFastClassInvoked();
    injector
        .getInstance(fakeLoader.loadLogCreatorType(ProtectedInject.class))
        .assertIsFastClassInvoked();
    injector
        .getInstance(fakeLoader.loadLogCreatorType(PackagePrivateInject.class))
        .assertIsFastClassInvoked();
    injector
        .getInstance(fakeLoader.loadLogCreatorType(PrivateInject.class))
        .assertIsReflectionInvoked();
  }

  // This classloader simulates an OSGI environment where there's a conflicting version of guice.
  static class MultipleVersionsOfGuiceClassLoader extends URLClassLoader {
    MultipleVersionsOfGuiceClassLoader() {
      this(MultipleVersionsOfGuiceClassLoader.class.getClassLoader());
    }

    MultipleVersionsOfGuiceClassLoader(ClassLoader classloader) {
      super(getClassPathUrls(), classloader);
    }

    public Class<? extends LogCreator> loadLogCreatorType(Class<? extends LogCreator> cls)
        throws ClassNotFoundException {
      return loadClass(cls.getName()).asSubclass(LogCreator.class);
    }

    /**
     * Classic parent-delegating classloaders are meant to override findClass. However,
     * non-delegating classloaders (as used in OSGi) instead override loadClass to provide support
     * for "class-space" separation.
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve)
        throws ClassNotFoundException {

      synchronized (this) {
        // check our local cache to avoid duplicates
        final Class<?> clazz = findLoadedClass(name);
        if (clazz != null) {
          return clazz;
        }
      }

      if (name.startsWith("java.")
          || name.startsWith("javax.")
          || name.equals(LogCreator.class.getName())
          || (!name.startsWith("com.google.inject.")
              && !name.startsWith("com.googlecode.guice"))) {

        // standard parent delegation
        return super.loadClass(name, resolve);

      } else {
        // load a new copy of the class
        final Class<?> clazz = findClass(name);
        if (resolve) {
          resolveClass(clazz);
        }
        return clazz;
      }
    }
  }

  public static class LogCreator {
    final Throwable caller;

    public LogCreator() {
      this.caller = new Throwable();
    }

    void assertIsFastClassInvoked() throws Throwable {
      // 2 because the first 2 elements are
      // LogCreator.<init>()
      // Subclass.<init>()
      if (!caller.getStackTrace()[2].getClassName().contains("$$FastClassByGuice$$")) {
        throw new AssertionError("Caller was not FastClass").initCause(caller);
      }
    }

    void assertIsReflectionInvoked() throws Throwable {
      // Scan for a call to Constructor.newInstance, but stop if we see the test itself.
      for (StackTraceElement element : caller.getStackTrace()) {
        if (element.getClassName().equals(BytecodeGenTest.class.getName())) {
          // break when we hit the test method.
          break;
        }
        if (element.getClassName().equals(Constructor.class.getName())
            && element.getMethodName().equals("newInstance")) {
          return;
        }
      }
      throw new AssertionError("Caller was not Constructor.newInstance").initCause(caller);
    }
  }

  public static class PublicInject extends LogCreator {
    @Inject
    public PublicInject() {}
  }

  static class PackagePrivateInject extends LogCreator {
    @Inject
    PackagePrivateInject() {}
  }

  protected static class ProtectedInject extends LogCreator {
    @Inject
    protected ProtectedInject() {}
  }

  private static class PrivateInject extends LogCreator {
    @Inject
    private PrivateInject() {}
  }
}
