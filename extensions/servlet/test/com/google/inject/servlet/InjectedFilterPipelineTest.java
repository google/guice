package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
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
import junit.framework.TestCase;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;

/**
 * Exactly the same as {@linkplain com.google.inject.servlet.FilterPipelineTest} except
 * that we test that the static pipeline is not used.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class InjectedFilterPipelineTest extends TestCase {
  private Injector injector1;
  private Injector injector2;

  @Override
  public final void setUp() {
    injector1 = Guice.createInjector(new ServletModule() {

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
    injector2 = Guice.createInjector(new ServletModule() {

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
  public final void tearDown() {
  }

  public final void testDispatchThruInjectedGuiceFilter() throws ServletException, IOException {

    //create mocks
    FilterConfig filterConfig = createMock(FilterConfig.class);
    ServletContext servletContext = createMock(ServletContext.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);
    FilterChain proceedingFilterChain = createMock(FilterChain.class);

    //begin mock script ***

    expect(filterConfig.getServletContext())
        .andReturn(servletContext)
        .once();

    expect(request.getRequestURI())
        .andReturn("/non-jsp/login.html") // use a path that will fail in injector2
        .anyTimes();
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    //at the end, proceed down webapp's normal filter chain
    proceedingFilterChain.doFilter(isA(HttpServletRequest.class), (ServletResponse) isNull());
    expectLastCall().once();

    //run mock script ***
    replay(filterConfig, servletContext, request, proceedingFilterChain);

    GuiceFilter webFilter = injector1.getInstance(GuiceFilter.class);

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain);
    webFilter.destroy();

    //assert expectations
    verify(filterConfig, servletContext, request, proceedingFilterChain);






    // reset mocks and run them against the other injector
    reset(filterConfig, servletContext, request, proceedingFilterChain);

    // Create a second proceeding filter chain
    FilterChain proceedingFilterChain2 = createMock(FilterChain.class);

    //begin mock script ***

    expect(filterConfig.getServletContext())
        .andReturn(servletContext)
        .once();
    expect(request.getRequestURI())
            .andReturn("/public/login/login.jsp") // use a path that will fail in injector1
            .anyTimes();
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    //at the end, proceed down webapp's normal filter chain
    proceedingFilterChain2.doFilter(isA(HttpServletRequest.class), (ServletResponse) isNull());
    expectLastCall().once();

    // Never fire on this pipeline
    replay(filterConfig, servletContext, request, proceedingFilterChain2, proceedingFilterChain);

    webFilter = injector2.getInstance(GuiceFilter.class);

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain2);
    webFilter.destroy();

    // Verify that we have not crossed the streams, Venkman!
    verify(filterConfig, servletContext, request, proceedingFilterChain, proceedingFilterChain2);
  }

  @Singleton
  public static class TestFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      filterChain.doFilter(servletRequest, servletResponse);
    }

    public void destroy() {
    }
  }

  @Singleton
  public static class NeverFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
    	fail("This filter should never have fired");
    }

    public void destroy() {
    }
  }
}
