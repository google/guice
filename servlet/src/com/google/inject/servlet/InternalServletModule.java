package com.google.inject.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * This is a left-factoring of all ServletModules installed in the system.
 * In other words, this module contains the bindings common to all ServletModules,
 * and is bound exactly once per injector.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
final class InternalServletModule extends AbstractModule {

  @Override
  protected void configure() {
    bindScope(RequestScoped.class, REQUEST);
    bindScope(SessionScoped.class, SESSION);
    bind(ServletRequest.class).to(HttpServletRequest.class);
    bind(ServletResponse.class).to(HttpServletResponse.class);

    // inject the pipeline into GuiceFilter so it can route requests correctly
    // Unfortunate staticness... =(
    requestStaticInjection(GuiceFilter.class);

    bind(ManagedServletPipeline.class);
    bind(FilterPipeline.class).to(ManagedFilterPipeline.class).asEagerSingleton();
  }

  @Provides @RequestScoped HttpServletRequest provideHttpServletRequest() {
    return GuiceFilter.getRequest();
  }

  @Provides @RequestScoped HttpServletResponse provideHttpServletResponse() {
    return GuiceFilter.getResponse();
  }

  @Provides HttpSession provideHttpSession() {
    return GuiceFilter.getRequest().getSession();
  }

  @Provides ServletContext provideServletContext() {
    return GuiceFilter.getServletContext();
  }

  @SuppressWarnings({"unchecked"})
  @Provides @RequestScoped @RequestParameters Map<String, String[]> provideRequestParameters() {
    return GuiceFilter.getRequest().getParameterMap();
  }

  @Override
  public boolean equals(Object o) {
    // Is only ever installed internally, so we don't need to check state.
    return o instanceof InternalServletModule;
  }

  @Override
  public int hashCode() {
    return InternalServletModule.class.hashCode();
  }
}
