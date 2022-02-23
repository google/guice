/*
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist;

import static com.google.inject.persist.utils.PersistenceUtils.withinUnitOfWork;
import static org.junit.Assert.assertNotNull;

import com.google.inject.Inject;
import com.google.inject.persist.jpa.entities.JpaTestEntity;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.mockrunner.mock.web.WebMockObjectFactory;
import com.mockrunner.servlet.ServletTestModule;
import java.io.IOException;
import javax.persistence.EntityManager;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class PersistFilterTest {

  private final WebMockObjectFactory factory = new WebMockObjectFactory();
  private final ServletTestModule tester = new ServletTestModule(factory);

  @After
  public void tearDown() {
    tester.releaseFilters();
  }

  @Rule
  @ClassRule
  public static PersistenceInjectorResource persistenceInjector =
      new PersistenceInjectorResource("testUnit",
          module -> module.implicitUnitsOfWork(false))
          .autoStart(false);

  @Test
  public void testShouldWorkInServletContext() {
    // given
    PersistFilter filter = persistenceInjector.getInstance(PersistFilter.class);
    try {
      tester.addFilter(filter, true);
      Servlet servlet = tester.createServlet(ExampleServlet.class);
      persistenceInjector.injectMembers(servlet);
      tester.setDoChain(true);

      // when
      tester.doGet();
      Long entityId = Long.valueOf(tester.getOutput());

      // then
      withinUnitOfWork(persistenceInjector, em -> {
        JpaTestEntity newEntity = em.find(JpaTestEntity.class, entityId);
        assertNotNull(newEntity);
      });
    } finally {
      filter.destroy();
    }
  }

  public static class ExampleServlet extends HttpServlet {

    @Inject
    EntityManager entityManager;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
        throws IOException {
      entityManager.getTransaction().begin();
      JpaTestEntity entity = new JpaTestEntity("Hello world!");
      entityManager.persist(entity);
      entityManager.getTransaction().commit();
      resp.getWriter().write(String.valueOf(entity.getId()));
    }
  }
}
