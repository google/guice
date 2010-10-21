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

import com.google.inject.internal.util.FinalizableReferenceQueueTest;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Jsr166HashMapTest;
import com.google.inject.internal.util.LineNumbersTest;
import com.google.inject.internal.util.MapMakerTestSuite;
import com.google.inject.internal.MoreTypesTest;
import com.google.inject.internal.UniqueAnnotationsTest;
import com.google.inject.matcher.MatcherTest;
import com.google.inject.name.NamedEquivalanceTest;
import com.google.inject.name.NamesTest;
import com.google.inject.spi.BindingTargetVisitorTest;
import com.google.inject.spi.ElementApplyToTest;
import com.google.inject.spi.ElementsTest;
import com.google.inject.spi.HasDependenciesTest;
import com.google.inject.spi.InjectionPointTest;
import com.google.inject.spi.InjectorSpiTest;
import com.google.inject.spi.ModuleRewriterTest;
import com.google.inject.spi.ProviderMethodsTest;
import com.google.inject.spi.SpiBindingsTest;
import com.google.inject.spi.ToolStageInjectorTest;
import com.google.inject.util.NoopOverrideTest;
import com.google.inject.util.ProvidersTest;
import com.google.inject.util.TypesTest;
import com.googlecode.guice.Jsr330Test;
import com.googlecode.guice.GuiceTck;
import java.util.Enumeration;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class AllTests {

  private static final Set<String> SUPPRESSED_TEST_NAMES = ImmutableSet.of(
      "testUnscopedProviderWorksOutsideOfRequestedScope(" + ScopesTest.class.getName() + ")",
      "testCannotConvertUnannotatedBindings(" + TypeConversionTest.class.getName() + ")"
  );

  public static Test suite() {
    TestSuite suite = new TestSuite();

    suite.addTest(GuiceTck.suite());
    suite.addTestSuite(BinderTest.class);
    suite.addTest(BinderTestSuite.suite());
    suite.addTestSuite(BindingAnnotationTest.class);
    suite.addTestSuite(BindingOrderTest.class);
    suite.addTestSuite(BindingTest.class);
    suite.addTestSuite(BoundInstanceInjectionTest.class);
    suite.addTestSuite(BoundProviderTest.class);
    suite.addTestSuite(CircularDependencyTest.class);
    suite.addTestSuite(DuplicateBindingsTest.class);
    // ErrorHandlingTest.class is not a testcase
    suite.addTestSuite(EagerSingletonTest.class);
    suite.addTestSuite(GenericInjectionTest.class);
    suite.addTestSuite(ImplicitBindingTest.class);
    suite.addTestSuite(TypeListenerTest.class);
    suite.addTestSuite(InjectorTest.class);
    suite.addTestSuite(JitBindingsTest.class);
    // IntegrationTest is AOP-only
    suite.addTestSuite(KeyTest.class);
    suite.addTestSuite(LoggerInjectionTest.class);
    // MethodInterceptionTest is AOP-only
    suite.addTestSuite(MembersInjectorTest.class);
    suite.addTestSuite(ModulesTest.class);
    suite.addTestSuite(ModuleTest.class);
    suite.addTestSuite(NullableInjectionPointTest.class);
    suite.addTestSuite(OptionalBindingTest.class);
    suite.addTestSuite(OverrideModuleTest.class);
    suite.addTestSuite(ParentInjectorTest.class);
    suite.addTestSuite(PrivateModuleTest.class);
    suite.addTestSuite(ProviderInjectionTest.class);
    suite.addTestSuite(ProvisionExceptionTest.class);
    // ProxyFactoryTest is AOP-only
    suite.addTestSuite(ReflectionTest.class);
    suite.addTestSuite(RequestInjectionTest.class);
    suite.addTestSuite(ScopesTest.class);
    suite.addTestSuite(SerializationTest.class);
    suite.addTestSuite(SuperclassTest.class);
    suite.addTestSuite(TypeConversionTest.class);
    suite.addTestSuite(TypeLiteralInjectionTest.class);
    suite.addTestSuite(TypeLiteralTest.class);
    suite.addTestSuite(TypeLiteralTypeResolutionTest.class);

    // internal
    suite.addTestSuite(FinalizableReferenceQueueTest.class);
    suite.addTestSuite(Jsr166HashMapTest.class);
    suite.addTestSuite(LineNumbersTest.class);
    suite.addTest(MapMakerTestSuite.suite());
    suite.addTestSuite(MoreTypesTest.class);
    suite.addTestSuite(UniqueAnnotationsTest.class);

    // matcher
    suite.addTestSuite(MatcherTest.class);

    // names
    suite.addTestSuite(NamesTest.class);
    suite.addTestSuite(NamedEquivalanceTest.class);

    // spi
    suite.addTestSuite(BindingTargetVisitorTest.class);
    suite.addTestSuite(ElementsTest.class);
    suite.addTestSuite(ElementApplyToTest.class);
    suite.addTestSuite(HasDependenciesTest.class);
    suite.addTestSuite(InjectionPointTest.class);
    suite.addTestSuite(InjectorSpiTest.class);
    suite.addTestSuite(ModuleRewriterTest.class);
    suite.addTestSuite(ProviderMethodsTest.class);
    suite.addTestSuite(SpiBindingsTest.class);
    suite.addTestSuite(ToolStageInjectorTest.class);

    // tools
    // suite.addTestSuite(JmxTest.class); not a testcase

    // util
    suite.addTestSuite(NoopOverrideTest.class);
    suite.addTestSuite(ProvidersTest.class);
    suite.addTestSuite(TypesTest.class);

    /*if[AOP]*/
    suite.addTestSuite(com.google.inject.internal.ProxyFactoryTest.class);
    suite.addTestSuite(IntegrationTest.class);
    suite.addTestSuite(MethodInterceptionTest.class);
    suite.addTestSuite(com.googlecode.guice.BytecodeGenTest.class);
    /*end[AOP]*/

    // googlecode.guice
    suite.addTest(com.googlecode.guice.StrictContainerTestSuite.suite());
    suite.addTestSuite(com.googlecode.guice.OSGiContainerTest.class);
    suite.addTestSuite(Jsr330Test.class);

    return removeSuppressedTests(suite, SUPPRESSED_TEST_NAMES);
  }

  public static TestSuite removeSuppressedTests(TestSuite suite, Set<String> suppressedTestNames) {
    TestSuite result = new TestSuite(suite.getName());

    for(Enumeration e = suite.tests(); e.hasMoreElements(); ) {
      Test test = (Test) e.nextElement();

      if (suppressedTestNames.contains(test.toString())) {
        continue;
      }

      if (test instanceof TestSuite) {
        result.addTest(removeSuppressedTests((TestSuite) test, suppressedTestNames));
      } else {
        result.addTest(test);
      }
    }

    return result;
  }
}
