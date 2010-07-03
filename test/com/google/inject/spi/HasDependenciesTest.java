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

package com.google.inject.spi;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Iterables;
import java.util.Set;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class HasDependenciesTest extends TestCase {

  /**
   * When an instance implements HasDependencies, the injected dependencies aren't used.
   */
  public void testInstanceWithDependencies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).toInstance(new AWithDependencies());
      }
    });

    InstanceBinding<?> binding = (InstanceBinding<?>) injector.getBinding(A.class);
    assertEquals(ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Integer.class))),
        binding.getDependencies());
  }

  public void testInstanceWithoutDependencies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).toInstance(new A());
      }
    });

    InstanceBinding<?> binding = (InstanceBinding<?>) injector.getBinding(A.class);
    Dependency<?> onlyDependency = Iterables.getOnlyElement(binding.getDependencies());
    assertEquals(Key.get(String.class), onlyDependency.getKey());
  }

  public void testProvider() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(A.class).toProvider(new ProviderOfA());
      }
    });

    ProviderInstanceBinding<?> binding = (ProviderInstanceBinding<?>) injector.getBinding(A.class);
    Dependency<?> onlyDependency = Iterables.getOnlyElement(binding.getDependencies());
    assertEquals(Key.get(String.class), onlyDependency.getKey());
  }

  static class A {
    @Inject void injectUnusedDependencies(String unused) {}
  }

  static class ProviderOfA implements Provider<A> {
    @Inject void injectUnusedDependencies(String unused) {}

    public A get() {
      throw new UnsupportedOperationException();
    }
  }

  static class AWithDependencies extends A implements HasDependencies {
    public Set<Dependency<?>> getDependencies() {
      return ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Integer.class)));
    }
  }

  static class ProviderOfAWithDependencies
      extends ProviderOfA implements ProviderWithDependencies<A> {
    public Set<Dependency<?>> getDependencies() {
      return ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Integer.class)));
    }
  }
}
