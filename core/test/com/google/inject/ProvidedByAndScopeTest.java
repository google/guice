/*
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

import static org.junit.Assert.assertSame;

import org.junit.Test;

/** @author bn0010100@gmail.com (Neil) */
public class ProvidedByAndScopeTest {

  @Test
  public void testMixClass() {
    final Injector injector = Guice.createInjector();
    final MixClass i1 = injector.getInstance(MixClass.class);
    final MixClass i2 = injector.getInstance(MixClass.class);
    assertSame(i1, i2);
  }

  @Test
  public void testMixWithScope() {
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(MixClass.class).in(Scopes.SINGLETON);
              }
            });

    final MixClass i1 = injector.getInstance(MixClass.class);
    final MixClass i2 = injector.getInstance(MixClass.class);
    assertSame(i1, i2);
  }

  @Singleton
  @ProvidedBy(MixClassProvider.class)
  static class MixClass {}

  /* static because only support inject to static inner class */
  static class MixClassProvider implements Provider<MixClass> {
    public MixClass get() {
      return new MixClass();
    }
  }
}
