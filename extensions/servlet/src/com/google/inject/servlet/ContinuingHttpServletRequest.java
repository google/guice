/*
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

import com.google.common.collect.Maps;
import com.google.inject.OutOfScopeException;
import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletInputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;

/**
 * A wrapper for requests that makes requests immutable, taking a snapshot of the original request.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
class ContinuingHttpServletRequest extends HttpServletRequestWrapper {

  // We clear out the attributes as they are mutable and not thread-safe.
  private final Map<String, Object> attributes = Maps.newHashMap();
  private final Cookie[] cookies;

  public ContinuingHttpServletRequest(HttpServletRequest request) {
    super(request);

    Cookie[] originalCookies = request.getCookies();
    if (originalCookies != null) {
      int numberOfCookies = originalCookies.length;
      cookies = new Cookie[numberOfCookies];
      for (int i = 0; i < numberOfCookies; i++) {
        Cookie originalCookie = originalCookies[i];

        // Snapshot each cookie + freeze.
        // No snapshot is required if this is a snapshot of a snapshot(!)
        if (originalCookie instanceof ImmutableCookie) {
          cookies[i] = originalCookie;
        } else {
          cookies[i] = new ImmutableCookie(originalCookie);
        }
      }
    } else {
      cookies = null;
    }
  }

  @Override
  public HttpSession getSession() {
    throw new OutOfScopeException("Cannot access the session in a continued request");
  }

  @Override
  public HttpSession getSession(boolean create) {
    throw new UnsupportedOperationException("Cannot access the session in a continued request");
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException("Cannot access raw request on a continued request");
  }

  @Override
  public void setAttribute(String name, Object o) {
    attributes.put(name, o);
  }

  @Override
  public void removeAttribute(String name) {
    attributes.remove(name);
  }

  @Override
  public Object getAttribute(String name) {
    return attributes.get(name);
  }

  @Override
  public Cookie[] getCookies() {
    // NOTE(user): Cookies themselves are mutable. However a ContinuingHttpServletRequest
    // snapshots the original set of cookies it received and imprisons them in immutable
    // form. Unfortunately, the cookie array itself is mutable and there is no way for us
    // to avoid this. At worst, however, mutation effects are restricted within the scope
    // of a single request. Continued requests are not affected after snapshot time.
    return cookies;
  }

  private static final class ImmutableCookie extends Cookie {
    public ImmutableCookie(Cookie original) {
      super(original.getName(), original.getValue());

      super.setMaxAge(original.getMaxAge());
      super.setPath(original.getPath());
      super.setComment(original.getComment());
      super.setSecure(original.getSecure());
      super.setValue(original.getValue());
      super.setVersion(original.getVersion());

      if (original.getDomain() != null) {
        super.setDomain(original.getDomain());
      }
    }

    @Override
    public void setComment(String purpose) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setDomain(String pattern) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setMaxAge(int expiry) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setPath(String uri) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setSecure(boolean flag) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setValue(String newValue) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }

    @Override
    public void setVersion(int v) {
      throw new UnsupportedOperationException("Cannot modify cookies on a continued request");
    }
  }
}
