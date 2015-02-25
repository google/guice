/**
 * Copyright (C) 2015 Google Inc.
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
package com.google.inject.daggeradapter;

import static dagger.Provides.Type.SET;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Providers;

import junit.framework.TestCase;

import java.util.Set;

/**
 * Tests for {@link DaggerAdapter}.
 *
 * @author cgruber@google.com (Christian Gruber)
 */

public class DaggerAdapterTest extends TestCase {
  @dagger.Module static class SimpleDaggerModule {
    @dagger.Provides Integer anInteger() {
      return 1;
    }
  }

  public void testSimpleModule() {
    Injector i = Guice.createInjector(DaggerAdapter.from(new SimpleDaggerModule()));
    assertEquals((Integer) 1, i.getInstance(Integer.class));
  }

  static class SimpleGuiceModule extends AbstractModule {
    @Provides String aString(Integer i) {
      return i.toString();
    }
    @Override protected void configure() {}
  }

  public void testInteractionWithGuiceModules() {
     Injector i = Guice.createInjector(
         new SimpleGuiceModule(),
         DaggerAdapter.from(new SimpleDaggerModule()));
     assertEquals("1", i.getInstance(String.class));
  }

  @dagger.Module static class SetBindingDaggerModule1 {
    @dagger.Provides(type=SET) Integer anInteger() {
      return 5;
    }
  }

  @dagger.Module static class SetBindingDaggerModule2 {
    @dagger.Provides(type=SET) Integer anInteger() {
      return 3;
    }
  }

  public void testSetBindings() {
    Injector i = Guice.createInjector(
        DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(3, 5), i.getInstance(new Key<Set<Integer>>() {}));
  }

  static class MultibindingGuiceModule implements Module {
    @Override public void configure(Binder binder) {
      Multibinder<Integer> mb = Multibinder.newSetBinder(binder, Integer.class);
      mb.addBinding().toInstance(13);
      mb.addBinding().toProvider(Providers.of(8)); // mix'n'match.
    }
  }

  public void testSetBindingsWithGuiceModule() {
    Injector i = Guice.createInjector(
        new MultibindingGuiceModule(),
        DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(13, 3, 5, 8), i.getInstance(new Key<Set<Integer>>() {}));
  }
}
