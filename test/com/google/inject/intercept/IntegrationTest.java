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

import static com.google.inject.intercept.Queries.*;
import com.google.inject.ContainerBuilder;
import com.google.inject.Container;
import com.google.inject.ContainerCreationException;
import com.google.inject.Key;

import junit.framework.TestCase;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class IntegrationTest extends TestCase {

  public void testIntegration() throws ContainerCreationException {
    CountingInterceptor counter = new CountingInterceptor();
    ProxyFactoryBuilder proxyFactoryBuilder = new ProxyFactoryBuilder();
    proxyFactoryBuilder.intercept(any(), any(), counter);
    ProxyFactory proxyFactory = proxyFactoryBuilder.create();

    ContainerBuilder containerBuilder = new ContainerBuilder(proxyFactory);
    containerBuilder.bind(Foo.class);
    Container container = containerBuilder.create(false);

    Foo foo = container.getFactory(Key.get(Foo.class)).get();
    foo.foo();
    assertTrue(foo.invoked);
    assertEquals(1, counter.count);

    foo = container.getCreator(Foo.class).get();
    foo.foo();
    assertTrue(foo.invoked);
    assertEquals(2, counter.count);
  }

  static class Foo {
    boolean invoked;
    public void foo() {
      invoked = true;
    }
  }

  static class CountingInterceptor implements MethodInterceptor {

    int count;

    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count++;
      return methodInvocation.proceed();
    }
  }

}
