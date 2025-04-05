/*
 * Copyright (C) 2010 Google Inc.
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

import static com.google.inject.name.Names.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.inject.internal.Messages;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests that ProvisionExceptions are readable and clearly indicate to the user what went wrong with
 * their code.
 *
 * @author sameb@google.com (Sam Berlin)
 */
@RunWith(JUnit4.class)
public class ProvisionExceptionsTest{

  @Test
  public void testConstructorRuntimeException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(Names.named("runtime")).to(true);
                bind(Exploder.class).to(Explosion.class);
                bind(Tracer.class).to(TracerImpl.class);
              }
            });
    var pe =
        assertThrows(
            ProvisionException.class,
            () -> {
              injector.getInstance(Tracer.class);
            });

    // Make sure our initial error message gives the user exception.
    Asserts.assertContains(
        pe.getMessage(), "1) [Guice/ErrorInjectingConstructor]: IllegalStateException: boom!");
    assertEquals(1, pe.getErrorMessages().size());
    assertEquals(IllegalStateException.class, pe.getCause().getClass());
    assertEquals(
        IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
  }

  @Test
  public void testConstructorCheckedException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindConstant().annotatedWith(Names.named("runtime")).to(false);
                bind(Exploder.class).to(Explosion.class);
                bind(Tracer.class).to(TracerImpl.class);
              }
            });
    var pe =
        assertThrows(
            ProvisionException.class,
            () -> {
              injector.getInstance(Tracer.class);
            });
    // Make sure our initial error message gives the user exception.
    Asserts.assertContains(
        pe.getMessage(), "[Guice/ErrorInjectingConstructor]: IOException: boom!");
    assertEquals(1, pe.getErrorMessages().size());
    assertEquals(IOException.class, pe.getCause().getClass());
    assertEquals(IOException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
  }

  @Test
  public void testCustomProvidersRuntimeException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Exploder.class)
                    .toProvider(
                        new Provider<Exploder>() {
                          @Override
                          public Exploder get() {
                            return Explosion.createRuntime();
                          }
                        });
                bind(Tracer.class).to(TracerImpl.class);
              }
            });
    var pe =
        assertThrows(
            ProvisionException.class,
            () -> {
              injector.getInstance(Tracer.class);
            });

    // Make sure our initial error message gives the user exception.
    Asserts.assertContains(
        pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IllegalStateException: boom!");
    assertEquals(1, pe.getErrorMessages().size());
    assertEquals(IllegalStateException.class, pe.getCause().getClass());
    assertEquals(
        IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
  }

  @Test
  public void testProviderMethodRuntimeException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Tracer.class).to(TracerImpl.class);
              }

              @Provides
              Exploder exploder() {
                return Explosion.createRuntime();
              }
            });
    var pe =
        assertThrows(
            ProvisionException.class,
            () -> {
              injector.getInstance(Tracer.class);
            });
    // Make sure our initial error message gives the user exception.
    Asserts.assertContains(
        pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IllegalStateException: boom!");
    assertEquals(1, pe.getErrorMessages().size());
    assertEquals(IllegalStateException.class, pe.getCause().getClass());
    assertEquals(
        IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
  }

  @Test
  public void testProviderMethodCheckedException() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Tracer.class).to(TracerImpl.class);
              }

              @Provides
              Exploder exploder() throws IOException {
                return Explosion.createChecked();
              }
            });
    var pe =
        assertThrows(
            ProvisionException.class,
            () -> {
              injector.getInstance(Tracer.class);
            });
    pe.printStackTrace();
    // Make sure our initial error message gives the user exception.
    Asserts.assertContains(pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IOException: boom!");
    assertEquals(1, pe.getErrorMessages().size());
    assertEquals(IOException.class, pe.getCause().getClass());
    assertEquals(IOException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
  }

  static final class Errorer {
    @Inject
    Errorer() {
      throw new OutOfMemoryError("uh oh");
    }
  }
  static final class MethodErrorer {
    @Inject MethodErrorer() {}
    @Inject void injectErrorer() {
      throw new OutOfMemoryError("uh oh");
    }
  }

  // Demonstrate that different bindings do and do not wrap Errors thrown.
  // Constructor and method injection does since the reflection API does it for us via
  // InvocationTargetException.
  // Provider instances do not since we have our own catch clauses and only catch RuntimeException
  @Test
  public void testErrorPropagation() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {

              @Override
              protected void configure() {
                bind(Key.get(String.class, named("providerInstance")))
                    .toProvider(
                        () -> {
                          throw new OutOfMemoryError("uh oh");
                        });
                bind(Errorer.class);
                bind(MethodErrorer.class);
              }

              @Provides
              @Named("provides")
              String provideString() {
                throw new OutOfMemoryError("uh oh");
              }
            });
    assertThrows(ProvisionException.class, () -> injector.getInstance(Errorer.class));
    assertThrows(ProvisionException.class, () -> injector.getInstance(MethodErrorer.class));
    assertThrows(
        OutOfMemoryError.class,
        () -> injector.getInstance(Key.get(String.class, named("providerInstance"))));
    assertThrows(
        ProvisionException.class,
        () -> injector.getInstance(Key.get(String.class, named("provides"))));
  }

  private static interface Exploder {}

  public static class Explosion implements Exploder {
    @Inject
    public Explosion(@Named("runtime") boolean runtime) throws IOException {
      if (runtime) {
        throw new IllegalStateException("boom!");
      } else {
        throw new IOException("boom!");
      }
    }

    public static Explosion createRuntime() {
      try {
        return new Explosion(true);
      } catch (IOException iox) {
        throw new RuntimeException();
      }
    }

    public static Explosion createChecked() throws IOException {
      return new Explosion(false);
    }
  }

  private static interface Tracer {}

  private static class TracerImpl implements Tracer {
    @Inject
    TracerImpl(Exploder explosion) {}
  }
}
