// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.intercept;

import com.google.inject.spi.ConstructionProxy;

import junit.framework.TestCase;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.InvocationTargetException;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ProxyFactoryTest extends TestCase {

  public void testSimpleCase()
      throws NoSuchMethodException, InvocationTargetException {
    SimpleInterceptor interceptor = new SimpleInterceptor();

    ProxyFactoryBuilder builder = new ProxyFactoryBuilder();
    builder.intercept(Queries.any(), Queries.any(), interceptor);
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


}
