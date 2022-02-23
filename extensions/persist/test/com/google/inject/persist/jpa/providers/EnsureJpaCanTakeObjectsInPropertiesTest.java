/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.persist.jpa.providers;

import static com.google.inject.persist.utils.PersistenceUtils.withinUnitOfWork;

import com.google.inject.persist.jpa.JpaPersistModule;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import com.google.inject.persist.utils.SuiteAndTestResource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.persistence.PersistenceException;
import javax.sql.DataSource;
import org.hibernate.cfg.Environment;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnsureJpaCanTakeObjectsInPropertiesTest {

  @Parameters(name = "to datasource passed as param: {0} resulting in exception: {1}")
  public static Iterable<Object[]> parameters() {
    return Arrays.asList(
        new Object[] {true, null},
        new Object[] {false, PersistenceException.class});
  }

  private final boolean passDataSource;

  @Rule(order = -2)
  public ExpectedException expectedException = ExpectedException.none();

  public EnsureJpaCanTakeObjectsInPropertiesTest(boolean passDataSource,
                                                 Class<? extends Exception> expectedExceptionType) {
    this.passDataSource = passDataSource;
    if (expectedExceptionType != null) {
      expectedException.expect(expectedExceptionType);
    }
  }

  @Rule(order = -1)
  public PersistenceInjectorResource injector = new PersistenceInjectorResource(
      SuiteAndTestResource.Lifecycle.TEST,
      "testProperties",
      this::configureModule);

  private JpaPersistModule configureModule(JpaPersistModule jpaPersistModule) {
    Map<String, Object> properties = new HashMap<>();
    properties.put(Environment.CONNECTION_PROVIDER,
        DatasourceConnectionProviderImpl.class.getName());
    if (passDataSource) {
      properties.put(Environment.DATASOURCE, getDataSource());
    }
    return jpaPersistModule.properties(properties);
  }

  private static DataSource getDataSource() {
    final JDBCDataSource dataSource = new JDBCDataSource();
    dataSource.setDatabase("jdbc:hsqldb:mem:persistence");
    dataSource.setUser("sa");
    dataSource.setPassword("");
    return dataSource;
  }

  @Test
  public void testShouldReact() {
    withinUnitOfWork(injector, em -> {
    });
  }

}
