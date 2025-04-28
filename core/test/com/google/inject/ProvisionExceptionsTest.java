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

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.name.Names.named;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Iterables;
import com.google.inject.internal.Messages;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
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
public class ProvisionExceptionsTest {

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
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Tracer.class));

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
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Tracer.class));
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
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Tracer.class));

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
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Tracer.class));
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
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Tracer.class));
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
    @Inject
    MethodErrorer() {}

    @Inject
    void injectErrorer() {
      throw new OutOfMemoryError("uh oh");
    }
  }

  static final class MethodDependencyErrorer {
    @Inject
    MethodDependencyErrorer() {}

    @Inject
    void injectErrorer(@Named("providerInstance") String s) {}
  }

  static final class FieldDependencyErrorer {
    @Inject
    FieldDependencyErrorer() {}

    @Inject
    @Named("providerInstance")
    String s;
  }

  static final class ProviderErrorer implements Provider<String> {
    private final String s;

    @Inject
    ProviderErrorer(@Named("providerInstance") String s) {
      this.s = s;
    }

    @Override
    public String get() {
      return s;
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
                bind(Key.get(String.class, named("providerClass")))
                    .toProvider(ProviderErrorer.class);
                bind(Key.get(String.class, named("providerInstance")))
                    .toProvider(
                        () -> {
                          throw new OutOfMemoryError("uh oh");
                        });
                bind(Errorer.class);
                bind(MethodErrorer.class);
                bind(MethodDependencyErrorer.class);
                bind(FieldDependencyErrorer.class);
              }

              @Provides
              @Named("provides")
              String provideString() {
                throw new OutOfMemoryError("uh oh");
              }

              @Provides
              @Named("providesFromDependency")
              String provideString(@Named("providerInstance") String s) {
                return s;
              }
            });
    assertThrows(ProvisionException.class, () -> injector.getInstance(Errorer.class));
    assertThrows(ProvisionException.class, () -> injector.getInstance(MethodErrorer.class));
    assertThrows(OutOfMemoryError.class, () -> injector.getInstance(MethodDependencyErrorer.class));
    assertThrows(OutOfMemoryError.class, () -> injector.getInstance(FieldDependencyErrorer.class));
    assertThrows(
        OutOfMemoryError.class,
        () -> injector.getInstance(Key.get(String.class, named("providerInstance"))));
    assertThrows(
        ProvisionException.class,
        () -> injector.getInstance(Key.get(String.class, named("provides"))));
    assertThrows(
        ProvisionException.class,
        () -> injector.getInstance(Key.get(String.class, named("providesFromDependency"))));
    assertThrows(
        OutOfMemoryError.class,
        () -> injector.getInstance(Key.get(String.class, named("providerClass"))));
  }

  static final class DependsOnFailingProvider {
    @Inject
    DependsOnFailingProvider(String failing) {
      throw new AssertionError("unreachable");
    }
  }

  /**
   * Demonstrate that the order of `sources` is different depending on what kind of binding fails.
   *
   * <p>`@Provides` methods add the method to the source stack if a dependency fails, but `@Inject`
   * constructors do not.
   */
  @Test
  public void testDependencyFailingTrace() throws NoSuchMethodException {
    var module =
        new AbstractModule() {

          @Provides
          String provideFailingValue() {
            throw new RuntimeException("boom");
          }

          @Provides
          Object providesDependsOnFailingProvider(String failing) {
            throw new AssertionError("unreachable");
          }
        };
    Injector injector = Guice.createInjector(module);
    var provideFailingValue = injector.getBinding(String.class).getSource();
    var providesDependsOnFailingProvider = injector.getBinding(Object.class).getSource();
    var providesDependensOnFailingProvider_failing =
        Iterables.getOnlyElement(
            ((HasDependencies) injector.getBinding(Object.class)).getDependencies());
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(Object.class));
    var message = Iterables.getOnlyElement(pe.getErrorMessages()).getErrorDetail();
    assertThat(message.getCause()).hasMessageThat().isEqualTo("boom");
    assertThat(message.getSources())
        .containsExactly(
            Dependency.get(Key.get(Object.class)), // what we asked for
            providesDependsOnFailingProvider, // the provider method
            providesDependensOnFailingProvider_failing, // the dependency of the provider method
            provideFailingValue); // the thing that failed

    var dependsOnFailingProvider_failing =
        Iterables.getOnlyElement(
            ((HasDependencies) injector.getBinding(DependsOnFailingProvider.class))
                .getDependencies());
    pe =
        assertThrows(
            ProvisionException.class, () -> injector.getInstance(DependsOnFailingProvider.class));
    message = Iterables.getOnlyElement(pe.getErrorMessages()).getErrorDetail();
    assertThat(message.getCause()).hasMessageThat().isEqualTo("boom");
    assertThat(message.getSources())
        .containsExactly(
            Dependency.get(Key.get(DependsOnFailingProvider.class)), // what we asked for
            dependsOnFailingProvider_failing, // the dependency of the constructor
            provideFailingValue); // the thing that failed.
  }

  @Test
  @SuppressWarnings({"rawtypes", "unchecked"}) // Test requires incorrect types
  public void testConstructorBindingDependencyHasWrongType() {
    var module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class)
                .toProvider(
                    new Provider() {
                      @Override
                      public Integer get() {
                        return 1;
                      }
                    });
          }
        };
    Injector injector = Guice.createInjector(module);
    var pe = assertThrows(ProvisionException.class, () -> injector.getInstance(WantsString.class));
    assertThat(pe).hasCauseThat().isInstanceOf(ClassCastException.class);
  }

  private static class WantsString {
    @Inject
    WantsString(String s) {}
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
