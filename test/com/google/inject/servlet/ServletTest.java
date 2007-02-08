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

import com.google.inject.Container;
import com.google.inject.ContainerBuilder;
import com.google.inject.ContainerCreationException;
import com.google.inject.Key;

import junit.framework.TestCase;

import static org.easymock.EasyMock.*;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ServletTest extends TestCase {

  public void testNewRequestObject()
      throws ContainerCreationException, IOException, ServletException {
    ContainerBuilder builder = new ContainerBuilder();
    builder.install(new ServletModule());
    builder.bind(InSession.class);
    builder.bind(InRequest.class);
    final Container container = builder.create(false);

    GuiceFilter filter = new GuiceFilter();

    final HttpServletRequest request = createMock(HttpServletRequest.class);

    String requestName = Key.get(InRequest.class).toString();
    expect(request.getAttribute(requestName)).andReturn(null);
    request.setAttribute(eq(requestName), isA(InRequest.class));

    final boolean[] invoked = new boolean[1];
    FilterChain filterChain = new FilterChain() {
      public void doFilter(ServletRequest servletRequest,
          ServletResponse servletResponse) {
        invoked[0] = true;
        assertSame(request, servletRequest);
        assertTrue(container.getInstance(InRequest.class) instanceof InRequest);
      }
    };

    replay(request);

    filter.doFilter(request, null, filterChain);

    verify(request);
    assertTrue(invoked[0]);
  }

  @SessionScoped
  static class InSession {}

  @RequestScoped
  static class InRequest {}
}
