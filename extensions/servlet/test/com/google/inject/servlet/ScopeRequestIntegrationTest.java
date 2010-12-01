/**
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

package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import com.google.inject.internal.util.ImmutableMap;
import com.google.inject.internal.util.Maps;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import junit.framework.TestCase;

/**
 * Tests continuation of requests
 */
public class ScopeRequestIntegrationTest extends TestCase {
  private static final String A_VALUE = "thereaoskdao";
  private static final String A_DIFFERENT_VALUE = "hiaoskd";

  private static final String SHOULDNEVERBESEEN = "Shouldneverbeseen!";

  public final void testNonHttpRequestScopedCallable()
      throws ServletException, IOException, InterruptedException, ExecutionException {
    ExecutorService executor = Executors.newSingleThreadExecutor();

    // We use servlet module here because we want to test that @RequestScoped
    // behaves properly with the non-HTTP request scope logic.
    Injector injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        bindConstant().annotatedWith(Names.named(SomeObject.INVALID)).to(SHOULDNEVERBESEEN);
        bind(SomeObject.class).in(RequestScoped.class);
      }
    });

    SomeObject someObject = new SomeObject(A_VALUE);
    OffRequestCallable offRequestCallable = injector.getInstance(OffRequestCallable.class);
    executor.submit(ServletScopes.scopeRequest(offRequestCallable,
        ImmutableMap.<Key<?>, Object>of(Key.get(SomeObject.class), someObject))).get();

    assertSame(injector.getInstance(OffRequestCallable.class), offRequestCallable);

    // Make sure the value was passed on.
    assertEquals(someObject.value, offRequestCallable.value);
    assertFalse(SHOULDNEVERBESEEN.equals(someObject.value));

    // Now create a new request and assert that the scopes don't cross.
    someObject = new SomeObject(A_DIFFERENT_VALUE);
    executor.submit(ServletScopes.scopeRequest(offRequestCallable,
        ImmutableMap.<Key<?>, Object>of(Key.get(SomeObject.class), someObject))).get();

    assertSame(injector.getInstance(OffRequestCallable.class), offRequestCallable);

    // Make sure the value was passed on.
    assertEquals(someObject.value, offRequestCallable.value);
    assertFalse(SHOULDNEVERBESEEN.equals(someObject.value));
    executor.shutdown();
    executor.awaitTermination(2, TimeUnit.SECONDS);
  }
  
  public final void testWrongValueClasses() throws Exception {
    Injector injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        bindConstant().annotatedWith(Names.named(SomeObject.INVALID)).to(SHOULDNEVERBESEEN);
        bind(SomeObject.class).in(RequestScoped.class);
      }
    });
    
    OffRequestCallable offRequestCallable = injector.getInstance(OffRequestCallable.class);
    try {
      ServletScopes.scopeRequest(offRequestCallable,
        ImmutableMap.<Key<?>, Object>of(Key.get(SomeObject.class), "Boo!"));
      fail();
    } catch(IllegalArgumentException iae) {
      assertEquals("Value[Boo!] of type[java.lang.String] is not compatible with key[" + Key.get(SomeObject.class) + "]", iae.getMessage());
    }
  }
  
  public final void testNullReplacement() throws Exception {
    Injector injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        bindConstant().annotatedWith(Names.named(SomeObject.INVALID)).to(SHOULDNEVERBESEEN);
        bind(SomeObject.class).in(RequestScoped.class);
      }
    });
    
    Callable<SomeObject> callable = injector.getInstance(Caller.class);
    try {
      assertNotNull(callable.call());
      fail();
    } catch(ProvisionException pe) {
      assertTrue(pe.getCause() instanceof OutOfScopeException);
    }
    
    // Validate that an actual null entry in the map results in a null injected object.
    Map<Key<?>, Object> map = Maps.newHashMap();
    map.put(Key.get(SomeObject.class), null);
    callable = ServletScopes.scopeRequest(injector.getInstance(Caller.class), map);
    assertNull(callable.call());
  }

  @RequestScoped
  public static class SomeObject {
    private static final String INVALID = "invalid";

    @Inject
    public SomeObject(@Named(INVALID) String value) {
      this.value = value;
    }
    private final String value;
  }

  @Singleton
  public static class OffRequestCallable implements Callable<String> {
    @Inject Provider<SomeObject> someObject;

    public String value;

    public String call() throws Exception {
      // Inside this request, we should always get the same instance.
      assertSame(someObject.get(), someObject.get());

      value = someObject.get().value;
      assertFalse(SHOULDNEVERBESEEN.equals(value));

      return value;
    }
  }
  
  private static class Caller implements Callable<SomeObject> {
    @Inject Provider<SomeObject> someObject;
    
    public SomeObject call() throws Exception {
      return someObject.get();
    }
  }
}
