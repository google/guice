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
import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
public class BytecodeGenTest extends TestCase {

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

  /**
   * Custom URL classloader with basic visibility rules
   */
  static class TestVisibilityClassLoader
      extends URLClassLoader {

    public TestVisibilityClassLoader() {
      super(new URL[0]);

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
      throw new ClassNotFoundException();
    }
  }

  interface ProxyTest {
    String sayHello();
  }

  /**
   * Note: this class must be marked as public or protected so that the Guice
   * custom classloader will intercept it. Private and implementation classes
   * are not intercepted by the custom classloader.
   * 
   * @see com.google.inject.internal.BytecodeGen#isHookable(Class)
   */
  public static class ProxyTestImpl implements ProxyTest {

    static {
      //System.out.println(ProxyTestImpl.class.getClassLoader());
    }

    public String sayHello() {
      return "HELLO";
    }
  }

  @SuppressWarnings("unchecked")
  public void testProxyClassLoading() {
    final ClassLoader testClassLoader = new TestVisibilityClassLoader();

    final Class<ProxyTest> testAPIClazz;
    final Class<ProxyTest> testImplClazz;

    try {
      testAPIClazz = (Class<ProxyTest>) testClassLoader.loadClass(ProxyTest.class.getName());
      testImplClazz = (Class<ProxyTest>) testClassLoader.loadClass(ProxyTestImpl.class.getName());
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }

    final Object testObject = Guice.createInjector(interceptorModule, new Module() {
      public void configure(Binder binder) {
        binder.bind(testAPIClazz).to(testImplClazz);
      }
    }).getInstance(testAPIClazz);

    try {
      // verify method interception still works
      Method m = testImplClazz.getMethod("sayHello");
      assertEquals("HELLO WORLD", m.invoke(testObject));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void testProxyClassUnloading() {
    ProxyTest testProxy = Guice.createInjector(interceptorModule, new Module() {
      public void configure(Binder binder) {
        binder.bind(ProxyTest.class).to(ProxyTestImpl.class);
      }
    }).getInstance(ProxyTest.class);

    // take a weak reference to the generated proxy class
    Reference<Class<?>> clazzRef = new WeakReference<Class<?>>(testProxy.getClass());

    assertNotNull(clazzRef.get());

    // null the proxy
    testProxy = null;

    /*
     * this should be enough to queue the weak reference
     * unless something is holding onto it accidentally.
     */
    System.gc();
    System.gc();
    System.gc();

    assertNull(clazzRef.get());
  }
  
  public void testProxyingPackagePrivateMethods() {
    Injector injector = Guice.createInjector(interceptorModule);
    PackagePrivate instance = injector.getInstance(PackagePrivate.class);
    assertEquals("HI WORLD", instance.sayHi());
  }

  static class PackagePrivate {
    String sayHi() {
      return "HI";
    }
  }
}
