package com.google.inject.servlet;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
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

/**
 * Tests the lifecycle of the encapsulated {@link FilterDefinition} class.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
@SuppressWarnings("unchecked") // Safe because mocks can only return the required types.
public class FilterDefinitionTest extends TestCase {
  public final void testFilterInitAndConfig() throws ServletException {
    Injector injector = createMock(Injector.class);
    Binding<Filter> binding = createMock(Binding.class);

    final MockFilter mockFilter = new MockFilter();

    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class))).andReturn(binding);

    expect(injector.getInstance(Key.get(Filter.class))).andReturn(mockFilter).anyTimes();

    replay(binding, injector);

    //some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .build();

    ServletContext servletContext = createMock(ServletContext.class);
    final String contextName = "thing__!@@44";
    expect(servletContext.getServletContextName()).andReturn(contextName);

    replay(servletContext);

    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            initParams,
            null);
    filterDef.init(servletContext, injector, Sets.<Filter>newIdentityHashSet());

    assertTrue(filterDef.getFilter() instanceof MockFilter);
    final FilterConfig filterConfig = mockFilter.getConfig();
    assertTrue(null != filterConfig);
    assertEquals(contextName, filterConfig.getServletContext().getServletContextName());
    assertEquals(filterConfig.getFilterName(), Key.get(Filter.class).toString());

    final Enumeration<?> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = (String) names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertEquals(filterConfig.getInitParameter(name), initParams.get(name));
    }

    verify(binding, injector, servletContext);
  }

  public final void testFilterCreateDispatchDestroy() throws ServletException, IOException {
    Injector injector = createMock(Injector.class);
    Binding<Filter> binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter();

    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class))).andReturn(binding);

    expect(injector.getInstance(Key.get(Filter.class))).andReturn(mockFilter).anyTimes();

    expect(request.getRequestURI()).andReturn("/index.html");
    expect(request.getContextPath()).andReturn("").anyTimes();

    replay(injector, binding, request);

    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector, Sets.<Filter>newIdentityHashSet());
    assertTrue(filterDef.getFilter() instanceof MockFilter);

    assertTrue("Init did not fire", mockFilter.isInit());

    Filter matchingFilter = filterDef.getFilterIfMatching(request);
    assertSame(mockFilter, matchingFilter);

    final boolean proceed[] = new boolean[1];
    matchingFilter.doFilter(
        request,
        null,
        new FilterChainInvocation(null, null, null) {
          @Override
          public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
            proceed[0] = true;
          }
        });

    assertTrue("Filter did not proceed down chain", proceed[0]);

    filterDef.destroy(Sets.<Filter>newIdentityHashSet());
    assertTrue("Destroy did not fire", mockFilter.isDestroy());

    verify(injector, request);
  }

  public final void testFilterCreateDispatchDestroySupressChain()
      throws ServletException, IOException {

    Injector injector = createMock(Injector.class);
    Binding<Filter> binding = createMock(Binding.class);
    HttpServletRequest request = createMock(HttpServletRequest.class);

    final MockFilter mockFilter =
        new MockFilter() {
          @Override
          public void doFilter(
              ServletRequest servletRequest,
              ServletResponse servletResponse,
              FilterChain filterChain) {
            //suppress rest of chain...
          }
        };

    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getBinding(Key.get(Filter.class))).andReturn(binding);

    expect(injector.getInstance(Key.get(Filter.class))).andReturn(mockFilter).anyTimes();

    expect(request.getRequestURI()).andReturn("/index.html");
    expect(request.getContextPath()).andReturn("").anyTimes();

    replay(injector, binding, request);

    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    //should fire on mockfilter now
    filterDef.init(createMock(ServletContext.class), injector, Sets.<Filter>newIdentityHashSet());
    assertTrue(filterDef.getFilter() instanceof MockFilter);

    assertTrue("init did not fire", mockFilter.isInit());

    Filter matchingFilter = filterDef.getFilterIfMatching(request);
    assertSame(mockFilter, matchingFilter);

    final boolean proceed[] = new boolean[1];
    matchingFilter.doFilter(
        request,
        null,
        new FilterChainInvocation(null, null, null) {
          @Override
          public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) {
            proceed[0] = true;
          }
        });

    assertFalse("filter did not suppress chain", proceed[0]);

    filterDef.destroy(Sets.<Filter>newIdentityHashSet());
    assertTrue("destroy did not fire", mockFilter.isDestroy());

    verify(injector, request);
  }

  public void testGetFilterIfMatching() throws ServletException {
    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    ServletContext servletContext = createMock(ServletContext.class);
    Injector injector = createMock(Injector.class);
    Binding<Filter> binding = createMock(Binding.class);

    final MockFilter mockFilter =
        new MockFilter() {
          @Override
          public void doFilter(
              ServletRequest servletRequest,
              ServletResponse servletResponse,
              FilterChain filterChain) {
            //suppress rest of chain...
          }
        };
    expect(injector.getBinding(Key.get(Filter.class))).andReturn(binding);
    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getInstance(Key.get(Filter.class))).andReturn(mockFilter).anyTimes();

    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/a_context_path/test.html");

    replay(servletRequest, binding, injector);
    filterDef.init(servletContext, injector, Sets.<Filter>newIdentityHashSet());
    Filter filter = filterDef.getFilterIfMatching(servletRequest);
    assertSame(filter, mockFilter);
    verify(servletRequest, binding, injector);
  }

  public void testGetFilterIfMatchingNotMatching() throws ServletException {
    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    ServletContext servletContext = createMock(ServletContext.class);
    Injector injector = createMock(Injector.class);
    @SuppressWarnings("unchecked") // Safe because mock will only ever return Filter
    Binding<Filter> binding = createMock(Binding.class);

    final MockFilter mockFilter =
        new MockFilter() {
          @Override
          public void doFilter(
              ServletRequest servletRequest,
              ServletResponse servletResponse,
              FilterChain filterChain) {
            //suppress rest of chain...
          }
        };
    expect(injector.getBinding(Key.get(Filter.class))).andReturn(binding);
    expect(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) anyObject()))
        .andReturn(true);
    expect(injector.getInstance(Key.get(Filter.class))).andReturn(mockFilter).anyTimes();

    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/test.html");

    replay(servletRequest, binding, injector);
    filterDef.init(servletContext, injector, Sets.<Filter>newIdentityHashSet());
    Filter filter = filterDef.getFilterIfMatching(servletRequest);
    assertNull(filter);
    verify(servletRequest, binding, injector);
  }

  private static class MockFilter implements Filter {
    private boolean init;
    private boolean destroy;
    private FilterConfig config;

    @Override
    public void init(FilterConfig filterConfig) {
      init = true;

      this.config = filterConfig;
    }

    @Override
    public void doFilter(
        ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
        throws IOException, ServletException {
      //proceed
      filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {
      destroy = true;
    }

    public boolean isInit() {
      return init;
    }

    public boolean isDestroy() {
      return destroy;
    }

    public FilterConfig getConfig() {
      return config;
    }
  }
}
