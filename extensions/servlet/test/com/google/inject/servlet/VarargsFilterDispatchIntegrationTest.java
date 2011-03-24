package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

/**
 *
 * This tests that filter stage of the pipeline dispatches
 * correctly to guice-managed filters.
 *
 * WARNING(dhanji): Non-parallelizable test =(
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
    final Injector injector = Guice.createInjector(new ServletModule() {

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

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index.html")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);
    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));
    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not"
        + " fire correct number of times-- inits: " + inits + "; dos: " + doFilters
        + "; destroys: " + destroys,
    		inits == 1 && doFilters == 3 && destroys == 1);
  }

  public final void testDispatchThatNoFiltersFire() throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

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

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index.xhtml")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);
    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));
    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not "
            + "fire correct number of times-- inits: " + inits + "; dos: " + doFilters
            + "; destroys: " + destroys,
    		inits == 1 && doFilters == 0 && destroys == 1);
  }

  public final void testDispatchFilterPipelineWithRegexMatching() throws ServletException,
      IOException {

    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        filterRegex("/[A-Za-z]*", "/index").through(TestFilter.class);

        //these filters should never fire
        filterRegex("\\w").through(Key.get(TestFilter.class));
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    replay(requestMock);
    pipeline.dispatch(requestMock, null, createMock(FilterChain.class));
    pipeline.destroyPipeline();

    verify(requestMock);

    assertTrue("lifecycle states did not fire "
	    + "correct number of times-- inits: " + inits + "; dos: " + doFilters
	    + "; destroys: " + destroys,
    		inits == 1 && doFilters == 2 && destroys == 1);
  }

  @Singleton
  public static class TestFilter implements Filter {
    public void init(FilterConfig filterConfig) throws ServletException {
      inits++;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      doFilters++;
      filterChain.doFilter(servletRequest, servletResponse);
    }

    public void destroy() {
      destroys++;
    }
  }
}
