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

package com.google.inject;

import com.google.inject.internal.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

public class GraphScopeTest extends TestCase {

  static final AtomicInteger nextId = new AtomicInteger();

  public void test() {
    final GraphScope graphScope = new GraphScope();

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).in(graphScope);
        bind(B.class).in(graphScope);
        bind(C.class).in(graphScope);
      }
    });

    graphScope.beginNewGraph();
    A graphOne = injector.getInstance(A.class);
    graphScope.endCurrentGraph();

    graphScope.beginNewGraph();
    A graphTwo = injector.getInstance(A.class);
    graphScope.endCurrentGraph();

    assertSame(graphOne.b.c , graphOne.b.cProvider.get());
    assertSame(graphTwo.b.c , graphTwo.b.cProvider.get());
    assertNotSame(graphOne.b.c, graphTwo.b.c);
  }


  static class A {
    @Inject B b;
    int name = nextId.incrementAndGet();
  }

  static class B {
    @Inject C c;
    @Inject Provider<C> cProvider;
    int name = nextId.incrementAndGet();
  }

  static class C {
    int name = nextId.incrementAndGet();
  }



  public class GraphScope implements Scope {

    ThreadLocal<List<Integer>> ids = new ThreadLocal<List<Integer>>() {
      @Override protected List<Integer> initialValue() {
        return new ArrayList<Integer>();
      }
    };

    void beginNewGraph() {
      ids.get().add(nextId.incrementAndGet());
    }

    void endCurrentGraph() {
      List<Integer> ids = this.ids.get();
      ids.remove(ids.size() - 1);
    }


    public <T> Provider<T> scope(Key<T> key, final Provider<T> unscoped) {

      final Map<List<Integer>, Object> cache = new ConcurrentHashMap<List<Integer>, Object>();
      final List<Integer> whatIsMyGraph = ImmutableList.copyOf(ids.get());

      return new Provider<T>() {
        public T get() {
          Object alreadyCached = cache.get(whatIsMyGraph);
          if (alreadyCached != null) {
            return (T) alreadyCached;
          } else {
            T created = unscoped.get();
            cache.put(whatIsMyGraph, created);
            return created;
          }
        }
      };
    }
  }
}
