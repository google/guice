package com.google.inject.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import java.util.Map;

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
    // Scopes.
    bindScope(RequestScoped.class, REQUEST);
    bindScope(SessionScoped.class, SESSION);

    // Bind request.
    Provider<HttpServletRequest> requestProvider =
        new Provider<HttpServletRequest>() {
          public HttpServletRequest get() {
            return GuiceFilter.getRequest();
          }

          public String toString() {
            return "RequestProvider";
          }
        };
    bind(HttpServletRequest.class).toProvider(requestProvider);
    bind(ServletRequest.class).toProvider(requestProvider);

    // Bind response.
    Provider<HttpServletResponse> responseProvider =
        new Provider<HttpServletResponse>() {
          public HttpServletResponse get() {
            return GuiceFilter.getResponse();
          }

          public String toString() {
            return "ResponseProvider";
          }
        };
    bind(HttpServletResponse.class).toProvider(responseProvider);
    bind(ServletResponse.class).toProvider(responseProvider);

    // Bind session.
    bind(HttpSession.class).toProvider(new Provider<HttpSession>() {
      public HttpSession get() {
        return GuiceFilter.getRequest().getSession();
      }

      public String toString() {
        return "SessionProvider";
      }
    });

    // Bind servlet context.
    bind(ServletContext.class).toProvider(new Provider<ServletContext>() {
      public ServletContext get() {
        return GuiceFilter.getServletContext();
      }

      public String toString() {
        return "ServletContextProvider";
      }
    });

    // Bind request parameters.
    bind(new TypeLiteral<Map<String, String[]>>() {})
        .annotatedWith(RequestParameters.class)
        .toProvider(new Provider<Map<String, String[]>>() {
              @SuppressWarnings({"unchecked"})
              public Map<String, String[]> get() {
                return GuiceFilter.getRequest().getParameterMap();
              }

              public String toString() {
                return "RequestParametersProvider";
              }
            });

    // inject the pipeline into GuiceFilter so it can route requests correctly
    // Unfortunate staticness... =(
    requestStaticInjection(GuiceFilter.class);

    bind(ManagedServletPipeline.class);
    bind(FilterPipeline.class).to(ManagedFilterPipeline.class).asEagerSingleton();
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
