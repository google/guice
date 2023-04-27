package com.google.inject.servlet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import com.google.inject.Guice;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Ensures that an error is thrown if a Servlet or Filter is bound under any scope other than
 * singleton, explicitly.
 *
 * @author dhanji@gmail.com
 */
public class InvalidScopeBindingTest {

  @AfterEach
  protected void tearDown() throws Exception {
    GuiceFilter.reset();
  }

  @Test
  public final void testServletInNonSingletonScopeThrowsServletException() {
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(
        new ServletModule() {
          @Override
          protected void configureServlets() {
            serve("/*").with(MyNonSingletonServlet.class);
          }
        });

    ServletException se = null;
    try {
      guiceFilter.init(mock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNotNull(se, "Servlet exception was not thrown with wrong scope binding");
    }
  }

  @Test
  public final void testFilterInNonSingletonScopeThrowsServletException() {
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(
        new ServletModule() {
          @Override
          protected void configureServlets() {
            filter("/*").through(MyNonSingletonFilter.class);
          }
        });

    ServletException se = null;
    try {
      guiceFilter.init(mock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNotNull(se, "Servlet exception was not thrown with wrong scope binding");
    }
  }

  @Test
  public final void testHappyCaseFilter() {
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(
        new ServletModule() {
          @Override
          protected void configureServlets() {
            // Annotated scoping variant.
            filter("/*").through(MySingletonFilter.class);

            // Explicit scoping variant.
            bind(DummyFilterImpl.class).in(Scopes.SINGLETON);
            filter("/*").through(DummyFilterImpl.class);
          }
        });

    ServletException se = null;
    try {
      guiceFilter.init(mock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNull(se, "Servlet exception was thrown with correct scope binding");
    }
  }

  @RequestScoped
  public static class MyNonSingletonServlet extends HttpServlet {}

  @SessionScoped
  public static class MyNonSingletonFilter extends DummyFilterImpl {}

  @Singleton
  public static class MySingletonFilter extends DummyFilterImpl {}
}
