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

import com.google.inject.internal.MapMakerTestSuite;
import java.io.FilePermission;
import java.security.Permission;
import java.util.Arrays;
import junit.framework.Test;

/**
 * Runs a subset of our tests in a more secure environment. It loads the tests in another
 * classloader, and runs them with a specific security manager. Note that no security manager is in
 * place when test instances are constructed.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class StrictContainerTestSuite {

  public static Test suite() {
    SecurityManager securityManager = new SecurityManager() {
      @Override public void checkPermission(Permission permission) {
        if (permission instanceof FilePermission) {
          return;
        }

        if (permission instanceof RuntimePermission
            && permission.getName().equals("getClassLoader")
            && Arrays.toString(new Throwable().getStackTrace()).contains(".getSystemClassLoader(")) {
          throw new SecurityException("StrictContainerTestSuite forbids this!");
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
    return builder.build();
  }
}
