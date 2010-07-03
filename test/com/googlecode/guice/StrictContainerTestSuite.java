/**
 * Copyright (C) 2009 Google Inc.
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

package com.googlecode.guice;

import com.google.inject.AllTests;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.MapMakerTestSuite;
import com.google.inject.internal.util.MapMakerTestSuite.ReferenceMapTest;
import com.google.inject.internal.util.MapMakerTestSuite.ComputingTest;
import java.io.FilePermission;
import java.security.AccessControlException;
import java.security.Permission;
import java.util.Arrays;
import java.util.PropertyPermission;
import java.util.Set;
import junit.framework.Test;

/**
 * Runs a subset of our tests in a more secure environment. It loads the tests in another
 * classloader, and runs them with a specific security manager. Note that no security manager is in
 * place when test instances are constructed.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class StrictContainerTestSuite {

  /** Tests tests require background threads to pass, which the strict container forbids */
  private static final Set<String> SUPPRESSED_TEST_NAMES = ImmutableSet.of(
      "testValueCleanupWithWeakKey(" + ReferenceMapTest.class.getName() + ")",
      "testValueCleanupWithSoftKey(" + ReferenceMapTest.class.getName() + ")",
      "testKeyCleanupWithWeakKey(" + ReferenceMapTest.class.getName() + ")",
      "testKeyCleanupWithSoftKey(" + ReferenceMapTest.class.getName() + ")",
      "testKeyCleanupWithWeakValue(" + ReferenceMapTest.class.getName() + ")",
      "testKeyCleanupWithSoftValue(" + ReferenceMapTest.class.getName() + ")",
      "testInternedValueCleanupWithWeakKey(" + ReferenceMapTest.class.getName() + ")",
      "testInternedValueCleanupWithSoftKey(" + ReferenceMapTest.class.getName() + ")",
      "testInternedKeyCleanupWithWeakValue(" + ReferenceMapTest.class.getName() + ")",
      "testInternedKeyCleanupWithSoftValue(" + ReferenceMapTest.class.getName() + ")",
      "testSleepConcurrency(" + ComputingTest.class.getName() + ")",
      "testBusyConcurrency(" + ComputingTest.class.getName() + ")",
      "testFastConcurrency(" + ComputingTest.class.getName() + ")",
      "testSleepCanonical(" + ComputingTest.class.getName() + ")",
      "testBusyCanonical(" + ComputingTest.class.getName() + ")",
      "testFastCanonical(" + ComputingTest.class.getName() + ")"
  );

  public static Test suite() {
    SecurityManager securityManager = new SecurityManager() {
      @Override public void checkPermission(Permission permission) {
        if (permission instanceof FilePermission
            || permission instanceof PropertyPermission) {
          return; // avoid creating a stacktrace for common permissions
        }

        String stacktrace = Arrays.toString(new Throwable().getStackTrace());
        if (stacktrace.contains("Thread.<init>")
            || stacktrace.contains(".getSystemClassLoader(")) {
          throw new AccessControlException("StrictContainerTestSuite forbids this!");
        }
      }

      @Override public void checkPermission(Permission permission, Object context) {
        checkPermission(permission);
      }
    };

    StrictContainerTestSuiteBuilder builder = new StrictContainerTestSuiteBuilder(securityManager);
    /*if[AOP]*/
    builder.add(BytecodeGenTest.class.getName());
    /*end[AOP]*/
    builder.addSuite(MapMakerTestSuite.class.getName());

    return AllTests.removeSuppressedTests(builder.build(), SUPPRESSED_TEST_NAMES);
  }
}
