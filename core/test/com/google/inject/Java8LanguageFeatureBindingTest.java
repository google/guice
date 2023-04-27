/*
 * Copyright (C) 2014 Google Inc.
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Test bindings to lambdas, method references, etc.
 *
 * @author cgdecker@google.com (Colin Decker)
 */
public class Java8LanguageFeatureBindingTest {

  // Some of these tests are kind of weird.
  // See https://github.com/google/guice/issues/757 for more on why they exist.

  @Test
  public void testBinding_lambdaToInterface() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(new TypeLiteral<Predicate<Object>>() {}).toInstance(o -> o != null);
              }
            });

    Predicate<Object> predicate = injector.getInstance(new Key<Predicate<Object>>() {});
    assertTrue(predicate.test(new Object()));
    assertFalse(predicate.test(null));
  }

  @Test
  public void testProviderMethod_returningLambda() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              @Provides
              public Callable<String> provideCallable() {
                return () -> "foo";
              }
            });

    Callable<String> callable = injector.getInstance(new Key<Callable<String>>() {});
    assertEquals("foo", callable.call());
  }

  @Test
  public void testProviderMethod_containingLambda_throwingException() throws Exception {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              @Provides
              public Callable<String> provideCallable() {
                if (Boolean.parseBoolean("false")) { // avoid dead code warnings
                  return () -> "foo";
                } else {
                  throw new RuntimeException("foo");
                }
              }
            });

    ProvisionException expected =
        assertThrows(
            ProvisionException.class, () -> injector.getInstance(new Key<Callable<String>>() {}));
    assertTrue(expected.getCause() instanceof RuntimeException);
    assertEquals("foo", expected.getCause().getMessage());
  }

  @Test
  public void testProvider_usingJdk8Features() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(String.class).toProvider(StringProvider.class);
            }
          });

      fail();
    } catch (CreationException expected) {
    }

    UUID uuid = UUID.randomUUID();
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(UUID.class).toInstance(uuid);
                bind(String.class).toProvider(StringProvider.class);
              }
            });

    assertEquals(uuid.toString(), injector.getInstance(String.class));
  }

  private static final class StringProvider implements Provider<String> {
    private final UUID uuid;

    @Inject
    StringProvider(UUID uuid) {
      this.uuid = uuid;
    }

    @Override
    public String get() {
      return Collections.singleton(uuid).stream().map(UUID::toString).findFirst().get();
    }
  }

  @Test
  public void testBinding_toProvider_lambda() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                AtomicInteger i = new AtomicInteger();
                bind(String.class).toProvider(() -> "Hello" + i.incrementAndGet());
              }
            });

    assertEquals("Hello1", injector.getInstance(String.class));
    assertEquals("Hello2", injector.getInstance(String.class));
  }

  @Test
  public void testBinding_toProvider_methodReference() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toProvider(Java8LanguageFeatureBindingTest.this::provideString);
              }
            });

    Provider<String> provider = injector.getProvider(String.class);
    assertEquals("Hello", provider.get());
  }

  private String provideString() {
    return "Hello";
  }
}
