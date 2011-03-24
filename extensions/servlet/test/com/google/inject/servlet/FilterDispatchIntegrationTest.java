package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import junit.framework.TestCase;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import static com.google.inject.servlet.ManagedServletPipeline.REQUEST_DISPATCHER_REQUEST;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

/**
 *
 * This tests that filter stage of the pipeline dispatches
 * correctly to guice-managed filters.
 *
 * WARNING(dhanji): Non-parallelizable test =(
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class FilterDispatchIntegrationTest extends TestCase {
    private static int inits, doFilters, destroys;

  private IMocksControl control;

  @Override
  public final void setUp() {
    inits = 0;
    doFilters = 0;
    destroys = 0;
    control = EasyMock.createControl();
    GuiceFilter.reset();
  }


  public final void testDispatchRequestToManagedPipeline() throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        filter("/*").through(TestFilter.class);
        filter("*.html").through(TestFilter.class);
        filter("/*").through(Key.get(TestFilter.class));

        // These filters should never fire
        filter("/index/*").through(Key.get(TestFilter.class));
        filter("*.jsp").through(Key.get(TestFilter.class));

        // Bind a servlet
        serve("*.html").with(TestServlet.class);
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    // create ourselves a mock request with test URI
    HttpServletRequest requestMock = control.createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index.html")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    requestMock.setAttribute(REQUEST_DISPATCHER_REQUEST, true);
    requestMock.removeAttribute(REQUEST_DISPATCHER_REQUEST);

    HttpServletResponse responseMock = control.createMock(HttpServletResponse.class);
    expect(responseMock.isCommitted())
        .andReturn(false)
        .anyTimes();
    responseMock.resetBuffer();
    expectLastCall().anyTimes();

    FilterChain filterChain = control.createMock(FilterChain.class);

    //dispatch request
    control.replay();
    pipeline.dispatch(requestMock, responseMock, filterChain);
    pipeline.destroyPipeline();
    control.verify();

    TestServlet servlet = injector.getInstance(TestServlet.class);
    assertEquals(2, servlet.processedUris.size());
    assertTrue(servlet.processedUris.contains("/index.html"));
    assertTrue(servlet.processedUris.contains(TestServlet.FORWARD_TO));

    assertTrue("lifecycle states did not"
        + " fire correct number of times-- inits: " + inits + "; dos: " + doFilters
        + "; destroys: " + destroys, inits == 1 && doFilters == 3 && destroys == 1);
  }

  public final void testDispatchThatNoFiltersFire() throws ServletException, IOException {
    final Injector injector = Guice.createInjector(new ServletModule() {

      @Override
      protected void configureServlets() {
        filter("/public/*").through(TestFilter.class);
        filter("*.html").through(TestFilter.class);
        filter("*.xml").through(Key.get(TestFilter.class));

        // These filters should never fire
        filter("/index/*").through(Key.get(TestFilter.class));
        filter("*.jsp").through(Key.get(TestFilter.class));
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = control.createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index.xhtml")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    //dispatch request
    FilterChain filterChain = control.createMock(FilterChain.class);
    filterChain.doFilter(requestMock, null);
    control.replay();
    pipeline.dispatch(requestMock, null, filterChain);
    pipeline.destroyPipeline();
    control.verify();

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
        filterRegex("/[A-Za-z]*").through(TestFilter.class);
        filterRegex("/index").through(TestFilter.class);
        //these filters should never fire
        filterRegex("\\w").through(Key.get(TestFilter.class));
      }
    });

    final FilterPipeline pipeline = injector.getInstance(FilterPipeline.class);
    pipeline.initPipeline(null);

    //create ourselves a mock request with test URI
    HttpServletRequest requestMock = control.createMock(HttpServletRequest.class);

    expect(requestMock.getRequestURI())
            .andReturn("/index")
            .anyTimes();
    expect(requestMock.getContextPath())
        .andReturn("")
        .anyTimes();

    // dispatch request
    FilterChain filterChain = control.createMock(FilterChain.class);
    filterChain.doFilter(requestMock, null);
    control.replay();
    pipeline.dispatch(requestMock, null, filterChain);
    pipeline.destroyPipeline();
    control.verify();

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

  @Singleton
  public static class TestServlet extends HttpServlet {
    public static final String FORWARD_FROM = "/index.html";
    public static final String FORWARD_TO = "/forwarded.html";
    public List<String> processedUris = new ArrayList<String>();

    protected void service(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
        throws ServletException, IOException {
      String requestUri = httpServletRequest.getRequestURI();
      processedUris.add(requestUri);

      // If the client is requesting /index.html then we forward to /forwarded.html
      if (FORWARD_FROM.equals(requestUri)) {
        httpServletRequest.getRequestDispatcher(FORWARD_TO)
            .forward(httpServletRequest, httpServletResponse);
      }
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse)
        throws ServletException, IOException {
      service((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
    }
  }
}
