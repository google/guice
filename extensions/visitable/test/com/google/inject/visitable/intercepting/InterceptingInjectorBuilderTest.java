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

package com.google.inject.visitable.intercepting;

import com.google.inject.*;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;


/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class InterceptingInjectorBuilderTest extends TestCase {

  private final ProvisionInterceptor failingInterceptor = new ProvisionInterceptor() {
    public <T> T intercept(Key<T> key, Provider<? extends T> delegate) {
      throw new AssertionFailedError();
    }
  };

  public void testInterceptProvisionInterceptor() {
    InterceptingInjectorBuilder builder = new InterceptingInjectorBuilder();

    try {
      builder.intercept(ProvisionInterceptor.class);
      fail();
    } catch(IllegalArgumentException expected) {
    }
  }

  public void testProvisionInterception() {
    final ProvisionInterceptor interceptor = new ProvisionInterceptor() {
      @SuppressWarnings({"unchecked"})
      public <T> T intercept(Key<T> key, Provider<? extends T> delegate) {
        assertEquals(Key.get(String.class), key);
        assertEquals("A", delegate.get());
        return (T) "B";
      }
    };

    Module module = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(ProvisionInterceptor.class).toInstance(interceptor);
      }
    };

    Injector injector = new InterceptingInjectorBuilder()
        .intercept(String.class)
        .bindModules(module)
        .build();

    assertEquals("B", injector.getInstance(String.class));
  }

  /**
   * The user's provider is scoped but the interceptor is not. As this testcase
   * demonstrates, the user's provider gets called only once (in singleton
   * scope) but the interceptor gets called for each provision.
   */
  public void testInterceptionIsNotScoped() {
    final Provider<Integer> sequenceProvider = new Provider<Integer>() {
      private int next = 100;
      public Integer get() {
        return next++;
      }
    };

    final ProvisionInterceptor interceptor = new ProvisionInterceptor() {
      private int next = 1;
      @SuppressWarnings({"unchecked"})
      public <T> T intercept(Key<T> key, Provider<? extends T> delegate) {
        assertEquals(100, delegate.get());
        return (T) new Integer(next++);
      }
    };

    Module module = new AbstractModule() {
      protected void configure() {
        bind(Integer.class).toProvider(sequenceProvider).in(Scopes.SINGLETON);
        bind(ProvisionInterceptor.class).toInstance(interceptor);
      }
    };

    Injector injector = new InterceptingInjectorBuilder()
        .intercept(Integer.class)
        .bindModules(module)
        .build();

    assertEquals(1, (int) injector.getInstance(Integer.class));
    assertEquals(2, (int) injector.getInstance(Integer.class));
  }

  public void testInterceptionIsWhitelistedKeysOnly() {
    final ProvisionInterceptor interceptor = new ProvisionInterceptor() {
      @SuppressWarnings({"unchecked"})
      public <T> T intercept(Key<T> key, Provider<? extends T> delegate) {
        assertEquals(ArrayList.class, delegate.get().getClass());
        return (T) new LinkedList();
      }
    };

    Module module = new AbstractModule() {
      protected void configure() {
        bind(Collection.class).to(ArrayList.class);
        bind(List.class).to(ArrayList.class);
        bind(ProvisionInterceptor.class).toInstance(interceptor);
      }
    };

    Injector injector = new InterceptingInjectorBuilder()
        .intercept(List.class)
        .bindModules(module)
        .build();

    assertEquals(LinkedList.class, injector.getInstance(List.class).getClass());
    assertEquals(ArrayList.class, injector.getInstance(Collection.class).getClass());
  }
  
  public void testCannotInterceptBareBinding() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(ArrayList.class);
      }
    };

    InterceptingInjectorBuilder builder = new InterceptingInjectorBuilder()
        .intercept(ArrayList.class)
        .bindModules(module);

    try {
      builder.build();
      fail();
    } catch(UnsupportedOperationException expected) {
    }
  }

  public void testAllInterceptedKeysMustBeBound() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(ProvisionInterceptor.class).toInstance(failingInterceptor);
      }
    };

    InterceptingInjectorBuilder builder = new InterceptingInjectorBuilder()
        .intercept(ArrayList.class)
        .bindModules(module);

    try {
      builder.build();
      fail();
    } catch(IllegalArgumentException expected) {
    }
  }

  public void testTolerateUnmatchedInterceptions() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(ProvisionInterceptor.class).toInstance(failingInterceptor);
      }
    };

    Injector injector = new InterceptingInjectorBuilder()
        .intercept(ArrayList.class)
        .tolerateUnmatchedInterceptions()
        .bindModules(module)
        .build();

    assertEquals(new ArrayList(), injector.getInstance(ArrayList.class));
  }

}
