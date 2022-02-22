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

package com.google.inject.persist.jpa;

import static com.google.inject.persist.utils.PersistenceUtils.withinTransaction;
import static org.junit.Assert.assertEquals;

import com.google.inject.name.Named;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A test for dynamic finders
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class DynamicFinderTest {

  @Rule
  @ClassRule
  public static PersistenceInjectorResource persistenceInjector =
      new PersistenceInjectorResource("testUnit",
          module -> module.addFinder(JpaFinder.class));

  @Test
  public void testDynamicFinderListAll() {
    withinTransaction(persistenceInjector, entityManager -> {
      JpaTestEntity te = new JpaTestEntity();
      te.setText("HIAjsOKAOSD" + new Date() + UUID.randomUUID());
      entityManager.persist(te);
      entityManager.flush();

      List<JpaTestEntity> list = persistenceInjector.getInstance(JpaFinder.class).listAll();
      assertEquals(te, list.get(0));
    });
  }

  @Test
  public void testDynamicFinderListWithParam() {
    withinTransaction(persistenceInjector, entityManager -> {
      JpaTestEntity te = new JpaTestEntity();
      te.setText("customName");
      entityManager.persist(te);

      JpaTestEntity te2 = new JpaTestEntity();
      te2.setText("anotherName");
      entityManager.persist(te2);

      entityManager.flush();

      List<JpaTestEntity> list = persistenceInjector.getInstance(JpaFinder.class).listByName("customName");
      assertEquals(list.size(), 1);
      assertEquals(te, list.get(0));
    });
  }

  public interface JpaFinder {
    @Finder(query = "SELECT e FROM JpaTestEntity e", returnAs = ArrayList.class)
    List<JpaTestEntity> listAll();

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.text = :text", returnAs = ArrayList.class)
    List<JpaTestEntity> listByName(@Named("text") String textParam);
  }
}
