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

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import junit.framework.TestCase;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

public class InjectedHttpServletTest extends TestCase {

  private static class MyDependency {}

  private static class MyServlet extends InjectedHttpServlet {
    @Inject MyDependency myDependency;
  }

  static class MyListener extends GuiceServletContextListener {
    protected Injector getInjector() {
      return Guice.createInjector();
    }
  }

  public void test() throws ServletException {

    Map<String,Object> attributes = new HashMap<String,Object>();
    ServletContext context = createFakeServletContext(attributes);

    ServletConfig config = createMock(ServletConfig.class);
    expect(config.getServletContext()).andReturn(context).atLeastOnce();
    replay(config);

    GuiceServletContextListener listener = new MyListener();
    listener.contextInitialized(new ServletContextEvent(context));
    assertEquals(1, attributes.size());

    MyServlet servlet = new MyServlet();
    servlet.init(config);
    verify(config);
    assertNotNull(servlet.myDependency);

    listener.contextDestroyed(new ServletContextEvent(context));
    assertTrue(attributes.isEmpty());
  }

  private static ServletContext createFakeServletContext(
      final Map<String, Object> attributes) {
    InvocationHandler handler = new InvocationHandler() {
      public Object invoke(Object object, Method method, Object[] objects)
          throws Throwable {
        if (method.getName().equals("setAttribute")) {
          attributes.put((String) objects[0], objects[1]);
          return null;
        }
        if (method.getName().equals("getAttribute")) {
          return attributes.get(objects[0]);
        }
        if (method.getName().equals("removeAttribute")) {
          attributes.remove(objects[0]);
          return null;
        }
        throw new UnsupportedOperationException();
      }
    };

    return (ServletContext) Proxy.newProxyInstance(
        ServletContext.class.getClassLoader(),
        new Class[] { ServletContext.class },
        handler);
  }
}
