package com.google.inject.servlet;

import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import junit.framework.TestCase;
import org.mockito.Mockito;

/**
 * Exactly the same as {@linkplain com.google.inject.servlet.FilterPipelineTest} except that we test
 * that the static pipeline is not used.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class InjectedFilterPipelineTest extends TestCase {
  private Injector injector1;
  private Injector injector2;

  @Override
  public final void setUp() {
    injector1 =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/*").through(TestFilter.class);
                filter("*.html").through(TestFilter.class);
                filter("/*").through(Key.get(TestFilter.class));
                filter("*.jsp").through(Key.get(TestFilter.class));

                // These filters should never fire
                filter("/index/*").through(Key.get(NeverFilter.class));
                filter("/public/login/*").through(Key.get(NeverFilter.class));
              }
            });

    // Test second injector with exactly opposite pipeline config
    injector2 =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                // These filters should never fire
                filter("*.html").through(NeverFilter.class);
                filter("/non-jsp/*").through(Key.get(NeverFilter.class));

                // only these filters fire.
                filter("/index/*").through(Key.get(TestFilter.class));
                filter("/public/login/*").through(Key.get(TestFilter.class));
              }
            });
  }

  @Override
  public final void tearDown() {}

  public final void testDispatchThruInjectedGuiceFilter() throws ServletException, IOException {

    // create mocks
    FilterConfig filterConfig = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    FilterChain proceedingFilterChain = mock(FilterChain.class);

    // begin mock script ***

    when(filterConfig.getServletContext()).thenReturn(servletContext);

    when(request.getRequestURI())
        .thenReturn("/non-jsp/login.html"); // use a path that will fail in injector2
    when(request.getContextPath()).thenReturn("");

    // at the end, proceed down webapp's normal filter chain

    // run mock script ***
    GuiceFilter webFilter = injector1.getInstance(GuiceFilter.class);

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain);
    webFilter.destroy();

    // assert expectations
    verify(proceedingFilterChain)
        .doFilter(isA(HttpServletRequest.class), (ServletResponse) isNull());

    // reset mocks and run them against the other injector
    Mockito.reset(filterConfig, servletContext, request, proceedingFilterChain);

    // Create a second proceeding filter chain
    FilterChain proceedingFilterChain2 = mock(FilterChain.class);

    // begin mock script ***

    when(filterConfig.getServletContext()).thenReturn(servletContext);
    when(request.getRequestURI())
        .thenReturn("/public/login/login.jsp"); // use a path that will fail in injector1
    when(request.getContextPath()).thenReturn("");

    // Never fire on this pipeline
    webFilter = injector2.getInstance(GuiceFilter.class);

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain2);
    webFilter.destroy();

    // Verify that we have not crossed the streams, Venkman!
    verify(proceedingFilterChain2).doFilter(isA(HttpServletRequest.class), isNull());
  }

  @Singleton
  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {}
  }

  @Singleton
  public static class NeverFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      fail("This filter should never have fired");
    }

    @Override
    public void destroy() {}
  }
}
