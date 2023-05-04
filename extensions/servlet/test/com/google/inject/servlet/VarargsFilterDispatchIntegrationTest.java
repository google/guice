package com.google.inject.servlet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

/**
 * This tests that filter stage of the pipeline dispatches correctly to guice-managed filters.
 *
 * <p>WARNING(dhanji): Non-parallelizable test =(
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class VarargsFilterDispatchIntegrationTest extends TestCase {
  private static int inits, doFilters, destroys;

  @Override
  public final void setUp() {
    inits = 0;
    doFilters = 0;
    destroys = 0;

    GuiceFilter.reset();
  }

  public final void testDispatchRequestToManagedPipeline() throws ServletException, IOException {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                // This is actually a double match for "/*"
                filter("/*", "*.html", "/*").through(Key.get(TestFilter.class));

                // These filters should never fire
                filter("/index/*").through(Key.get(TestFilter.class));
                filter("*.jsp").through(Key.get(TestFilter.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.html");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));
    pipeline.destroyPipeline();

    assertTrue(
        "lifecycle states did not"
            + " fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys,
        inits == 1 && doFilters == 3 && destroys == 1);
  }

  public final void testDispatchThatNoFiltersFire() throws ServletException, IOException {
    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filter("/public/*", "*.html", "*.xml").through(Key.get(TestFilter.class));

                // These filters should never fire
                filter("/index/*").through(Key.get(TestFilter.class));
                filter("*.jsp").through(Key.get(TestFilter.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index.xhtml");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));
    pipeline.destroyPipeline();

    assertTrue(
        "lifecycle states did not "
            + "fire correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys,
        inits == 1 && doFilters == 0 && destroys == 1);
  }

  public final void testDispatchFilterPipelineWithRegexMatching()
      throws ServletException, IOException {

    final Injector injector =
        Guice.createInjector(
            new ServletModule() {

              @Override
              protected void configureServlets() {
                filterRegex("/[A-Za-z]*", "/index").through(TestFilter.class);

                //these filters should never fire
                filterRegex("\\w").through(Key.get(TestFilter.class));
              }
            });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = mock(HttpServletRequest.class);

    when(requestMock.getRequestURI()).thenReturn("/index");
    when(requestMock.getContextPath()).thenReturn("");

    // dispatch request

    pipeline.dispatch(requestMock, null, mock(FilterChain.class));
    pipeline.destroyPipeline();

    assertTrue(
        "lifecycle states did not fire "
            + "correct number of times-- inits: "
            + inits
            + "; dos: "
            + doFilters
            + "; destroys: "
            + destroys,
        inits == 1 && doFilters == 2 && destroys == 1);
  }

  @Singleton
  public static class TestFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
      inits++;
    }

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      doFilters++;
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
      destroys++;
    }
  }
}
