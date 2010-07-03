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

package com.google.inject;

import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.util.Modules;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class GenericInjectionTest extends TestCase {

  public void testGenericInjection() throws CreationException {
    final List<String> names = Arrays.asList("foo", "bar", "bob");

    Injector injector = Guice.createInjector((Module) new AbstractModule() {
      protected void configure() {
        bind(new TypeLiteral<List<String>>() {}).toInstance(names);
      }
    });

    Foo foo = injector.getInstance(Foo.class);
    assertEquals(names, foo.names);
  }

  static class Foo {
    @Inject List<String> names;
  }

  /**
   * Although we may not have intended to support this behaviour, this test
   * passes under Guice 1.0. The workaround is to add an explicit binding for
   * the parameterized type. See {@link #testExplicitBindingOfGenericType()}.
   */
  public void testImplicitBindingOfGenericType() {
    Parameterized<String> parameterized
        = Guice.createInjector().getInstance(Key.get(new TypeLiteral<Parameterized<String>>() {}));
    assertNotNull(parameterized);
  }

  public void testExplicitBindingOfGenericType() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Key.get(new TypeLiteral<Parameterized<String>>() {}))
            .to((Class) Parameterized.class);
      }
    });

    Parameterized<String> parameterized
        = injector.getInstance(Key.get(new TypeLiteral<Parameterized<String>>() { }));
    assertNotNull(parameterized);
  }

  static class Parameterized<T> {
    @Inject
    Parameterized() { }
  }

  public void testInjectingParameterizedDependenciesForImplicitBinding() {
    assertParameterizedDepsInjected(new Key<ParameterizedDeps<String, Integer>>() {},
        Modules.EMPTY_MODULE);
  }

  public void testInjectingParameterizedDependenciesForBindingTarget() {
    final TypeLiteral<ParameterizedDeps<String, Integer>> type
        = new TypeLiteral<ParameterizedDeps<String, Integer>>() {};

    assertParameterizedDepsInjected(Key.get(Object.class), new AbstractModule() {
      protected void configure() {
        bind(Object.class).to(type);
      }
    });
  }

  public void testInjectingParameterizedDependenciesForBindingSource() {
    final TypeLiteral<ParameterizedDeps<String, Integer>> type
        = new TypeLiteral<ParameterizedDeps<String, Integer>>() {};

    assertParameterizedDepsInjected(Key.get(type), new AbstractModule() {
      protected void configure() {
        bind(type);
      }
    });
  }

  public void testBindingToSubtype() {
    final TypeLiteral<ParameterizedDeps<String, Integer>> type
        = new TypeLiteral<ParameterizedDeps<String, Integer>>() {};

    assertParameterizedDepsInjected(Key.get(type), new AbstractModule() {
      protected void configure() {
        bind(type).to(new TypeLiteral<SubParameterizedDeps<String, Long, Integer>>() {});
      }
    });
  }

  public void testBindingSubtype() {
    final TypeLiteral<SubParameterizedDeps<String, Long, Integer>> type
        = new TypeLiteral<SubParameterizedDeps<String, Long, Integer>>() {};

    assertParameterizedDepsInjected(Key.get(type), new AbstractModule() {
      protected void configure() {
        bind(type);
      }
    });
  }

  @SuppressWarnings("unchecked")
  public void assertParameterizedDepsInjected(Key<?> key, Module bindingModule) {
    Module bindDataModule = new AbstractModule() {
      protected void configure() {}
      @Provides Map<String, Integer> provideMap() {
        return ImmutableMap.of("one", 1, "two", 2);
      }
      @Provides Set<String> provideSet(Map<String, Integer> map) {
        return map.keySet();
      }
      @Provides Collection<Integer> provideCollection(Map<String, Integer> map) {
        return map.values();
      }
    };

    Injector injector = Guice.createInjector(bindDataModule, bindingModule);
    ParameterizedDeps<String, Integer> parameterizedDeps
        = (ParameterizedDeps<String, Integer>) injector.getInstance(key);
    assertEquals(ImmutableMap.of("one", 1, "two", 2), parameterizedDeps.map);
    assertEquals(ImmutableSet.of("one", "two"), parameterizedDeps.keys);
    assertEquals(ImmutableSet.of(1, 2), ImmutableSet.copyOf(parameterizedDeps.values));
  }

  static class SubParameterizedDeps<A, B, C> extends ParameterizedDeps<A, C> {
    @Inject SubParameterizedDeps(Set<A> keys) {
      super(keys);
    }
  }

  static class ParameterizedDeps<K, V> {
    @Inject private Map<K, V> map;
    private Set<K> keys;
    private Collection<V> values;

    @Inject ParameterizedDeps(Set<K> keys) {
      this.keys = keys;
    }

    @Inject void method(Collection<V> values) {
      this.values = values;
    }
  }

  public void testImmediateTypeVariablesAreInjected() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("tee");
      }
    });
    InjectsT<String> injectsT = injector.getInstance(new Key<InjectsT<String>>() {});
    assertEquals("tee", injectsT.t);
  }

  static class InjectsT<T> {
    @Inject T t;
  }
}
