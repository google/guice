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
import static org.junit.Assert.assertTrue;

import com.google.inject.name.Named;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.finder.FirstResult;
import com.google.inject.persist.finder.MaxResults;
import com.google.inject.persist.jpa.entities.JpaTestEntity;
import com.google.inject.persist.utils.PersistenceInjectorResource;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A test for dynamic finders
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */

public class DynamicFinderTest {

  private final JpaTestEntity testEntity = new JpaTestEntity("customName");
  private final JpaTestEntity anotherTestEntity = new JpaTestEntity("anotherName");

  @Rule
  @ClassRule
  public static PersistenceInjectorResource persistenceInjector =
      new PersistenceInjectorResource("testUnit",
          module -> module.addFinder(JpaFinder.class));

  @Before
  public void setUp() {
    withinTransaction(persistenceInjector, entityManager -> {
      entityManager.persist(testEntity);
      entityManager.persist(anotherTestEntity);
      entityManager.flush();
    });
  }

  @Test
  public void testDynamicFinderListAll() {
    List<JpaTestEntity> list = getFinder().listAll();
    assertEquals(list.size(), 2);
    assertTrue(list.contains(testEntity));
    assertTrue(list.contains(anotherTestEntity));
  }

  @Test
  public void testDynamicFinderListAllUsingNamedQuery() {
    List<JpaTestEntity> list = Arrays.asList(getFinder().listAllUsingNamedQuery());
    assertEquals(list.size(), 2);
    assertTrue(list.contains(testEntity));
    assertTrue(list.contains(anotherTestEntity));
  }

  @Test
  public void testDynamicFinderListWithParam() {
    List<JpaTestEntity> list = getFinder().listByName(testEntity.getText());
    assertEquals(list.size(), 1);
    assertEquals(testEntity, list.get(0));
  }

  @Test
  public void testDynamicFinderFindOne() {
    JpaTestEntity found = getFinder().findOne(testEntity.getId());
    assertEquals(testEntity, found);
  }

  @Test
  public void testDynamicFinderFindByText() {
    JpaTestEntity found = getFinder().findByText(anotherTestEntity.getText());
    assertEquals(anotherTestEntity, found);
  }

  @Test
  public void testDynamicFinderListOrderedByTextWithPaging() {
    List<JpaTestEntity> list = getFinder().listOrderedByText(1, 1);
    assertEquals(list.size(), 1);
    assertEquals(anotherTestEntity, list.get(0));
  }

  private JpaFinder getFinder() {
    return persistenceInjector.getInstance(JpaFinder.class);
  }

  public interface JpaFinder {
    @Finder(query = "SELECT e FROM JpaTestEntity e", returnAs = ArrayList.class)
    List<JpaTestEntity> listAll();

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.id = :id", returnAs = JpaTestEntity.class)
    JpaTestEntity findOne(@Named("id") Long id);

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.text = ?1", returnAs = JpaTestEntity.class)
    JpaTestEntity findByText(String text);

    @Finder(namedQuery = "JpaTestEntity.findAll", returnAs = JpaTestEntity[].class)
    JpaTestEntity[] listAllUsingNamedQuery();

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.text = :text", returnAs = ArrayList.class)
    List<JpaTestEntity> listByName(@javax.inject.Named("text") String textParam);

    @Finder(query = "SELECT e FROM JpaTestEntity e ORDER BY e.text DESC", returnAs = ArrayList.class)
    List<JpaTestEntity> listOrderedByText(@FirstResult int first, @MaxResults int max);
  }
}
