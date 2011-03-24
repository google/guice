package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Singleton;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.isNull;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

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

/**
 * This is a basic whitebox test that verifies the glue between
 * GuiceFilter and ManagedFilterPipeline is working.
 * 
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class FilterPipelineTest extends TestCase {

  @Override
  public final void setUp() {
    GuiceFilter.reset();
    
    Guice.createInjector(new ServletModule() {

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

  @Override
  public final void tearDown() {
    GuiceFilter.reset();
  }

  public final void testDispatchThruGuiceFilter() throws ServletException, IOException {

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
        .andReturn("/public/login.jsp")
        .anyTimes();
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    //at the end, proceed down webapp's normal filter chain
    proceedingFilterChain.doFilter(isA(HttpServletRequest.class), (ServletResponse) isNull());
    expectLastCall().once();

    //run mock script ***
    replay(filterConfig, servletContext, request, proceedingFilterChain);

    final GuiceFilter webFilter = new GuiceFilter();

    webFilter.init(filterConfig);
    webFilter.doFilter(request, null, proceedingFilterChain);
    webFilter.destroy();

    //assert expectations
    verify(filterConfig, servletContext, request, proceedingFilterChain);
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
