/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import com.google.inject.matcher.MatcherTest;
import com.google.inject.util.FinalizableReferenceQueueTest;
import com.google.inject.util.ReferenceCacheTest;
import com.google.inject.util.ReferenceMapTest;
import com.google.inject.util.ReferenceMapTestSuite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class AllTests {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTestSuite(BoundProviderTest.class);
    suite.addTestSuite(CircularDependencyTest.class);
    suite.addTestSuite(ConstantConversionTest.class);
    suite.addTestSuite(InjectorTest.class);
    suite.addTestSuite(GenericInjectionTest.class);
    suite.addTestSuite(ImplicitBindingTest.class);
    suite.addTestSuite(KeyTest.class);
    suite.addTestSuite(ModuleTest.class);
    suite.addTestSuite(ProviderInjectionTest.class);
    suite.addTestSuite(NotRequiredTest.class);
    suite.addTestSuite(PreloadingTest.class);
    suite.addTestSuite(ProxyFactoryTest.class);
    suite.addTestSuite(ReflectionTest.class);
    suite.addTestSuite(ScopesTest.class);
    suite.addTestSuite(StaticInjectionTest.class);
    suite.addTestSuite(SuperclassTest.class);
    suite.addTestSuite(TypeLiteralTest.class);
    suite.addTestSuite(BoundInstanceInjectionTest.class);
    suite.addTestSuite(BindingAnnotationTest.class);

    suite.addTestSuite(MatcherTest.class);

    suite.addTestSuite(FinalizableReferenceQueueTest.class);
    suite.addTestSuite(ReferenceCacheTest.class);
    suite.addTestSuite(ReferenceMapTest.class);
    suite.addTest(ReferenceMapTestSuite.suite());

    suite.addTestSuite(IntegrationTest.class);

    return suite;
  }
}
