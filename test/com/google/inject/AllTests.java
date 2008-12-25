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

import com.google.inject.internal.FinalizableReferenceQueueTest;
import com.google.inject.internal.LineNumbersTest;
import com.google.inject.internal.ReferenceCacheTest;
import com.google.inject.internal.ReferenceMapTest;
import com.google.inject.internal.ReferenceMapTestSuite;
import com.google.inject.internal.UniqueAnnotationsTest;
import com.google.inject.matcher.MatcherTest;
import com.google.inject.name.NamesTest;
import com.google.inject.spi.ElementsTest;
import com.google.inject.spi.HasDependenciesTest;
import com.google.inject.spi.InjectionPointTest;
import com.google.inject.spi.ModuleRewriterTest;
import com.google.inject.spi.ModuleWriterTest;
import com.google.inject.spi.ProviderMethodsTest;
import com.google.inject.spi.SpiBindingsTest;
import com.google.inject.util.ProvidersTest;
import com.google.inject.util.TypesTest;
import com.googlecode.guice.BytecodeGenTest;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class AllTests {

  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTestSuite(BinderTest.class);
    suite.addTest(BinderTestSuite.suite());
    suite.addTestSuite(BindingAnnotationTest.class);
    suite.addTestSuite(BindingOrderTest.class);
    suite.addTestSuite(BindingTest.class);
    suite.addTestSuite(BoundInstanceInjectionTest.class);
    suite.addTestSuite(BoundProviderTest.class);
    suite.addTestSuite(CircularDependencyTest.class);
    suite.addTestSuite(TypeConversionTest.class);
    // suite.addTestSuite(ErrorHandlingTest.class); not a testcase
    suite.addTestSuite(EagerSingletonTest.class);
    suite.addTestSuite(GenericInjectionTest.class);
    suite.addTestSuite(ImplicitBindingTest.class);
    suite.addTestSuite(InjectionPointTest.class);
    suite.addTestSuite(InjectorTest.class);
    suite.addTestSuite(IntegrationTest.class);
    suite.addTestSuite(KeyTest.class);
    suite.addTestSuite(LoggerInjectionTest.class);
    suite.addTestSuite(MethodInterceptionTest.class);
    suite.addTestSuite(ModuleTest.class);
    suite.addTestSuite(ModulesTest.class);
    suite.addTestSuite(NullableInjectionPointTest.class);
    suite.addTestSuite(OptionalBindingTest.class);
    suite.addTestSuite(OverrideModuleTest.class);
    suite.addTestSuite(ParentInjectorTest.class);
    suite.addTestSuite(PrivateModuleTest.class);
    suite.addTestSuite(ProviderInjectionTest.class);
    suite.addTestSuite(ProvisionExceptionTest.class);
    suite.addTestSuite(ProxyFactoryTest.class);
    suite.addTest(ReferenceMapTestSuite.suite());
    suite.addTestSuite(ReflectionTest.class);
    suite.addTestSuite(ScopesTest.class);
    suite.addTestSuite(SerializationTest.class);
    suite.addTestSuite(RequestInjectionTest.class);
    suite.addTestSuite(SuperclassTest.class);
    suite.addTestSuite(TypeLiteralInjectionTest.class);
    suite.addTestSuite(TypeLiteralTest.class);
    suite.addTestSuite(TypeLiteralTypeResolutionTest.class);

    // spi
    suite.addTestSuite(ElementsTest.class);
    suite.addTestSuite(HasDependenciesTest.class);
    suite.addTestSuite(ProviderMethodsTest.class);
    suite.addTestSuite(ModuleWriterTest.class);
    suite.addTestSuite(ModuleRewriterTest.class);
    suite.addTestSuite(SpiBindingsTest.class);

    // internal
    suite.addTestSuite(FinalizableReferenceQueueTest.class);
    suite.addTestSuite(LineNumbersTest.class);
    suite.addTestSuite(ReferenceCacheTest.class);
    suite.addTestSuite(ReferenceMapTest.class);
    suite.addTestSuite(TypesTest.class);
    suite.addTestSuite(UniqueAnnotationsTest.class);
    suite.addTestSuite(BytecodeGenTest.class);

    // matcher
    suite.addTestSuite(MatcherTest.class);

    // names
    suite.addTestSuite(NamesTest.class);
    
    // tools
    // suite.addTestSuite(JmxTest.class); not a testcase

    // util
    suite.addTestSuite(ProvidersTest.class);

    return suite;
  }
}
