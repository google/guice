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

import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import java.util.concurrent.Callable;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Servlet scopes.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletScopes {

  private ServletScopes() {}

  /** A sentinel attribute value representing null. */
  enum NullObject { INSTANCE }

  /**
   * HTTP servlet request scope.
   */
  public static final Scope REQUEST = new Scope() {
    public <T> Provider<T> scope(Key<T> key, final Provider<T> creator) {
      final String name = key.toString();
      return new Provider<T>() {
        public T get() {
          HttpServletRequest request = GuiceFilter.getRequest();
          synchronized (request) {
            Object obj = request.getAttribute(name);
            if (NullObject.INSTANCE == obj) {
              return null;
            }
            @SuppressWarnings("unchecked")
            T t = (T) obj;
            if (t == null) {
              t = creator.get();
              request.setAttribute(name, (t != null) ? t : NullObject.INSTANCE);
            }
            return t;
          }
        }

        public String toString() {
          return String.format("%s[%s]", creator, REQUEST);
        }
      };
    }

    public String toString() {
      return "ServletScopes.REQUEST";
    }
  };

  /**
   * HTTP session scope.
   */
  public static final Scope SESSION = new Scope() {
    public <T> Provider<T> scope(Key<T> key, final Provider<T> creator) {
      final String name = key.toString();
      return new Provider<T>() {
        public T get() {
          HttpSession session = GuiceFilter.getRequest().getSession();
          synchronized (session) {
            Object obj = session.getAttribute(name);
            if (NullObject.INSTANCE == obj) {
              return null;
            }
            @SuppressWarnings("unchecked")
            T t = (T) obj;
            if (t == null) {
              t = creator.get();
              session.setAttribute(name, (t != null) ? t : NullObject.INSTANCE);
            }
            return t;
          }
        }
        public String toString() {
          return String.format("%s[%s]", creator, SESSION);
        }
      };
    }

    public String toString() {
      return "ServletScopes.SESSION";
    }
  };

  /**
   * Wraps the given callable in a contextual callable that "continues" the
   * HTTP request in another thread. This acts as a way of transporting
   * request context data from the request processing thread to to worker
   * threads.
   * <p>
   * There are some limitations:
   * <ul>
   *   <li>Derived objects (i.e. anything marked @RequestScoped will not be
   *      transported.</li>
   *   <li>State changes to the HttpServletRequest after this method is called
   *      will not be seen in the continued thread.</li>
   *   <li>Only the HttpServletRequest, ServletContext and request parameter
   *      map are available in the continued thread. The response and session
   *      are not available.</li>
   * </ul>
   *
   * @param callable code to be executed in another thread, which depends on
   *     the request scope.
   * @return a callable that will invoke the given callable, making the request
   *     context available to it.
   * @throws OutOfScopeException if this method is called from a non-request
   *     thread, or if the request has completed.
   */
  public static <T> Callable<T> continueRequest(final Callable<T> callable) {
    return new Callable<T>() {
      private HttpServletRequest request =
          new ContinuingHttpServletRequest(GuiceFilter.getRequest());

      public T call() throws Exception {
        GuiceFilter.Context context = GuiceFilter.localContext.get();
        if (null == context) {
          // Only set up the request continuation if we're running in a
          // new vanilla thread.
          GuiceFilter.localContext.set(new GuiceFilter.Context(request, null));
        }
        try {
          return callable.call();
        } finally {

          // Clear the copied context if we set one up.
          if (null == context) {
            GuiceFilter.localContext.remove();
          }
        }
      }
    };
  }
}
