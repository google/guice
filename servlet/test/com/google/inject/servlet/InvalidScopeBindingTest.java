package com.google.inject.servlet;

import com.google.inject.Guice;
import com.google.inject.Singleton;
import com.google.inject.Scopes;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;

/**
 * Ensures that an error is thrown if a Servlet or Filter is bound
 * under any scope other than singleton, explicitly.
 *
 * @author dhanji@gmail.com
 */
public class InvalidScopeBindingTest extends TestCase {

  @Override
  protected void tearDown() throws Exception {
    GuiceFilter.reset();
  }

  public final void testServletInNonSingletonScopeThrowsServletException(){
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(new ServletModule() {
      @Override
      protected void configureServlets() {
        serve("/*").with(MyNonSingletonServlet.class);
      }
    });

    ServletException se = null;
    try {
      guiceFilter.init(createMock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNotNull("Servlet exception was not thrown with wrong scope binding", se);
    }
  }

  public final void testFilterInNonSingletonScopeThrowsServletException(){
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(MyNonSingletonFilter.class);
      }
    });

    ServletException se = null;
    try {
      guiceFilter.init(createMock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNotNull("Servlet exception was not thrown with wrong scope binding", se);
    }
  }

  public final void testHappyCaseFilter(){
    GuiceFilter guiceFilter = new GuiceFilter();

    Guice.createInjector(new ServletModule() {
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
      guiceFilter.init(createMock(FilterConfig.class));
    } catch (ServletException e) {
      se = e;
    } finally {
      assertNull("Servlet exception was thrown with correct scope binding", se);
    }
  }

  @RequestScoped
  public static class MyNonSingletonServlet extends HttpServlet { }

  @SessionScoped
  public static class MyNonSingletonFilter extends DummyFilterImpl { }

  @Singleton
  public static class MySingletonFilter extends DummyFilterImpl { }
}
