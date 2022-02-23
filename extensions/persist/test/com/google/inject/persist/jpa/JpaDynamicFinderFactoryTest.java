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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.inject.name.Named;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.finder.FirstResult;
import com.google.inject.persist.finder.MaxResults;
import com.google.inject.persist.jpa.entities.JpaTestEntity;
import com.google.inject.spi.Message;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Tests for finder validation logic.
 *
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
public class JpaDynamicFinderFactoryTest {

  private final JpaDynamicFinderFactory factory = new JpaDynamicFinderFactory();

  @Test
  public void testGivenInputIsNotInterface_whenCreate_thenError() {
    // when
    JpaDynamicFinderFactory.FinderCreationResult actual = factory.createFinder(Object.class);

    // then
    assertThat(actual.getErrors(), hasItem(findersMustBeInterfaces(Object.class)));
  }

  @Test
  public void testGivenFinderAnnotationMissing_whenCreate_thenError() {
    // when
    JpaDynamicFinderFactory.FinderCreationResult actual =
        factory.createFinder(FinderWithMissingAnnotation.class);

    // then
    assertThat(actual.getErrors(),
        hasItem(dynamicFindersMustBeAnnotated(FinderWithMissingAnnotation.class, "listByName")));
  }

  @Test
  public void testGivenValidFinderInterface_whenCreate_thenFinderProxyReturnedWithoutErrors() {
    // when
    JpaDynamicFinderFactory.FinderCreationResult actual =
        factory.createFinder(ValidJpaFinder.class);

    // then
    assertFalse(actual.hasErrors());
    assertNotNull(actual.getHandler());
  }

  private Message findersMustBeInterfaces(Class<?> finderClass) {
    return new Message(
        String.format("%s is not an interface. Dynamic Finders must be interfaces.",
            finderClass));
  }

  private Message dynamicFindersMustBeAnnotated(Class<?> finderClass, String methodName) {
    return new Message(
        String.format("Dynamic Finder methods must be annotated with @Finder, but %s.%s was not",
            finderClass,
            methodName));
  }

  interface FinderWithMissingAnnotation {
    List<JpaTestEntity> listByName(@Named("text") String textParam);
  }

  interface ValidJpaFinder {
    @Finder(query = "SELECT e FROM JpaTestEntity e", returnAs = ArrayList.class)
    List<JpaTestEntity> listAll();

    @Finder(namedQuery = "JpaTestEntity.findAll", returnAs = JpaTestEntity[].class)
    JpaTestEntity[] listAllUsingNamedQuery();

    @Finder(query = "SELECT e FROM JpaTestEntity e WHERE e.text = :text", returnAs = ArrayList.class)
    List<JpaTestEntity> listByName(@Named("text") String textParam);

    @Finder(query = "SELECT e FROM JpaTestEntity e ORDER BY e.text DESC", returnAs = ArrayList.class)
    List<JpaTestEntity> listOrderedByText(@FirstResult int first, @MaxResults int max);
  }
}
