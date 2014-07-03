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

import static com.google.inject.matcher.Matchers.only;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ConstructorBinding;

import junit.framework.TestCase;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MethodInterceptionTest extends TestCase {

  private AtomicInteger count = new AtomicInteger();

  private final class CountingInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count.incrementAndGet();
      return methodInvocation.proceed();
    }
  }

  private final class ReturnNullInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      return null;
    }
  }
  
  private final class NoOpInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      return methodInvocation.proceed();
    }
  }

  public void testSharedProxyClasses() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class)),
            new ReturnNullInterceptor());
      }
    });

    Injector childOne = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
      }
    });

    Interceptable nullFoosOne = childOne.getInstance(Interceptable.class);
    assertNotNull(nullFoosOne.bar());
    assertNull(nullFoosOne.foo()); // confirm it's being intercepted

    Injector childTwo = injector.createChildInjector(new AbstractModule() {
      protected void configure() {
        bind(Interceptable.class);
      }
    });

    Interceptable nullFoosTwo = childTwo.getInstance(Interceptable.class);
    assertNull(nullFoosTwo.foo()); // confirm it's being intercepted

    assertSame("Child injectors should share proxy classes, otherwise memory leaks!",
        nullFoosOne.getClass(), nullFoosTwo.getClass());
    
    Injector injector2 = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class)),
            new ReturnNullInterceptor());
      }
    });
    Interceptable separateNullFoos = injector2.getInstance(Interceptable.class);
    assertNull(separateNullFoos.foo()); // confirm it's being intercepted
    assertSame("different injectors should share proxy classes, otherwise memory leaks!",
        nullFoosOne.getClass(), separateNullFoos.getClass());
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
    final MethodInterceptor countingInterceptor = new CountingInterceptor();
    final MethodInterceptor returnNullInterceptor = new ReturnNullInterceptor();
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(),Matchers.returns(only(Foo.class)),
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

  public void testInterceptedMethodThrows() throws Exception {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), new CountingInterceptor());
        bindInterceptor(Matchers.any(), Matchers.any(), new CountingInterceptor());
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    try {
      interceptable.explode();
      fail();
    } catch (Exception e) {
      // validate all causes.
      for (Throwable t = e; t != null; t = t.getCause()) {
        StackTraceElement[] stackTraceElement = t.getStackTrace();
        assertEquals("explode", stackTraceElement[0].getMethodName());
        assertEquals("invoke", stackTraceElement[1].getMethodName());
        assertEquals("invoke", stackTraceElement[2].getMethodName());
        assertEquals("testInterceptedMethodThrows", stackTraceElement[3].getMethodName());
      }
    }
  }
  
  public void testNotInterceptedMethodsInInterceptedClassDontAddFrames() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.returns(only(Foo.class)),
            new NoOpInterceptor());
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    assertNull(interceptable.lastElements);
    interceptable.foo();
    boolean cglibFound = false;
    for (int i = 0; i < interceptable.lastElements.length; i++) {
      if (interceptable.lastElements[i].toString().contains("cglib")) {
        cglibFound = true;
        break;
      }
    }
    assertTrue(Arrays.toString(interceptable.lastElements), cglibFound);
    cglibFound = false;
    
    interceptable.bar();
    for (int i = 0; i < interceptable.lastElements.length; i++) {
      if (interceptable.lastElements[i].toString().contains("cglib")) {
        cglibFound = true;
        break;
      }
    }
    assertFalse(Arrays.toString(interceptable.lastElements), cglibFound);
  }

  static class Foo {}
  static class Bar {}

  public static class Interceptable {
    StackTraceElement[] lastElements; 
    
    public Foo foo() {
      lastElements = Thread.currentThread().getStackTrace();
      return new Foo() {};
    }
    public Bar bar() {
      lastElements = Thread.currentThread().getStackTrace();
      return new Bar() {};
    }
    public String explode() throws Exception {
      lastElements = Thread.currentThread().getStackTrace();
      throw new Exception("kaboom!", new RuntimeException("boom!"));
    }
  }

  public static final class NotInterceptable {}
  
  public void testInterceptingNonBridgeWorks() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Interface.class).to(Impl.class);
        bindInterceptor(Matchers.any(), new AbstractMatcher<Method>() {
          public boolean matches(Method t) {
            return !t.isBridge() && t.getDeclaringClass() != Object.class;
          }
        }, new CountingInterceptor());
      }
    });
    Interface intf = injector.getInstance(Interface.class);
    assertEquals(0, count.get());
    intf.aMethod(null);
    assertEquals(1, count.get());
  }
  
  static class ErasedType {}
  static class RetType extends ErasedType {}  
  static abstract class Superclass<T extends ErasedType> {
      public T aMethod(T t) { return null; }
  }
  public interface Interface {
      RetType aMethod(RetType obj);
  }
  public static class Impl extends Superclass<RetType> implements Interface {
  }
  
  public void testInterceptionOrder() {
    final List<String> callList = Lists.newArrayList();
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), 
          new NamedInterceptor("a", callList),
          new NamedInterceptor("b", callList),
          new NamedInterceptor("c", callList));
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    assertEquals(0, callList.size());
    interceptable.foo();
    assertEquals(Arrays.asList("a", "b", "c"), callList);
  }
  
  private final class NamedInterceptor implements MethodInterceptor {
    private final String name;
    final List<String> called;
    
    NamedInterceptor(String name, List<String> callList) {
      this.name = name;
      this.called = callList;
    }
    
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      called.add(name);
      return methodInvocation.proceed();
    }
  }
  
  public void testDeDuplicateInterceptors() throws Exception {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        CountingInterceptor interceptor = new CountingInterceptor();
        bindInterceptor(Matchers.any(), Matchers.any(), interceptor);
        bindInterceptor(Matchers.any(), Matchers.any(), interceptor);
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    interceptable.foo();
    assertEquals(1, count.get());
  }

  public void testCallLater() {
    final Queue<Runnable> queue = Lists.newLinkedList();
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.any(), new CallLaterInterceptor(queue));
      }
    });

    Interceptable interceptable = injector.getInstance(Interceptable.class);
    interceptable.foo();
    assertNull(interceptable.lastElements);
    assertEquals(1, queue.size());

    queue.remove().run();
    assertNotNull(interceptable.lastElements);
  }

  private final class CallLaterInterceptor implements MethodInterceptor {
    private final Queue<Runnable> queue;

    public CallLaterInterceptor(Queue<Runnable> queue) {
      this.queue = queue;
    }

    public Object invoke(final MethodInvocation methodInvocation) throws Throwable {
      queue.add(new Runnable() {
        @Override
        public void run() {
          try {
            methodInvocation.proceed();
          } catch (Throwable t) {
            throw new RuntimeException(t);
          }
        }
      });
      return null;
    }
  }
}
