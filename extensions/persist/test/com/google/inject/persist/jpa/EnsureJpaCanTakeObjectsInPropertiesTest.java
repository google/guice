/**
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

package com.google.inject.persist.jpa;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;

import junit.framework.TestCase;

import org.hibernate.cfg.Environment;
import org.hibernate.ejb.connection.InjectedDataSourceConnectionProvider;
import org.hsqldb.jdbc.JDBCDataSource;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.sql.DataSource;

public class EnsureJpaCanTakeObjectsInPropertiesTest extends TestCase {

  private Injector injector;

  public static class DBModule extends AbstractModule {

    final DataSource ds;
    final boolean passDataSource;

    DBModule(DataSource ds, boolean passDataSource) {
      this.ds = ds;
      this.passDataSource = passDataSource;
    }

    @Override
    protected void configure() {
      Map<String, Object> p = new HashMap<>();

      p.put(Environment.CONNECTION_PROVIDER, InjectedDataSourceConnectionProvider.class.getName());
      if (passDataSource) {
        p.put(Environment.DATASOURCE, ds);
      }

      JpaPersistModule jpaPersistModule = new JpaPersistModule("testProperties").properties(p);

      install(jpaPersistModule);
    }
  }

  @Override
  public void setUp() {
    injector = null;
  }

  @Override
  public final void tearDown() {
    if (injector == null) {
      return;
    }

    injector.getInstance(UnitOfWork.class).end();
    injector.getInstance(EntityManagerFactory.class).close();
  }

  private static DataSource getDataSource() {
    final JDBCDataSource dataSource = new JDBCDataSource();
    dataSource.setDatabase("jdbc:hsqldb:mem:persistence");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return dataSource;
  }

  private void startPersistService(boolean passDataSource) {
    final DataSource dataSource = getDataSource();

    injector = Guice.createInjector(new DBModule(dataSource, passDataSource));

    //startup persistence
    injector.getInstance(PersistService.class).start();
  }

  public void testWorksIfPassDataSource() {
    startPersistService(true);
  }

  public void testFailsIfNoDataSource() {
    try {
      startPersistService(false);
      fail();
    } catch (PersistenceException ex) {
      // Expected
      injector = null;
    }
  }

}
