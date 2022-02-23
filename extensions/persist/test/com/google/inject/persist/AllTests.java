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

import com.google.inject.persist.jpa.ClassLevelManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.CustomPropsEntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.DefaultPropsEntityManagerFactoryProvisionTest;
import com.google.inject.persist.jpa.EntityManagerPerRequestProvisionTest;
import com.google.inject.persist.jpa.EntityManagerProvisionTest;
import com.google.inject.persist.jpa.JoiningLocalTransactionsTest;
import com.google.inject.persist.jpa.JpaWorkManagerTest;
import com.google.inject.persist.jpa.MethodLevelManagedLocalTransactionsTest;
import com.google.inject.persist.jpa.ManualLocalTransactionsTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

/** @author dhanji@gmail.com (Dhanji R. Prasanna) */
@RunWith(Suite.class)
@SuiteClasses({
    EdslTest.class,
	ClassLevelManagedLocalTransactionsTest.class,
	MethodLevelManagedLocalTransactionsTest.class,
    DefaultPropsEntityManagerFactoryProvisionTest.class,
	CustomPropsEntityManagerFactoryProvisionTest.class,
    EntityManagerPerRequestProvisionTest.class,
    EntityManagerProvisionTest.class,
    JoiningLocalTransactionsTest.class,
    JpaWorkManagerTest.class,
    MethodLevelManagedLocalTransactionsTest.class,
    ManualLocalTransactionsTest.class
})
public class AllTests {

}
