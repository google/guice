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

import com.google.inject.internal.Messages;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.inject.spi.Message;
import junit.framework.TestCase;
import org.junit.Test;

/**
 * Tests that ProvisionExceptions are readable and clearly indicate to the user what went wrong with
 * their code.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public class ProvisionExceptionsTest extends TestCase {

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
    try {
      injector.getInstance(Tracer.class);
      fail();
    } catch (ProvisionException pe) {
      // Make sure our initial error message gives the user exception.
      Asserts.assertContains(
          pe.getMessage(), "1) [Guice/ErrorInjectingConstructor]: IllegalStateException: boom!");
      assertEquals(1, pe.getErrorMessages().size());
      assertEquals(IllegalStateException.class, pe.getCause().getClass());
      assertEquals(
          IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
    }
  }

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
    try {
      injector.getInstance(Tracer.class);
      fail();
    } catch (ProvisionException pe) {
      // Make sure our initial error message gives the user exception.
      Asserts.assertContains(
          pe.getMessage(), "[Guice/ErrorInjectingConstructor]: IOException: boom!");
      assertEquals(1, pe.getErrorMessages().size());
      assertEquals(IOException.class, pe.getCause().getClass());
      assertEquals(IOException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
    }
  }

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
    try {
      injector.getInstance(Tracer.class);
      fail();
    } catch (ProvisionException pe) {
      // Make sure our initial error message gives the user exception.
      Asserts.assertContains(
          pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IllegalStateException: boom!");
      assertEquals(1, pe.getErrorMessages().size());
      assertEquals(IllegalStateException.class, pe.getCause().getClass());
      assertEquals(
          IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
    }
  }

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
    try {
      injector.getInstance(Tracer.class);
      fail();
    } catch (ProvisionException pe) {
      // Make sure our initial error message gives the user exception.
      Asserts.assertContains(
          pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IllegalStateException: boom!");
      assertEquals(1, pe.getErrorMessages().size());
      assertEquals(IllegalStateException.class, pe.getCause().getClass());
      assertEquals(
          IllegalStateException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
    }
  }

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
    try {
      injector.getInstance(Tracer.class);
      fail();
    } catch (ProvisionException pe) {
      pe.printStackTrace();
      Asserts.assertContains(
          pe.getMessage(), "1) [Guice/ErrorInCustomProvider]: IOException: boom!");
      assertEquals(1, pe.getErrorMessages().size());
      assertEquals(IOException.class, pe.getCause().getClass());
      assertEquals(IOException.class, Messages.getOnlyCause(pe.getErrorMessages()).getClass());
    }
  }

  @Test
  public void testProvisionExceptionWithMessageAndCause() {
    String errorMessage = "This is a test error message.";
    Throwable cause = new Throwable("This is a test cause.");
    ProvisionException provisionException = new ProvisionException(errorMessage, cause);
    Collection<Message> errorMessages = provisionException.getErrorMessages();
    assertNotNull(errorMessages);
    assertEquals(1, errorMessages.size());
    Message message = errorMessages.iterator().next();
    assertEquals(errorMessage, message.getMessage());
    assertEquals(cause, message.getCause());
  }

  @Test
  public void testProvisionExceptionWithMessage() {
    String errorMessage = "This is a test error message.";
    ProvisionException provisionException = new ProvisionException(errorMessage);
    Collection<Message> errorMessages = provisionException.getErrorMessages();
    assertNotNull(errorMessages);
    assertEquals(1, errorMessages.size());
    Message message = errorMessages.iterator().next();
    assertEquals(errorMessage, message.getMessage());
    assertEquals(null, message.getCause());
  }

  @Test
  public void testProvisionExceptionGetMessage() {
    List<Message> messages = new ArrayList<>();
    messages.add(new Message("Error 1"));
    messages.add(new Message("Error 2"));
    ProvisionException provisionException = new ProvisionException(messages);
    String expectedMessage = "Unable to provision, see the following errors:\n\n1) Error 1\n\n2) Error 2\n\n2 errors";
    assertEquals(expectedMessage, provisionException.getMessage());
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
