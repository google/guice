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
import com.google.inject.Provider;
import com.google.inject.Scope;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * Servlet scopes.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletScopes {

  private ServletScopes() {}

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
            @SuppressWarnings("unchecked")
            T t = (T) request.getAttribute(name);
            if (t == null) {
              t = creator.get();
              request.setAttribute(name, t);
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
            @SuppressWarnings("unchecked")
            T t = (T) session.getAttribute(name);
            if (t == null) {
              t = creator.get();
              session.setAttribute(name, t);
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
}
