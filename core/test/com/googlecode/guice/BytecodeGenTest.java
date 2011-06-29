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

package com.googlecode.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import static com.google.inject.matcher.Matchers.any;
import com.googlecode.guice.PackageVisibilityTestModule.PublicUserOfPackagePrivate;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This test is in a separate package so we can test package-level visibility
 * with confidence.
 *
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class BytecodeGenTest extends TestCase {

  private final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

  private final Module interceptorModule = new AbstractModule() {
    protected void configure() {
      bindInterceptor(any(), any(), new MethodInterceptor() {
        public Object invoke(MethodInvocation chain)
            throws Throwable {
          return chain.proceed() + " WORLD";
        }
      });
    }
  };

  private final Module noopInterceptorModule = new AbstractModule() {
      protected void configure() {
        bindInterceptor(any(), any(), new MethodInterceptor() {
          public Object invoke(MethodInvocation chain)
              throws Throwable {
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
    Injector injector = Guice.createInjector(interceptorModule, new PackageVisibilityTestModule());
    injector.getInstance(PublicUserOfPackagePrivate.class); // This must pass.
  }
  
  public void testEnhancerNaming() {
    Injector injector = Guice.createInjector(interceptorModule, new PackageVisibilityTestModule());
    PublicUserOfPackagePrivate pupp = injector.getInstance(PublicUserOfPackagePrivate.class);
    assertTrue(pupp.getClass().getName().startsWith(
        PublicUserOfPackagePrivate.class.getName() + "$$EnhancerByGuice$$"));
  }
  
  // TODO(sameb): Figure out how to test FastClass naming tests.

  /**
   * Custom URL classloader with basic visibility rules
   */
  static class TestVisibilityClassLoader
      extends URLClassLoader {

    boolean hideInternals;

    public TestVisibilityClassLoader(boolean hideInternals) {
      super(new URL[0]);

      this.hideInternals = hideInternals;

      final String[] classpath = System.getProperty("java.class.path").split(File.pathSeparator);
      for (final String element : classpath) {
        try {
          // is it a remote/local URL?
          addURL(new URL(element));
        } catch (final MalformedURLException e1) {
          try {
            // nope - perhaps it's a filename?
            addURL(new File(element).toURI().toURL());
          } catch (final MalformedURLException e2) {
            throw new RuntimeException(e1);
          }
        }
      }
    }

    /**
     * Classic parent-delegating classloaders are meant to override findClass.
     * However, non-delegating classloaders (as used in OSGi) instead override
     * loadClass to provide support for "class-space" separation.
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

      } else if (!name.contains(".internal.") && !name.contains(".cglib.")) {

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

  @SuppressWarnings("unchecked")
  protected void setUp() throws Exception {
    super.setUp();

    ClassLoader testClassLoader = new TestVisibilityClassLoader(true);
    proxyTestClass = (Class<ProxyTest>) testClassLoader.loadClass(ProxyTest.class.getName());
    realClass = (Class<ProxyTestImpl>) testClassLoader.loadClass(ProxyTestImpl.class.getName());

    testModule = new AbstractModule() {
      public void configure() {
        bind(proxyTestClass).to(realClass);
      }
    };
  }

  interface ProxyTest {
    String sayHello();
  }

  /**
   * Note: this class must be marked as public or protected so that the Guice
   * custom classloader will intercept it. Private and implementation classes
   * are not intercepted by the custom classloader.
   * 
   * @see com.google.inject.internal.BytecodeGen.Visibility
   */
  public static class ProxyTestImpl implements ProxyTest {

    static {
      //System.out.println(ProxyTestImpl.class.getClassLoader());
    }

    public String sayHello() {
      return "HELLO";
    }
  }

  public void testProxyClassLoading() throws Exception {
    Object testObject = Guice.createInjector(interceptorModule, testModule)
        .getInstance(proxyTestClass);

    // verify method interception still works
    Method m = realClass.getMethod("sayHello");
    assertEquals("HELLO WORLD", m.invoke(testObject));
  }

  public void testSystemClassLoaderIsUsedIfProxiedClassUsesIt() {
    ProxyTest testProxy = Guice.createInjector(interceptorModule, new Module() {
      public void configure(Binder binder) {
        binder.bind(ProxyTest.class).to(ProxyTestImpl.class);
      }
    }).getInstance(ProxyTest.class);

    if (ProxyTest.class.getClassLoader() == systemClassLoader) {
      assertSame(testProxy.getClass().getClassLoader(), systemClassLoader);
    } else {
      assertNotSame(testProxy.getClass().getClassLoader(), systemClassLoader);
    }
  }
  
  public void testProxyClassUnloading() {
    Object testObject = Guice.createInjector(interceptorModule, testModule)
        .getInstance(proxyTestClass);
    assertNotNull(testObject.getClass().getClassLoader());
    assertNotSame(testObject.getClass().getClassLoader(), systemClassLoader);

    // take a weak reference to the generated proxy class
    Reference<Class<?>> clazzRef = new WeakReference<Class<?>>(testObject.getClass());

    assertNotNull(clazzRef.get());

    // null the proxy
    testObject = null;

    /*
     * this should be enough to queue the weak reference
     * unless something is holding onto it accidentally.
     */
    String[] buf;
    System.gc();
    buf = new String[8 * 1024 * 1024];
    buf = null;
    System.gc();
    buf = new String[8 * 1024 * 1024];
    buf = null;
    System.gc();
    buf = new String[8 * 1024 * 1024];
    buf = null;
    System.gc();
    buf = new String[8 * 1024 * 1024];
    buf = null;
    System.gc();

    // This test could be somewhat flaky when the GC isn't working.
    // If it fails, run the test again to make sure it's failing reliably.
    assertNull(clazzRef.get());
  }

  public void testProxyingPackagePrivateMethods() {
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

  static class Hidden {
  }

  public static class HiddenMethodReturn {
    public Hidden method() {
      return new Hidden();
    }
  }

  public static class HiddenMethodParameter {
    public void method(Hidden h) {
    }
  }

  public void testClassLoaderBridging() throws Exception {
    ClassLoader testClassLoader = new TestVisibilityClassLoader(false);

    Class hiddenMethodReturnClass = testClassLoader.loadClass(HiddenMethodReturn.class.getName());
    Class hiddenMethodParameterClass = testClassLoader.loadClass(HiddenMethodParameter.class.getName());

    Injector injector = Guice.createInjector(noopInterceptorModule);

    Class hiddenClass = testClassLoader.loadClass(Hidden.class.getName());
    Constructor ctor = hiddenClass.getDeclaredConstructor();

    ctor.setAccessible(true);

    // don't use bridging for proxies with private parameters
    Object o1 = injector.getInstance(hiddenMethodParameterClass);
    o1.getClass().getDeclaredMethod("method", hiddenClass).invoke(o1, ctor.newInstance());

    // don't use bridging for proxies with private return types
    Object o2 = injector.getInstance(hiddenMethodReturnClass);
    o2.getClass().getDeclaredMethod("method").invoke(o2);
  }
}
