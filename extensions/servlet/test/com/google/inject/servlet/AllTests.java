/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.servlet;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class AllTests {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    // Filter tests.
    suite.addTestSuite(EdslTest.class);
    suite.addTestSuite(FilterDefinitionTest.class);
    suite.addTestSuite(FilterDispatchIntegrationTest.class);
    suite.addTestSuite(FilterPipelineTest.class);

    // Servlet + integration tests.
    suite.addTestSuite(ServletModuleTest.class);
    suite.addTestSuite(ServletTest.class);
    suite.addTestSuite(ServletDefinitionTest.class);
    suite.addTestSuite(ServletDefinitionPathsTest.class);
    suite.addTestSuite(ServletPipelineRequestDispatcherTest.class);
    suite.addTestSuite(ServletDispatchIntegrationTest.class);
    suite.addTestSuite(InvalidScopeBindingTest.class);

    // Varargs URL mapping tests.
    suite.addTestSuite(VarargsFilterDispatchIntegrationTest.class);
    suite.addTestSuite(VarargsServletDispatchIntegrationTest.class);

    // Multiple modules tests.
    suite.addTestSuite(MultiModuleDispatchIntegrationTest.class);
    
    // Extension SPI tests.
    suite.addTestSuite(ExtensionSpiTest.class);

    return suite;
  }
}
