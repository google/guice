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

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.matcher.Matchers.any;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.inject.internal.InternalFlags;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** @author crazybob@google.com (Bob Lee) */
@RunWith(JUnit4.class)
public class IntegrationTest {

  @Test
  public void testIntegration() throws CreationException {
    final CountingInterceptor counter = new CountingInterceptor();

    Module module =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Foo.class);
            bindInterceptor(any(), any(), counter);
          }
        };
    if (InternalFlags.isBytecodeGenEnabled()) {
      Injector injector = Guice.createInjector(module);

      Foo foo = injector.getInstance(Key.get(Foo.class));
      foo.foo();
      assertTrue(foo.invoked);
      assertEquals(1, counter.count);

      foo = injector.getInstance(Foo.class);
      foo.foo();
      assertTrue(foo.invoked);
      assertEquals(2, counter.count);
    } else {
      CreationException exception =
          assertThrows(CreationException.class, () -> Guice.createInjector(module));
      assertThat(exception)
          .hasMessageThat()
          .contains("Binding interceptor is not supported when bytecode generation is disabled.");
    }
  }

  public static class Foo {
    boolean invoked;

    public void foo() {
      invoked = true;
    }
  }

  static class CountingInterceptor implements MethodInterceptor {

    int count;

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Throwable {
      count++;
      return methodInvocation.proceed();
    }
  }
}
