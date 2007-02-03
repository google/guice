/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.intercept;

import com.google.inject.spi.ConstructionProxy;
import static com.google.inject.intercept.Queries.*;

import junit.framework.TestCase;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.InvocationTargetException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProxyFactoryTest extends TestCase {

  public void testSimpleCase()
      throws NoSuchMethodException, InvocationTargetException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();
    builder.intercept(all(), all(), interceptor);
    ProxyFactory factory = builder.create();

    ConstructionProxy<Simple> constructor =
        factory.get(Simple.class.getDeclaredConstructor());

    Simple simple = constructor.newInstance();
    simple.invoke();
    assertTrue(simple.invoked);
    assertTrue(interceptor.invoked);
  }

  static class Simple {
    boolean invoked = false;
    public void invoke() {
      invoked = true;
    }
  }

  static class SimpleInterceptor implements MethodInterceptor {

    boolean invoked = false;

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      invoked = true;
      return methodInvocation.proceed();
    }
  }

  public void testInterceptOneMethod()
      throws NoSuchMethodException, InvocationTargetException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();

    builder.intercept(
        only(Bar.class), annotatedWith(Intercept.class), interceptor);
    ProxyFactory factory = builder.create();

    ConstructionProxy<Foo> fooFactory =
        factory.get(Foo.class.getDeclaredConstructor());
    ConstructionProxy<Bar> barFactory =
        factory.get(Bar.class.getDeclaredConstructor());

    Foo foo = fooFactory.newInstance();
    Bar bar = barFactory.newInstance();

    foo.foo();
    assertTrue(foo.fooCalled);
    assertFalse(interceptor.invoked);

    bar.bar();
    assertTrue(bar.barCalled);
    assertFalse(interceptor.invoked);

    bar.intercepted();
    assertTrue(bar.interceptedCalled);
    assertTrue(interceptor.invoked);
  }

  static class Foo {
    boolean fooCalled;
    @Intercept
    void foo() {
      fooCalled = true;
    }
  }

  static class Bar {

    boolean barCalled;
    void bar() {
      barCalled = true;
    }

    boolean interceptedCalled;

    @Intercept
    void intercepted() {
      interceptedCalled = true;
    }
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Intercept {}

  public void testWithConstructorArguments()
      throws InvocationTargetException, NoSuchMethodException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();
    builder.intercept(all(), all(), interceptor);
    ProxyFactory factory = builder.create();

    ConstructionProxy<A> constructor =
        factory.get(A.class.getDeclaredConstructor(int.class));

    A a = constructor.newInstance(5);
    a.a();
    assertEquals(5, a.i);
  }

  public void testNotProxied()
      throws NoSuchMethodException, InvocationTargetException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();
    builder.intercept(not(all()), not(all()), interceptor);
    ProxyFactory factory = builder.create();

    ConstructionProxy<A> constructor =
        factory.get(A.class.getDeclaredConstructor(int.class));

    A a = constructor.newInstance(5);
    assertEquals(A.class, a.getClass());
  }

  static class A {
    final int i;
    public A(int i) {
      this.i = i;
    }
    public void a() {}
  }

  public void testMultipleInterceptors()
      throws NoSuchMethodException, InvocationTargetException {
    DoubleInterceptor doubleInterceptor = new DoubleInterceptor();
    CountingInterceptor countingInterceptor = new CountingInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();
    builder.intercept(all(), all(), doubleInterceptor, countingInterceptor);
    ProxyFactory factory = builder.create();

    ConstructionProxy<Counter> constructor =
        factory.get(Counter.class.getDeclaredConstructor());

    Counter counter = constructor.newInstance();
    counter.inc();
    assertEquals(2, counter.count);
    assertEquals(2, countingInterceptor.count);
  }

  static class CountingInterceptor implements MethodInterceptor {

    int count;

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count++;
      return methodInvocation.proceed();
    }
  }

  static class DoubleInterceptor implements MethodInterceptor {

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      methodInvocation.proceed();
      return methodInvocation.proceed();
    }
  }

  static class Counter {
    int count;
    void inc() {
      count++;
    }
  }
}
