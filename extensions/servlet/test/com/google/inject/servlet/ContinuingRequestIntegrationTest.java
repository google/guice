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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.ImmutableMap;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Tests continuation of requests
 */
public class ContinuingRequestIntegrationTest extends TestCase {
  private static final String PARAM_VALUE = "there";
  private static final String PARAM_NAME = "hi";

  private final AtomicBoolean failed = new AtomicBoolean(false);
  private final AbstractExecutorService sameThreadExecutor = new AbstractExecutorService() {
    public void shutdown() {
    }

    public List<Runnable> shutdownNow() {
      return ImmutableList.of();
    }

    public boolean isShutdown() {
      return true;
    }

    public boolean isTerminated() {
      return true;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return true;
    }

    public void execute(Runnable command) {
      command.run();
    }

    @Override public <T> Future<T> submit(Callable<T> task) {
      try {
        task.call();
        fail();
      } catch (Exception e) {
        // Expected.
        assertTrue(e instanceof IllegalStateException);
        failed.set(true);
      }

      return null;
    }
  };

  private ExecutorService executor;
  private Injector injector;

  @Override protected void tearDown() throws Exception {
    injector.getInstance(GuiceFilter.class).destroy();
  }

  public final void testRequestContinuesInOtherThread()
      throws ServletException, IOException, InterruptedException {
    executor = Executors.newSingleThreadExecutor();

    injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        serve("/*").with(ContinuingServlet.class);

        bind(ExecutorService.class).toInstance(executor);
      }
    });

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getServletContext()).andReturn(createMock(ServletContext.class));
    
    GuiceFilter guiceFilter = injector.getInstance(GuiceFilter.class);

    HttpServletRequest request = createMock(HttpServletRequest.class);

    expect(request.getRequestURI()).andReturn("/");
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();
    expect(request.getMethod()).andReturn("GET");

    FilterChain filterChain = createMock(FilterChain.class);
    expect(request.getParameter(PARAM_NAME)).andReturn(PARAM_VALUE);

    replay(request, filterConfig, filterChain);

    guiceFilter.init(filterConfig);
    guiceFilter.doFilter(request, null, filterChain);

    // join.
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    assertEquals(PARAM_VALUE, injector.getInstance(OffRequestCallable.class).value);
    verify(request, filterConfig, filterChain);
  }

  public final void testRequestContinuationDiesInHttpRequestThread()
      throws ServletException, IOException, InterruptedException {
    executor = sameThreadExecutor;
    injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        serve("/*").with(ContinuingServlet.class);

        bind(ExecutorService.class).toInstance(executor);

        bind(SomeObject.class);
      }
    });

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getServletContext()).andReturn(createMock(ServletContext.class));

    GuiceFilter guiceFilter = injector.getInstance(GuiceFilter.class);

    HttpServletRequest request = createMock(HttpServletRequest.class);

    expect(request.getRequestURI()).andReturn("/");
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    expect(request.getMethod()).andReturn("GET");
    FilterChain filterChain = createMock(FilterChain.class);
    
    replay(request, filterConfig, filterChain);

    guiceFilter.init(filterConfig);
    guiceFilter.doFilter(request, null, filterChain);

    // join.
    executor.shutdown();
    executor.awaitTermination(10, TimeUnit.SECONDS);

    assertTrue(failed.get());
    assertFalse(PARAM_VALUE.equals(injector.getInstance(OffRequestCallable.class).value));

    verify(request, filterConfig, filterChain);
  }

  @RequestScoped
  public static class SomeObject {
  }

  @Singleton
  public static class ContinuingServlet extends HttpServlet {
    @Inject OffRequestCallable callable;
    @Inject ExecutorService executorService;

    private SomeObject someObject;

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      assertNull(someObject);

      // Seed with someobject.
      someObject = new SomeObject();
      Callable<String> task = ServletScopes.continueRequest(callable,
          ImmutableMap.<Key<?>, Object>of(Key.get(SomeObject.class), someObject));

      executorService.submit(task);
    }
  }

  @Singleton
  public static class OffRequestCallable implements Callable<String> {
    @Inject Provider<HttpServletRequest> request;
    @Inject Provider<HttpServletResponse> response;
    @Inject Provider<SomeObject> someObject;

    public String value;

    public String call() throws Exception {
      assertNull(response.get());

      // Inside this request, we should always get the same instance.
      assertSame(someObject.get(), someObject.get());

      return value = request.get().getParameter(PARAM_NAME);
    }
  }
}
