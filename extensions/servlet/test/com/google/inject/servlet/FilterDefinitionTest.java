package com.google.inject.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.Test;

/**
 * Tests the lifecycle of the encapsulated {@link FilterDefinition} class.
 *
 * @author Dhanji R. Prasanna (dhanji@gmail com)
 */
@SuppressWarnings("unchecked") // Safe because mocks can only return the required types.
public class FilterDefinitionTest {
  @Test
  public final void testFilterInitAndConfig() throws ServletException {
    Injector injector = mock(Injector.class);
    Binding<Filter> binding = mock(Binding.class);

    final MockFilter mockFilter = new MockFilter();

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(Filter.class))).thenReturn(binding);

    when(injector.getInstance(Key.get(Filter.class))).thenReturn(mockFilter);

    // some init params
    //noinspection SSBasedInspection
    final Map<String, String> initParams =
        new ImmutableMap.Builder<String, String>()
            .put("ahsd", "asdas24dok")
            .put("ahssd", "asdasd124ok")
            .buildOrThrow();

    ServletContext servletContext = mock(ServletContext.class);
    final String contextName = "thing__!@@44";
    when(servletContext.getServletContextName()).thenReturn(contextName);

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

    final Enumeration<String> names = filterConfig.getInitParameterNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();

      assertTrue(initParams.containsKey(name));
      assertEquals(filterConfig.getInitParameter(name), initParams.get(name));
    }
  }

  @Test
  public final void testFilterCreateDispatchDestroy() throws ServletException, IOException {
    Injector injector = mock(Injector.class);
    Binding<Filter> binding = mock(Binding.class);
    HttpServletRequest request = mock(HttpServletRequest.class);

    final MockFilter mockFilter = new MockFilter();

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(Filter.class))).thenReturn(binding);

    when(injector.getInstance(Key.get(Filter.class))).thenReturn(mockFilter);

    when(request.getRequestURI()).thenReturn("/index.html");
    when(request.getContextPath()).thenReturn("");

    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    // should fire on mockfilter now
    filterDef.init(mock(ServletContext.class), injector, Sets.<Filter>newIdentityHashSet());
    assertTrue(filterDef.getFilter() instanceof MockFilter);

    assertTrue(mockFilter.isInit(), "Init did not fire");

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

    assertTrue(proceed[0], "Filter did not proceed down chain");

    filterDef.destroy(Sets.<Filter>newIdentityHashSet());
    assertTrue(mockFilter.isDestroy(), "Destroy did not fire");
  }

  @Test
  public final void testFilterCreateDispatchDestroySupressChain()
      throws ServletException, IOException {

    Injector injector = mock(Injector.class);
    Binding<Filter> binding = mock(Binding.class);
    HttpServletRequest request = mock(HttpServletRequest.class);

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

    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getBinding(Key.get(Filter.class))).thenReturn(binding);

    when(injector.getInstance(Key.get(Filter.class))).thenReturn(mockFilter);

    when(request.getRequestURI()).thenReturn("/index.html");
    when(request.getContextPath()).thenReturn("");

    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    // should fire on mockfilter now
    filterDef.init(mock(ServletContext.class), injector, Sets.<Filter>newIdentityHashSet());
    assertTrue(filterDef.getFilter() instanceof MockFilter);

    assertTrue(mockFilter.isInit(), "init did not fire");

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

    assertFalse(proceed[0], "filter did not suppress chain");

    filterDef.destroy(Sets.<Filter>newIdentityHashSet());
    assertTrue(mockFilter.isDestroy(), "destroy did not fire");
  }

  @Test
  public void testGetFilterIfMatching() throws ServletException {
    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    Injector injector = mock(Injector.class);
    Binding<Filter> binding = mock(Binding.class);

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
    when(injector.getBinding(Key.get(Filter.class))).thenReturn(binding);
    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getInstance(Key.get(Filter.class))).thenReturn(mockFilter);

    when(servletRequest.getContextPath()).thenReturn("/a_context_path");
    when(servletRequest.getRequestURI()).thenReturn("/a_context_path/test.html");

    filterDef.init(servletContext, injector, Sets.<Filter>newIdentityHashSet());
    Filter filter = filterDef.getFilterIfMatching(servletRequest);
    assertSame(filter, mockFilter);
  }

  @Test
  public void testGetFilterIfMatchingNotMatching() throws ServletException {
    String pattern = "/*";
    final FilterDefinition filterDef =
        new FilterDefinition(
            Key.get(Filter.class),
            UriPatternType.get(UriPatternType.SERVLET, pattern),
            new HashMap<String, String>(),
            null);
    HttpServletRequest servletRequest = mock(HttpServletRequest.class);
    ServletContext servletContext = mock(ServletContext.class);
    Injector injector = mock(Injector.class);
    @SuppressWarnings("unchecked") // Safe because mock will only ever return Filter
    Binding<Filter> binding = mock(Binding.class);

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
    when(injector.getBinding(Key.get(Filter.class))).thenReturn(binding);
    when(binding.acceptScopingVisitor((BindingScopingVisitor<Boolean>) any())).thenReturn(true);
    when(injector.getInstance(Key.get(Filter.class))).thenReturn(mockFilter);

    when(servletRequest.getContextPath()).thenReturn("/a_context_path");
    when(servletRequest.getRequestURI()).thenReturn("/test.html");

    filterDef.init(servletContext, injector, Sets.<Filter>newIdentityHashSet());
    Filter filter = filterDef.getFilterIfMatching(servletRequest);
    assertNull(filter);
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
