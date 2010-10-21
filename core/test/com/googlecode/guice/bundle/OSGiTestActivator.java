/**
 * Copyright (C) 2009 Google Inc.
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

package com.googlecode.guice.bundle;

import static com.google.inject.name.Names.named;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Random;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.matcher.AbstractMatcher;

/**
 * Test Guice from inside an OSGi bundle activator.
 * 
 * @author mcculls@gmail.com (Stuart McCulloch)
 */
@SuppressWarnings("unused") public class OSGiTestActivator
    implements BundleActivator {

  // varying visibilities to test our code-generation support

  public static class _ {}

  public interface A {}

  protected interface B {}

  interface C {}

  private interface D {}

  public static class AA
      implements A {

    public AA() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  protected static class AB
      implements A {

    public AB() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  static class AC
      implements A {

    public AC() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  private static class AD
      implements A {

    public AD() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  public static class BA
      implements B {

    protected BA() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  protected static class BB
      implements B {

    protected BB() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  static class BC
      implements B {

    protected BC() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  private static class BD
      implements B {

    protected BD() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  public static class CA
      implements C {

    CA() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  protected static class CB
      implements C {

    CB() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  static class CC
      implements C {

    CC() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  private static class CD
      implements C {

    CD() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  public static class DA
      implements D {

    @Inject private DA() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  protected static class DB
      implements D {

    @Inject private DB() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  static class DC
      implements D {

    @Inject private DC() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  private static class DD
      implements D {

    private DD() {}

    @Inject public void setA(_ _) {}

    @Inject protected void setB(_ _) {}

    @Inject void setC(_ _) {}

    @Inject private void setD(_ _) {}

    @Inject public _ a;

    @Inject protected _ b;

    @Inject _ c;

    @Inject private _ d;
  }

  enum Visibility {
    PUBLIC, PROTECTED, PACKAGE_PRIVATE, PRIVATE
  }

  static final Class<?>[] TEST_CLAZZES = {A.class, B.class, C.class, D.class};

  // registers all the class combinations
  static class TestModule
      extends AbstractModule {

    final Bundle bundle;

    TestModule(Bundle bundle) {
      this.bundle = bundle;
    }

    @Override @SuppressWarnings("unchecked") protected void configure() {
      for (Class<?> api : TEST_CLAZZES) {
        for (Visibility visibility : Visibility.values()) {
          try {

            // this registers: A + PUBLIC -> AA, A + PROTECTED -> AB, etc...
            String suffix = TEST_CLAZZES[visibility.ordinal()].getSimpleName();
            Class imp = bundle.loadClass(api.getName() + suffix);
            bind(api).annotatedWith(named(visibility.name())).to(imp);

          } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load test class", e);
          }
        }
      }
    }
  }

/*if[AOP]*/
  // applies method-interception to classes with enough visibility
  static class InterceptorModule
      extends AbstractModule {
    @Override protected void configure() {
      bindInterceptor(new AbstractMatcher<Class<?>>() {
        public boolean matches(Class<?> clazz) {
          try {

            // the class and constructor must be visible
            int clazzModifiers = clazz.getModifiers();
            int ctorModifiers = clazz.getConstructor().getModifiers();
            return (clazzModifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0
                && (ctorModifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;

          } catch (NoSuchMethodException e) {
            return false;
          }
        }
      }, new AbstractMatcher<Method>() {
        public boolean matches(Method method) {

          // the intercepted method must also be visible
          int methodModifiers = method.getModifiers();
          return (methodModifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;

        }
      }, new org.aopalliance.intercept.MethodInterceptor() {
        public Object invoke(org.aopalliance.intercept.MethodInvocation mi)
            throws Throwable {

          return mi.proceed();
        }
      });
    }
  }
/*end[AOP]*/

  // called from OSGi when bundle starts
  public void start(BundleContext context)
      throws BundleException {

    final Bundle bundle = context.getBundle();

    Injector injector = Guice.createInjector(new TestModule(bundle));
/*if[AOP]*/
    Injector aopInjector = Guice.createInjector(new TestModule(bundle), new InterceptorModule());
/*end[AOP]*/

    // test code-generation support
    for (Class<?> api : TEST_CLAZZES) {
      for (Visibility vis : Visibility.values()) {
        injector.getInstance(Key.get(api, named(vis.name())));
/*if[AOP]*/
        aopInjector.getInstance(Key.get(api, named(vis.name())));
/*end[AOP]*/
      }
    }

    // test injection of system class (issue 343)
    injector.getInstance(Random.class);
/*if[AOP]*/
    aopInjector.getInstance(Random.class);
/*end[AOP]*/
  }

  // called from OSGi when bundle stops
  public void stop(BundleContext context) {}
}
