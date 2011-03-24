package com.google.inject.servlet;

import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.Maps;
import com.google.inject.internal.util.Sets;
import com.google.inject.spi.BindingScopingVisitor;
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
import static org.easymock.EasyMock.anyObject;
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
    Binding binding = createMock(Binding.class);

    final MockFilter mockFilter = new MockFilter();

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(binding);

    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    replay(binding, injector);

    //some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams = new HashMap<String, String>() {{
      put("ahsd", "asdas24dok");
      put("ahssd", "asdasd124ok");
      put("ahfsasd", "asda124sdok");
      put("ahsasgd", "a124sdasdok");
      put("ahsd124124", "as124124124dasdok");
    }};


    ServletContext servletContext = createMock(ServletContext.class);
    final String contextName = "thing__!@@44";
    expect(servletContext.getServletContextName()).andReturn(contextName);

    replay(servletContext);

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
    		UriPatternType.get(UriPatternType.SERVLET, pattern), initParams, null);
    filterDef.init(servletContext, injector,
        Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));

    assertTrue(filterDef.getFilter() instanceof MockFilter);
    final FilterConfig filterConfig = mockFilter.getConfig();
    assertTrue(null != filterConfig);
    assertTrue(contextName.equals(filterConfig.getServletContext().getServletContextName()));
    assertTrue(Key.get(Filter.class).toString().equals(filterConfig.getFilterName()));

    final Enumeration names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertTrue(initParams.get(name).equals(filterConfig.getInitParameter(name)));
    }
    
    verify(binding, injector, servletContext);
  }

  public final void testFilterCreateDispatchDestroy() throws ServletException, IOException {
    Injector injector = createMock(Injector.class);
    Binding binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter();

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(binding);

    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    expect(request.getRequestURI()).andReturn("/index.html");
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    replay(injector, binding, request);

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
        UriPatternType.get(UriPatternType.SERVLET, pattern), new HashMap<String, String>(), null);
    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector,
        Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));
    assertTrue(filterDef.getFilter() instanceof MockFilter);

    assertTrue("Init did not fire", mockFilter.isInit());

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
    Binding binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter() {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
          FilterChain filterChain) throws IOException, ServletException {
        setRun(true);

        //suppress rest of chain...
      }
    };

    expect(binding.acceptScopingVisitor((BindingScopingVisitor) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class)))
        .andReturn(binding);
    
    expect(injector.getInstance(Key.get(Filter.class)))
        .andReturn(mockFilter)
        .anyTimes();

    expect(request.getRequestURI()).andReturn("/index.html");
    expect(request.getContextPath())
        .andReturn("")
        .anyTimes();

    replay(injector, binding, request);

    String pattern = "/*";
    final FilterDefinition filterDef = new FilterDefinition(pattern, Key.get(Filter.class),
        UriPatternType.get(UriPatternType.SERVLET, pattern), new HashMap<String, String>(), null);
    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector,
    		Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));
    assertTrue(filterDef.getFilter() instanceof MockFilter);


    assertTrue("init did not fire", mockFilter.isInit());

    final boolean proceed[] = new boolean[1];
    filterDef.doFilter(request, null, new FilterChainInvocation(null, null, null) {
      public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse)
          throws IOException, ServletException {
        proceed[0] = true;
      }
    });

    assertTrue("filter did not suppress chain", !proceed[0]);

    filterDef.destroy(Sets.newSetFromMap(Maps.<Filter, Boolean>newIdentityHashMap()));
    assertTrue("destroy did not fire", mockFilter.isDestroy());

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
