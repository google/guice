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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.inject.persist.UnitOfWork;
import java.util.Properties;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** @author Dhanji R. Prasanna (dhanji@gmail.com) */

public class CustomPropsEntityManagerFactoryProvisionTest extends BaseEntityManagerFactoryProvisionTest {

  private static final String HIBERNATE_CONNECTION_URL = "hibernate.connection.url";
  private static final String CUSTOM_PERSISTENCE_URL = "jdbc:hsqldb:mem:customPersistence";

  @Override
  protected Properties getCustomProps() {
    Properties props = new Properties();
    props.put(HIBERNATE_CONNECTION_URL, CUSTOM_PERSISTENCE_URL);
    return props;
  }

  @Test
  public void testFactoryReceivesCustomProps() {
    //obtain em
    assertTrue(injector.getInstance(EntityManagerFactory.class).isOpen());
    assertTrue(injector.getInstance(EntityManager.class).isOpen());
    assertEquals(injector.getInstance(EntityManagerFactory.class).getProperties().get(HIBERNATE_CONNECTION_URL), CUSTOM_PERSISTENCE_URL);


  }
  
  @Before
  public void setUp() {
	  injector.getInstance(UnitOfWork.class).begin();
  }

  @After
  public void tearDown() {
    injector.getInstance(UnitOfWork.class).end();
  }
}
