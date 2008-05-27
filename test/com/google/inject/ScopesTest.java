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

import junit.framework.TestCase;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ScopesTest extends TestCase {

  public void testSingletonAnnotation() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(AnnotatedSingleton.class);
      }
    });

    assertSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));
  }

  public void testBoundAsSingleton() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(BoundAsSingleton.class).in(Scopes.SINGLETON);
      }
    });

    assertSame(
        injector.getInstance(BoundAsSingleton.class),
        injector.getInstance(BoundAsSingleton.class));
  }

  public void testJustInTimeAnnotatedSingleton() {
    Injector injector = Guice.createInjector();

    assertSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));
  }

  public void testSingletonIsPerInjector() {
    assertNotSame(
        Guice.createInjector().getInstance(AnnotatedSingleton.class),
        Guice.createInjector().getInstance(AnnotatedSingleton.class));
  }

  public void testOverriddingAnnotation() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(AnnotatedSingleton.class).in(Scopes.NO_SCOPE);
      }
    });

    assertNotSame(
        injector.getInstance(AnnotatedSingleton.class),
        injector.getInstance(AnnotatedSingleton.class));
  }

  public void testAnnotatedSingletonsInProductionAreEager() {
    int nextInstanceId = AnnotatedSingleton.nextInstanceId.intValue();
    
    Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      protected void configure() {
        bind(AnnotatedSingleton.class);
      }
    });

    assertEquals(nextInstanceId + 1, AnnotatedSingleton.nextInstanceId.intValue());
  }

  public void testAnnotatedSingletonsInDevelopmentAreNotEager() {
    int nextInstanceId = AnnotatedSingleton.nextInstanceId.intValue();

    Guice.createInjector(Stage.DEVELOPMENT, new AbstractModule() {
      protected void configure() {
        bind(AnnotatedSingleton.class);
      }
    });

    assertEquals(nextInstanceId, AnnotatedSingleton.nextInstanceId.intValue());
  }

  public void testBoundAsSingletonInProductionAreEager() {
    int nextInstanceId = BoundAsSingleton.nextInstanceId.intValue();

    Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      protected void configure() {
        bind(BoundAsSingleton.class).in(Scopes.SINGLETON);
      }
    });

    assertEquals(nextInstanceId + 1, BoundAsSingleton.nextInstanceId.intValue());
  }

  public void testBoundAsSingletonInDevelopmentAreNotEager() {
    int nextInstanceId = BoundAsSingleton.nextInstanceId.intValue();

    Guice.createInjector(Stage.DEVELOPMENT, new AbstractModule() {
      protected void configure() {
        bind(BoundAsSingleton.class).in(Scopes.SINGLETON);
      }
    });

    assertEquals(nextInstanceId, BoundAsSingleton.nextInstanceId.intValue());
  }

  public void testSingletonScopeIsNotSerializable() throws IOException {
    Asserts.assertNotSerializable(Scopes.SINGLETON);
  }

  public void testNoScopeIsNotSerializable() throws IOException {
    Asserts.assertNotSerializable(Scopes.NO_SCOPE);
  }

  @Singleton
  static class AnnotatedSingleton {
    static final AtomicInteger nextInstanceId = new AtomicInteger(1);
    final int instanceId = nextInstanceId.getAndIncrement();
  }

  static class BoundAsSingleton {
    static final AtomicInteger nextInstanceId = new AtomicInteger(1);
    final int instanceId = nextInstanceId.getAndIncrement();
  }
}
