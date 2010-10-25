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

package com.google.inject;

import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.Iterables;
import com.google.inject.matcher.Matchers;
import static com.google.inject.matcher.Matchers.only;
import com.google.inject.spi.ConstructorBinding;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import junit.framework.TestCase;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MethodInterceptionTest extends TestCase {

  private AtomicInteger count = new AtomicInteger();

  private final MethodInterceptor countingInterceptor = new MethodInterceptor() {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count.incrementAndGet();
      return methodInvocation.proceed();
    }
  };

  private final MethodInterceptor returnNullInterceptor = new MethodInterceptor() {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      return null;
    }
  };

  public void testSharedProxyClasses() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class)),
            returnNullInterceptor);
      }
    });

    Injector nullFoosInjector = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
      }
    });

    Interceptable nullFoos = nullFoosInjector.getInstance(Interceptable.class);
    assertNotNull(nullFoos.bar());
    assertNull(nullFoos.foo());

    Injector nullFoosAndBarsInjector = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
        bindInterceptor(Matchers.any(), Matchers.returns(only(Bar.class)),
            returnNullInterceptor);
      }
    });

    Interceptable bothNull = nullFoosAndBarsInjector.getInstance(Interceptable.class);
    assertNull(bothNull.bar());
    assertNull(bothNull.foo());
    
    assertSame("Child injectors should share proxy classes, otherwise memory leaks!",
        nullFoos.getClass(), bothNull.getClass());
  }
  
  public void testGetThis() {
    final AtomicReference<Object> lastTarget = new AtomicReference<Object>();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), new MethodInterceptor() {
          public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            lastTarget.set(methodInvocation.getThis());
            return methodInvocation.proceed();
          }
        });
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    interceptable.foo();
    assertSame(interceptable, lastTarget.get());
  }

  public void testInterceptingFinalClass() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), new MethodInterceptor() {
          public Object invoke(MethodInvocation methodInvocation) throws Throwable {
            return methodInvocation.proceed();
          }
        });
      }
    });
    try {
      injector.getInstance(NotInterceptable.class);
      fail();
    } catch(ConfigurationException ce) {
      assertEquals("Unable to method intercept: " + NotInterceptable.class.getName(),
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage().toString());
      assertEquals("Cannot subclass final class class " + NotInterceptable.class.getName(),
          ce.getCause().getMessage());
    }
  }

  public void testSpiAccessToInterceptors() throws NoSuchMethodException {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class)),
            countingInterceptor);
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class).or(only(Bar.class))),
            returnNullInterceptor);
      }
    });

    ConstructorBinding<?> interceptedBinding
        = (ConstructorBinding<?>) injector.getBinding(Interceptable.class);
    Method barMethod = Interceptable.class.getMethod("bar");
    Method fooMethod = Interceptable.class.getMethod("foo");
    assertEquals(ImmutableMap.<Method, List<MethodInterceptor>>of(
        fooMethod, ImmutableList.of(countingInterceptor, returnNullInterceptor),
        barMethod, ImmutableList.of(returnNullInterceptor)),
        interceptedBinding.getMethodInterceptors());

    ConstructorBinding<?> nonInterceptedBinding
        = (ConstructorBinding<?>) injector.getBinding(Foo.class);
    assertEquals(ImmutableMap.<Method, List<MethodInterceptor>>of(),
        nonInterceptedBinding.getMethodInterceptors());

    injector.getInstance(Interceptable.class).foo();
    assertEquals("expected counting interceptor to be invoked first", 1, count.get());
  }

  static class Foo {}
  static class Bar {}

  public static class Interceptable {
    public Foo foo() {
      return new Foo() {};
    }
    public Bar bar() {
      return new Bar() {};
    }
  }
  
  public static final class NotInterceptable {}
}
