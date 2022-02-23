/*
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

import com.google.inject.persist.jpa.providers.CustomPropsEntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.providers.DefaultPropsEntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.providers.EntityManagerPerRequestProvisionTest;
import com.google.inject.persist.jpa.providers.EntityManagerProvisionTest;
import com.google.inject.persist.jpa.transactions.ClassLevelManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.transactions.JoiningManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.transactions.ManualLocalTransactionsTest;
import com.google.inject.persist.jpa.transactions.MethodLevelManagedLocalTransactionsTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@RunWith(Suite.class)
@SuiteClasses({
    EdslTest.class,
    ClassLevelManagedLocalTransactionsTest.class,
    MethodLevelManagedLocalTransactionsTest.class,
    DefaultPropsEntityManagerFactoryProvisionTest.class,
    CustomPropsEntityManagerFactoryProvisionTest.class,
    EntityManagerPerRequestProvisionTest.class,
    EntityManagerProvisionTest.class,
    JoiningManagedLocalTransactionsTest.class,
    MethodLevelManagedLocalTransactionsTest.class,
    ManualLocalTransactionsTest.class,
    PersistFilterTest.class
})
public class AllTests {

}
