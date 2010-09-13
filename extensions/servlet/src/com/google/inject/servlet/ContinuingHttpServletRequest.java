/**
 * Copyright (C) 2010 Google Inc.
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

import com.google.inject.OutOfScopeException;
import com.google.inject.internal.util.Maps;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

/**
 * A wrapper for requests that makes requests immutable, taking a snapshot
 * of the original request.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class ContinuingHttpServletRequest extends HttpServletRequestWrapper {

  // We clear out the attributes as they are mutable and not thread-safe.
  private final Map<String, Object> attributes = Maps.newHashMap();

  public ContinuingHttpServletRequest(HttpServletRequest request) {
    super(request);
  }

  @Override public HttpSession getSession() {
    throw new OutOfScopeException("Cannot access the session in a continued request");
  }

  @Override public HttpSession getSession(boolean create) {
    throw new UnsupportedOperationException("Cannot access the session in a continued request");
  }

  @Override public ServletInputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException("Cannot access raw request on a continued request");
  }

  @Override public void setAttribute(String name, Object o) {
    attributes.put(name, o);
  }

  @Override public void removeAttribute(String name) {
    attributes.remove(name);
  }

  @Override public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override public Cookie[] getCookies() {
    // TODO(dhanji): Cookies themselves are mutable. Is this a problem?
    return super.getCookies().clone();
  }
}
