/*
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

import com.google.common.collect.ImmutableList;
import com.google.inject.spi.ElementSource;
import com.google.inject.util.Modules;
import java.util.Arrays;
import junit.framework.TestCase;

/** @author jessewilson@google.com (Jesse Wilson) */
public class ModulesTest extends TestCase {

  public void testCombineVarargs() {
    Module combined = Modules.combine(newModule(1), newModule(2L), newModule((short) 3));
    Injector injector = Guice.createInjector(combined);
    assertEquals(1, injector.getInstance(Integer.class).intValue());
    assertEquals(2L, injector.getInstance(Long.class).longValue());
    assertEquals(3, injector.getInstance(Short.class).shortValue());
  }

  public void testCombineIterable() {
    Iterable<Module> modules = Arrays.asList(newModule(1), newModule(2L), newModule((short) 3));
    Injector injector = Guice.createInjector(Modules.combine(modules));
    assertEquals(1, injector.getInstance(Integer.class).intValue());
    assertEquals(2, injector.getInstance(Long.class).longValue());
    assertEquals(3, injector.getInstance(Short.class).shortValue());
  }

  /** The module returned by Modules.combine shouldn't show up in binder sources. */
  public void testCombineSources() {
    final Module m1 = newModule(1);
    final Module m2 = newModule(2L);
    final Module combined1 = Modules.combine(m1, m2);
    Module skipSourcesModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            install(combined1);
          }
        };
    final Module combined2 = Modules.combine(skipSourcesModule); // returns skipSourcesModule
    Injector injector = Guice.createInjector(combined2);
    ElementSource source = (ElementSource) injector.getBinding(Integer.class).getSource();
    assertEquals(3, source.getModuleClassNames().size());
    assertEquals(
        ImmutableList.of(
            m1.getClass().getName(),
            combined1.getClass().getName(),
            skipSourcesModule.getClass().getName()),
        source.getModuleClassNames());
    StackTraceElement stackTraceElement = (StackTraceElement) source.getDeclaringSource();
    assertEquals(skipSourcesModule.getClass().getName(), stackTraceElement.getClassName());
  }

  private <T> Module newModule(final T toBind) {
    return new AbstractModule() {
      @Override
      protected void configure() {
        @SuppressWarnings("unchecked") // getClass always needs a cast
        Class<T> tClass = (Class<T>) toBind.getClass();
        binder().skipSources(getClass()).bind(tClass).toInstance(toBind);
      }
    };
  }
}
