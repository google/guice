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
import com.google.inject.Factory;
import com.google.inject.TypeLiteral;
import static com.google.inject.servlet.ServletScopes.REQUEST;
import static com.google.inject.servlet.ServletScopes.REQUEST_NAME;
import static com.google.inject.servlet.ServletScopes.SESSION;
import static com.google.inject.servlet.ServletScopes.SESSION_NAME;

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

  /**
   * Name of the request parameters binding. The type is {@code
   * Map<String, String[]>}.
   */
  public static final String REQUEST_PARAMETERS = "requestParameters";

  protected void configure() {
    // Scopes.
    scope(REQUEST_NAME, REQUEST);
    scope(SESSION_NAME, SESSION);

    // Bind request.
    Factory<HttpServletRequest> requestFactory =
        new Factory<HttpServletRequest>() {
      public HttpServletRequest get() {
        return GuiceFilter.getRequest();
      }
    };
    bind(HttpServletRequest.class).to(requestFactory);
    bind(ServletRequest.class).to(requestFactory);

    // Bind response.
    Factory<HttpServletResponse> responseFactory =
        new Factory<HttpServletResponse>() {
          public HttpServletResponse get() {
            return GuiceFilter.getResponse();
          }
        };
    bind(HttpServletResponse.class).to(responseFactory);
    bind(ServletResponse.class).to(responseFactory);

    // Bind session.
    bind(HttpSession.class).to(new Factory<HttpSession>() {
      public HttpSession get() {
        return GuiceFilter.getRequest().getSession();
      }
    });

    // Bind request parameters.
    bind(new TypeLiteral<Map<String, String[]>>() {})
        .named(REQUEST_PARAMETERS)
        .to(new Factory<Map<String, String[]>>() {
          @SuppressWarnings({"unchecked"})
          public Map<String, String[]> get() {
            return GuiceFilter.getRequest().getParameterMap();
          }
        });
  }
}
