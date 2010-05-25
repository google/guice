/**
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

package com.google.inject.persist;

import com.google.inject.persist.jpa.ClassLevelManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.CustomPropsEntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.EntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.EntityManagerPerRequestProvisionTest;
import com.google.inject.persist.jpa.EntityManagerProvisionTest;
import com.google.inject.persist.jpa.JoiningLocalTransactionsTest;
import com.google.inject.persist.jpa.JpaWorkManagerTest;
import com.google.inject.persist.jpa.ManagedLocalTransactionsAcrossRequestTest;
import com.google.inject.persist.jpa.ManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.ManualLocalTransactionsTest;
import com.google.inject.persist.jpa.ManualLocalTransactionsWithCustomMatcherTest;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AllTests {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTestSuite(EdslTest.class);
    suite.addTestSuite(ClassLevelManagedLocalTransactionsTest.class);
    suite.addTestSuite(CustomPropsEntityManagerFactoryProvisionTest.class);
    suite.addTestSuite(EntityManagerFactoryProvisionTest.class);
    suite.addTestSuite(EntityManagerPerRequestProvisionTest.class);
    suite.addTestSuite(EntityManagerProvisionTest.class);
    suite.addTestSuite(JoiningLocalTransactionsTest.class);
    suite.addTestSuite(JpaWorkManagerTest.class);
    suite.addTestSuite(ManagedLocalTransactionsAcrossRequestTest.class);
    suite.addTestSuite(ManagedLocalTransactionsTest.class);
    suite.addTestSuite(ManualLocalTransactionsTest.class);
    suite.addTestSuite(ManualLocalTransactionsWithCustomMatcherTest.class);

    return suite;
  }
}