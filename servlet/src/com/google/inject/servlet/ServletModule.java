/**
 * Copyright (C) 2006 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Configures the servlet scopes and creates bindings for the servlet API
 * objects so you can inject the request, response, session, etc.
 *
 * <p>
 * <strong>
 * As of Guice 2.0, this module is no longer used directly. Instead you should
 * use the module obtained from the {@link Servlets#configure()} servlet binding
 * API.</strong>
 *
 * @author crazybob@google.com (Bob Lee)
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 * @see Servlets#configure() Configuring Guice Servlet.
 */
public class ServletModule extends AbstractModule {

  /**
   * @deprecated Don't construct this module directly,
   *  use {@link Servlets#configure} instead.
   */
  @Deprecated
  public ServletModule() {
    //encourage people to switch to the new system
    Logger.getLogger(ServletModule.class.getName())
        .warning("Directly using ServletModule (in the Guice 1.0 style) is "
                + "now deprecated. Prefer the use of Servlets.configure() "
                + "instead. Your application will still work as normal, "
                + "but you are encouraged to make the switch for additional"
                + " functionality and flexibility.");
  }

  @SuppressWarnings("UnusedDeclaration")
  ServletModule(boolean locallyCreated) {}

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

    //inject the pipeline into GuiceFilter so it can route requests correctly
    //Unfortunate staticness... =(
    requestStaticInjection(GuiceFilter.class);
  }
}
