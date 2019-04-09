/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.inject.servlet;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provides;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import junit.framework.TestCase;

// TODO: Add test for HTTP transferring.
/** Tests transferring of entire request scope. */

public class TransferRequestIntegrationTest extends TestCase {

  public void testTransferHttp_outOfScope() {
    try {
      ServletScopes.transferRequest(() -> false);
      fail();
    } catch (OutOfScopeException expected) {
    }
  }

  public void testTransferNonHttp_outOfScope() {
    try {
      ServletScopes.transferRequest(() -> false);
      fail();
    } catch (OutOfScopeException expected) {
    }
  }

  public void testTransferNonHttp_outOfScope_closeable() {
    try {
      ServletScopes.transferRequest();
      fail();
    } catch (OutOfScopeException expected) {
    }
  }

  public void testTransferNonHttpRequest() throws Exception {
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindScope(RequestScoped.class, ServletScopes.REQUEST);
              }

              @Provides
              @RequestScoped
              Object provideObject() {
                return new Object();
              }
            });

    Callable<Callable<Boolean>> callable =
        () -> {
          final Object original = injector.getInstance(Object.class);
          return ServletScopes.transferRequest(
              () -> original == injector.getInstance(Object.class));
        };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    Callable<Boolean> transfer = ServletScopes.scopeRequest(callable, seedMap).call();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    assertTrue(executor.submit(transfer).get());
    executor.shutdownNow();
  }

  public void testTransferNonHttpRequest_closeable() throws Exception {
    final Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindScope(RequestScoped.class, ServletScopes.REQUEST);
              }

              @Provides
              @RequestScoped
              Object provideObject() {
                return new Object();
              }
            });

    class Data {
      Object object;
      RequestScoper scoper;
    }

    Callable<Data> callable =
        () -> {
          Data data = new Data();
          data.object = injector.getInstance(Object.class);
          data.scoper = ServletScopes.transferRequest();
          return data;
        };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    Data data = ServletScopes.scopeRequest(callable, seedMap).call();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    RequestScoper.CloseableScope scope = data.scoper.open();
    try {
      assertSame(data.object, injector.getInstance(Object.class));
    } finally {
      scope.close();
      executor.shutdownNow();
    }
  }

  public void testTransferNonHttpRequest_concurrentUseBlocks() throws Exception {
    Callable<Boolean> callable =
        () -> {
          ExecutorService executor = Executors.newSingleThreadExecutor();
          try {
            Future<Boolean> future = executor.submit(ServletScopes.transferRequest(() -> false));
            try {
              return future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
              return true;
            }
          } finally {
            executor.shutdownNow();
          }
        };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertTrue(ServletScopes.scopeRequest(callable, seedMap).call());
  }

  public void testTransferNonHttpRequest_concurrentUseBlocks_closeable() throws Exception {
    Callable<Boolean> callable =
        () -> {
          final RequestScoper scoper = ServletScopes.transferRequest();
          ExecutorService executor = Executors.newSingleThreadExecutor();
          try {
            Future<Boolean> future =
                executor.submit(
                    () -> {
                      RequestScoper.CloseableScope scope = scoper.open();
                      try {
                        return false;
                      } finally {
                        scope.close();
                      }
                    });
            try {
              return future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
              return true;
            }
          } finally {
            executor.shutdownNow();
          }
        };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertTrue(ServletScopes.scopeRequest(callable, seedMap).call());
  }

  public void testTransferNonHttpRequest_concurrentUseSameThreadOk() throws Exception {
    Callable<Boolean> callable = () -> ServletScopes.transferRequest(() -> false).call();

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertFalse(ServletScopes.scopeRequest(callable, seedMap).call());
  }

  public void testTransferNonHttpRequest_concurrentUseSameThreadOk_closeable() throws Exception {
    Callable<Boolean> callable =
        () -> {
          RequestScoper.CloseableScope scope = ServletScopes.transferRequest().open();
          try {
            return false;
          } finally {
            scope.close();
          }
        };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertFalse(ServletScopes.scopeRequest(callable, seedMap).call());
  }
}
