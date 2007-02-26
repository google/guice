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
import com.google.inject.TypeLiteral;
import com.google.inject.Provider;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.SESSION;

import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Configures the servlet scopes and creates bindings for the servlet API
 * objects so you can inject the request, response, session, etc.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletModule extends AbstractModule {

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
  }
}
