package com.google.inject.servlet;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * This is a basic whitebox test that verifies the glue between GuiceFilter and
 * ManagedFilterPipeline is working.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class FilterPipelineTest {

  @BeforeEach
  public final void setUp() {
    GuiceFilter.reset();

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
  }

  @AfterEach
  public final void tearDown() {
    GuiceFilter.reset();
  }

  @Test
  public final void testDispatchThruGuiceFilter() throws ServletException, IOException {

    // create mocks
    FilterConfig filterConfig = mock(FilterConfig.class);
    ServletContext servletContext = mock(ServletContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    FilterChain proceedingFilterChain = mock(FilterChain.class);

    // begin mock script ***

    when(filterConfig.getServletContext()).thenReturn(servletContext);

    when(request.getRequestURI()).thenReturn("/public/login.jsp");
    when(request.getContextPath()).thenReturn("");

    // run mock script ***
    final GuiceFilter webFilter = new GuiceFilter();

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain);
    webFilter.destroy();

    // assert expectations
    verify(proceedingFilterChain)
        .doFilter(isA(HttpServletRequest.class), (ServletResponse) isNull());
  }

  @Singleton
  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {}

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
    public void init(FilterConfig filterConfig) {}

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) {
      fail("This filter should never have fired");
    }

    @Override
    public void destroy() {}
  }
}
