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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.util.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

/**
 * Tests continuation of requests
 */
public class ContinuingRequestIntegrationTest extends TestCase {
  private static final String PARAM_VALUE = "there";
  private static final String PARAM_NAME = "hi";

  private static final AbstractExecutorService SAME_THREAD_EXECUTOR = new AbstractExecutorService() {
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
      } catch (Exception e) {
        throw new RuntimeException(e);
      }

      return null;
    }
  };

  private ExecutorService executor;

  public final void testRequestContinuesInOtherThread()
      throws ServletException, IOException, InterruptedException {
    executor = Executors.newSingleThreadExecutor();

    Injector injector = Guice.createInjector(new ServletModule() {
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
    expect(request.getServletPath()).andReturn("/");
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
  }

  public final void testRequestContinuesInSameThread()
      throws ServletException, IOException, InterruptedException {
    executor = SAME_THREAD_EXECUTOR;
    Injector injector = Guice.createInjector(new ServletModule() {
      @Override protected void configureServlets() {
        serve("/*").with(ContinuingServlet.class);

        bind(ExecutorService.class).toInstance(executor);
      }
    });

    FilterConfig filterConfig = createMock(FilterConfig.class);
    expect(filterConfig.getServletContext()).andReturn(createMock(ServletContext.class));

    GuiceFilter guiceFilter = injector.getInstance(GuiceFilter.class);

    HttpServletRequest request = createMock(HttpServletRequest.class);

    // this time it will try to get it from the scope, because its same-thread.
    // This is part of Isaac's patch that enabled request-scoping of the request.
    expect(request.getAttribute("Key[type=javax.servlet.http.HttpServletResponse, annotation=[none]]"))
        .andReturn(null);
    request.setAttribute(eq("Key[type=javax.servlet.http.HttpServletResponse, annotation=[none]]"),
        anyObject());
    expect(request.getAttribute("Key[type=javax.servlet.http.HttpServletRequest, annotation=[none]]"))
        .andReturn(null);
    request.setAttribute(eq("Key[type=javax.servlet.http.HttpServletRequest, annotation=[none]]"),
        anyObject());

    expect(request.getRequestURI()).andReturn("/");
    expect(request.getServletPath()).andReturn("/");
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
  }

  @Singleton
  public static class ContinuingServlet extends HttpServlet {
    @Inject OffRequestCallable callable;
    @Inject ExecutorService executorService;

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {
      Callable<String> task = ServletScopes.continueRequest(callable);

      executorService.submit(task);
    }
  }

  @Singleton
  public static class OffRequestCallable implements Callable<String> {
    @Inject Provider<HttpServletRequest> request;
    @Inject Provider<HttpServletResponse> response;

    public String value;

    public String call() throws Exception {
      assertNull(response.get());

      return value = request.get().getParameter(PARAM_NAME);
    }
  }
}
