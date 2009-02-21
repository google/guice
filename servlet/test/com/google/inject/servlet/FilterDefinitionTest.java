package com.google.inject.servlet;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.Maps;
import com.google.inject.internal.Sets;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
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
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

/**
 * Tests the lifecycle of the encapsulated {@link FilterDefinition} class.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
public class FilterDefinitionTest extends TestCase {

  public final void testFilterInitAndConfig() throws ServletException {

    Injector injector = createMock(Injector.class);

    final MockFilter mockFilter = new MockFilter();

    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(createMock(Binding.class));

    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    replay(injector);

    //some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams = new HashMap<String, String>() {{
      put("ahsd", "asdas24dok");
      put("ahssd", "asdasd124ok");
      put("ahfsasd", "asda124sdok");
      put("ahsasgd", "a124sdasdok");
      put("ahsd124124", "as124124124dasdok");
    }};

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
        UriPatternType.get(UriPatternType.SERVLET, pattern), initParams);
    assert filterDef.getFilter() instanceof MockFilter;

    ServletContext servletContext = createMock(ServletContext.class);
    final String contextName = "thing__!@@44";
    expect(servletContext.getServletContextName()).andReturn(contextName);

    replay(servletContext);

    filterDef.init(servletContext, injector,
        Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));

    final FilterConfig filterConfig = mockFilter.getConfig();
    assert null != filterConfig;
    assert contextName.equals(filterConfig.getServletContext().getServletContextName());
    assert Key.get(Filter.class).toString().equals(filterConfig.getFilterName());

    final Enumeration names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();

      assert initParams.containsKey(name);
      assert initParams.get(name).equals(filterConfig.getInitParameter(name));
    }
  }

  public final void testFilterCreateDispatchDestroy() throws ServletException, IOException {
    Injector injector = createMock(Injector.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter();

    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(createMock(Binding.class));

    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    expect(request.getServletPath()).andReturn("/index.html");

    replay(injector, request);

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
        UriPatternType.get(UriPatternType.SERVLET, pattern), new HashMap<String, String>());
    assert filterDef.getFilter() instanceof MockFilter;

    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector,
        Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));

    assert mockFilter.isInit() : "Init did not fire";

    final boolean proceed[] = new boolean[1];
    filterDef.doFilter(request, null, new FilterChainInvocation(null, null, null) {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
          throws IOException, ServletException {

        proceed[0] = true;
      }
    });

    assertTrue("Filter did not proceed down chain", proceed[0]);

    filterDef.destroy(Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));
    assertTrue("Destroy did not fire", mockFilter.isDestroy());

    verify(injector, request);

  }

  public final void testFilterCreateDispatchDestroySupressChain()
      throws ServletException, IOException {

    Injector injector = createMock(Injector.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
          FilterChain filterChain) throws IOException, ServletException {
        setRun(true);

        //suppress rest of chain...
      }
    };

    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(createMock(Binding.class));
    
    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    expect(request.getServletPath()).andReturn("/index.html");

    replay(injector, request);

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
        UriPatternType.get(UriPatternType.SERVLET, pattern), new HashMap<String, String>());
    assert filterDef.getFilter() instanceof MockFilter;

    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector,
        Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));

    assert mockFilter.isInit() : "Init did not fire";

    final boolean proceed[] = new boolean[1];
    filterDef.doFilter(request, null, new FilterChainInvocation(null, null, null) {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
          throws IOException, ServletException {
        proceed[0] = true;
      }
    });

    assert !proceed[0] : "Filter did not suppress chain";

    filterDef.destroy(Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));
    assert mockFilter.isDestroy() : "Destroy did not fire";

    verify(injector, request);

  }

  private static class MockFilter implements Filter {
    private boolean init;
    private boolean destroy;
    private boolean run;
    private FilterConfig config;

    public void init(FilterConfig filterConfig) throws ServletException {
      init = true;

      this.config = filterConfig;
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
        FilterChain filterChain) throws IOException, ServletException {
      run = true;

      //proceed
      filterChain.doFilter(servletRequest, servletResponse);
    }

    protected void setRun(boolean run) {
      this.run = run;
    }

    public void destroy() {
      destroy = true;
    }

    public boolean isInit() {
      return init;
    }

    public boolean isDestroy() {
      return destroy;
    }

    public boolean isRun() {
      return run;
    }

    public FilterConfig getConfig() {
      return config;
    }
  }
}
