/**
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

import junit.framework.TestCase;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// TODO: Add test for HTTP transferring.
/**
 * Tests transferring of entire request scope.
 */

public class TransferRequestIntegrationTest extends TestCase {
  private final Callable<Boolean> FALSE_CALLABLE = new Callable<Boolean>() {
    @Override public Boolean call() {
      return false;
    }
  };

  public void testTransferHttp_outOfScope() {
    try {
      ServletScopes.transferRequest(FALSE_CALLABLE);
      fail();
    } catch (OutOfScopeException expected) {}
  }

  public void testTransferNonHttp_outOfScope() {
    try {
      ServletScopes.transferRequest(FALSE_CALLABLE);
      fail();
    } catch (OutOfScopeException expected) {}
  }

  public void testTransferNonHttpRequest() throws Exception {
    final Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bindScope(RequestScoped.class, ServletScopes.REQUEST);
      }

      @Provides @RequestScoped Object provideObject() {
        return new Object();
      }
    });

    Callable<Callable<Boolean>> callable = new Callable<Callable<Boolean>>() {
      @Override public Callable<Boolean> call() {
        final Object original = injector.getInstance(Object.class);
        return ServletScopes.transferRequest(new Callable<Boolean>() {
          @Override public Boolean call() {
            return original == injector.getInstance(Object.class);
          }
        });
      }
    };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    Callable<Boolean> transfer = ServletScopes.scopeRequest(callable, seedMap).call();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    assertTrue(executor.submit(transfer).get());
    executor.shutdownNow();
  }

  public void testTransferNonHttpRequest_concurrentUseBlocks() throws Exception {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override public Boolean call() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
          Future<Boolean> future = executor.submit(ServletScopes.transferRequest(FALSE_CALLABLE));
          try {
            return future.get(100, TimeUnit.MILLISECONDS);
          } catch (TimeoutException e) {
            return true;
          }
        } finally {
          executor.shutdownNow();
        }
      }
    };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertTrue(ServletScopes.scopeRequest(callable, seedMap).call());
  }

  public void testTransferNonHttpRequest_concurrentUseSameThreadOk() throws Exception {
    Callable<Boolean> callable = new Callable<Boolean>() {
      @Override public Boolean call() throws Exception {
        return ServletScopes.transferRequest(FALSE_CALLABLE).call();
      }
    };

    ImmutableMap<Key<?>, Object> seedMap = ImmutableMap.of();
    assertFalse(ServletScopes.scopeRequest(callable, seedMap).call());
  }
}
