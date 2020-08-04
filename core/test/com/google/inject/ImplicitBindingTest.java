/*
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

import com.google.common.collect.Iterables;
import com.google.inject.internal.Annotations;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;
import java.util.List;
import junit.framework.TestCase;

/** @author crazybob@google.com (Bob Lee) */
public class ImplicitBindingTest extends TestCase {

  public void testCircularDependency() throws CreationException {
    Injector injector = Guice.createInjector();
    Foo foo = injector.getInstance(Foo.class);
    assertSame(foo, foo.bar.foo);
  }

  static class Foo {
    @Inject Bar bar;
  }

  static class Bar {
    final Foo foo;

    @Inject
    public Bar(Foo foo) {
      this.foo = foo;
    }
  }

  public void testDefaultImplementation() {
    Injector injector = Guice.createInjector();
    I i = injector.getInstance(I.class);
    i.go();
  }

  @ImplementedBy(IImpl.class)
  interface I {
    void go();
  }

  static class IImpl implements I {
    @Override
    public void go() {}
  }

  static class AlternateImpl implements I {
    @Override
    public void go() {}
  }

  public void testDefaultProvider() {
    Injector injector = Guice.createInjector();
    Provided provided = injector.getInstance(Provided.class);
    provided.go();
  }

  public void testBindingOverridesImplementedBy() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(I.class).to(AlternateImpl.class);
              }
            });
    assertEquals(AlternateImpl.class, injector.getInstance(I.class).getClass());
  }

  @ProvidedBy(ProvidedProvider.class)
  interface Provided {
    void go();
  }

  public void testNoImplicitBindingIsCreatedForAnnotatedKeys() {
    try {
      Guice.createInjector().getInstance(Key.get(I.class, Names.named("i")));
      fail();
    } catch (ConfigurationException expected) {
      Asserts.assertContains(
          expected.getMessage(),
          "1) No implementation for " + I.class.getName(),
          "annotated with @"
              + Named.class.getName()
              + "(value="
              + Annotations.memberValueString("i")
              + ") was bound.",
          "while locating " + I.class.getName(),
          " annotated with @"
              + Named.class.getName()
              + "(value="
              + Annotations.memberValueString("i")
              + ")");
    }
  }

  static class ProvidedProvider implements Provider<Provided> {
    @Override
    public Provided get() {
      return new Provided() {
        @Override
        public void go() {}
      };
    }
  }

  /**
   * When we're building the binding for A, we temporarily insert that binding to support circular
   * dependencies. And so we can successfully create a binding for B. But later, when the binding
   * for A ultimately fails, we need to clean up the dependent binding for B.
   *
   * <p>The test loops through linked bindings & bindings with constructor & member injections, to
   * make sure that all are cleaned up and traversed. It also makes sure we don't touch explicit
   * bindings.
   */
  public void testCircularJitBindingsLeaveNoResidue() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Valid.class);
                bind(Valid2.class);
              }
            });

    // Capture good bindings.
    Binding<Valid> v1 = injector.getBinding(Valid.class);
    Binding<Valid2> v2 = injector.getBinding(Valid2.class);
    Binding<JitValid> jv1 = injector.getBinding(JitValid.class);
    Binding<JitValid2> jv2 = injector.getBinding(JitValid2.class);

    // Then validate that a whole series of invalid bindings are erased.
    assertFailure(injector, Invalid.class);
    assertFailure(injector, InvalidLinked.class);
    assertFailure(injector, InvalidLinkedImpl.class);
    assertFailure(injector, InvalidLinked2.class);
    assertFailure(injector, InvalidLinked2Impl.class);
    assertFailure(injector, InvalidProvidedBy.class);
    assertFailure(injector, InvalidProvidedByProvider.class);
    assertFailure(injector, InvalidProvidedBy2.class);
    assertFailure(injector, InvalidProvidedBy2Provider.class);
    assertFailure(injector, Invalid2.class);

    // Validate we didn't do anything to the valid explicit bindings.
    assertSame(v1, injector.getBinding(Valid.class));
    assertSame(v2, injector.getBinding(Valid2.class));

    // Validate that we didn't erase the valid JIT bindings
    assertSame(jv1, injector.getBinding(JitValid.class));
    assertSame(jv2, injector.getBinding(JitValid2.class));
  }

  private static void assertFailure(Injector injector, Class<?> clazz) {
    try {
      injector.getBinding(clazz);
      fail("Shouldn't have been able to get binding of: " + clazz);
    } catch (ConfigurationException expected) {
      Message msg = Iterables.getOnlyElement(expected.getErrorMessages());
      Asserts.assertContains(
          msg.getMessage(),
          "No implementation for " + InvalidInterface.class.getName() + " was bound.");
      List<Object> sources = msg.getSources();
      // Assert that the first item in the sources if the key for the class we're looking up,
      // ensuring that each lookup is "new".
      assertEquals(Key.get(clazz).toString(), sources.get(0).toString());
      // Assert that the last item in each lookup contains the InvalidInterface class
      Asserts.assertContains(
          sources.get(sources.size() - 1).toString(), Key.get(InvalidInterface.class).toString());
    }
  }

  static class Invalid {
    @Inject Valid a;
    @Inject JitValid b;
    @Inject InvalidProvidedBy c;

    @Inject
    Invalid(InvalidLinked a) {}

    @Inject
    void foo(InvalidInterface a) {}
  }

  @ImplementedBy(InvalidLinkedImpl.class)
  static interface InvalidLinked {}

  static class InvalidLinkedImpl implements InvalidLinked {
    @Inject InvalidLinked2 a;
  }

  @ImplementedBy(InvalidLinked2Impl.class)
  static interface InvalidLinked2 {}

  static class InvalidLinked2Impl implements InvalidLinked2 {
    @Inject
    InvalidLinked2Impl(Invalid2 a) {}
  }

  @ProvidedBy(InvalidProvidedByProvider.class)
  static interface InvalidProvidedBy {}

  static class InvalidProvidedByProvider implements Provider<InvalidProvidedBy> {
    @Inject InvalidProvidedBy2 a;

    @Override
    public InvalidProvidedBy get() {
      return null;
    }
  }

  @ProvidedBy(InvalidProvidedBy2Provider.class)
  static interface InvalidProvidedBy2 {}

  static class InvalidProvidedBy2Provider implements Provider<InvalidProvidedBy2> {
    @Inject Invalid2 a;

    @Override
    public InvalidProvidedBy2 get() {
      return null;
    }
  }

  static class Invalid2 {
    @Inject Invalid a;
  }

  interface InvalidInterface {}

  static class Valid {
    @Inject Valid2 a;
  }

  static class Valid2 {}

  static class JitValid {
    @Inject JitValid2 a;
  }

  static class JitValid2 {}

  /**
   * Regression test for https://github.com/google/guice/issues/319
   *
   * <p>The bug is that a class that asks for a provider for itself during injection time, where any
   * one of the other types required to fulfill the object creation was bound in a child
   * constructor, explodes when the injected Provider is called.
   *
   * <p>It works just fine when the other types are bound in a main injector.
   */
  public void testInstancesRequestingProvidersForThemselvesWithChildInjectors() {
    final Module testModule =
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(String.class).toProvider(TestStringProvider.class);
          }
        };

    // Verify it works when the type is setup in the parent.
    Injector parentSetupRootInjector = Guice.createInjector(testModule);
    Injector parentSetupChildInjector = parentSetupRootInjector.createChildInjector();
    assertEquals(
        TestStringProvider.TEST_VALUE,
        parentSetupChildInjector
            .getInstance(RequiresProviderForSelfWithOtherType.class)
            .getValue());

    // Verify it works when the type is setup in the child, not the parent.
    // If it still occurs, the bug will explode here.
    Injector childSetupRootInjector = Guice.createInjector();
    Injector childSetupChildInjector = childSetupRootInjector.createChildInjector(testModule);
    assertEquals(
        TestStringProvider.TEST_VALUE,
        childSetupChildInjector.getInstance(RequiresProviderForSelfWithOtherType.class).getValue());
  }

  static class TestStringProvider implements Provider<String> {
    static final String TEST_VALUE = "This is to verify it all works";

    @Override
    public String get() {
      return TEST_VALUE;
    }
  }

  static class RequiresProviderForSelfWithOtherType {
    private final Provider<RequiresProviderForSelfWithOtherType> selfProvider;
    private final String providedStringValue;

    @Inject
    RequiresProviderForSelfWithOtherType(
        String providedStringValue, Provider<RequiresProviderForSelfWithOtherType> selfProvider) {
      this.providedStringValue = providedStringValue;
      this.selfProvider = selfProvider;
    }

    public String getValue() {
      // Attempt to get another instance of ourself. This pattern
      // is possible for recursive processing.
      selfProvider.get();

      return providedStringValue;
    }
  }

  /**
   * Ensure that when we cleanup failed JIT bindings, we don't break. The test here requires a
   * sequence of JIT bindings:
   *
   * <ol>
   * <li> A-> B
   * <li> B -> C, A
   * <li> C -> A, D
   * <li> D not JITable
   * </ol>
   *
   * <p>The problem was that C cleaned up A's binding and then handed control back to B, which tried
   * to continue processing A.. but A was removed from the jitBindings Map, so it attempts to create
   * a new JIT binding for A, but we haven't yet finished constructing the first JIT binding for A,
   * so we get a recursive computation exception from ComputingConcurrentHashMap.
   *
   * <p>We also throw in a valid JIT binding, E, to guarantee that if something fails in this flow,
   * it can be recreated later if it's not from a failed sequence.
   */
  public void testRecursiveJitBindingsCleanupCorrectly() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(A.class);
      fail("Expected failure");
    } catch (ConfigurationException expected) {
      Message msg = Iterables.getOnlyElement(expected.getErrorMessages());
      Asserts.assertContains(
          msg.getMessage(),
          "No implementation for "
              + D.class.getName()
              + " (with no qualifier annotation) was bound, and could not find an injectable"
              + " constructor");
    }
    // Assert that we've removed all the bindings.
    assertNull(injector.getExistingBinding(Key.get(A.class)));
    assertNull(injector.getExistingBinding(Key.get(B.class)));
    assertNull(injector.getExistingBinding(Key.get(C.class)));
    assertNull(injector.getExistingBinding(Key.get(D.class)));

    // Confirm that we didn't prevent 'E' from working.
    assertNotNull(injector.getBinding(Key.get(E.class)));
  }

  static class A {
    @Inject
    public A(B b) {}
  }

  static class B {
    @Inject
    public B(C c, A a) {}
  }

  static class C {
    @Inject
    public C(A a, D d, E e) {}
  }

  static class D {
    public D(int i) {}
  }

  // Valid JITable binding
  static class E {}

  public void testProvidedByNonEmptyEnum() {
    NonEmptyEnum cardSuit = Guice.createInjector().getInstance(NonEmptyEnum.class);

    assertEquals(NonEmptyEnum.HEARTS, cardSuit);
  }

  public void testProvidedByEmptyEnum() {
    EmptyEnum emptyEnumValue = Guice.createInjector().getInstance(EmptyEnum.class);
    assertNull(emptyEnumValue);
  }

  @ProvidedBy(NonEmptyEnumProvider.class)
  enum NonEmptyEnum {
    HEARTS,
    DIAMONDS,
    CLUBS,
    SPADES
  }

  static final class NonEmptyEnumProvider implements Provider<NonEmptyEnum> {
    @Override
    public NonEmptyEnum get() {
      return NonEmptyEnum.HEARTS;
    }
  }

  @ProvidedBy(EmptyEnumProvider.class)
  enum EmptyEnum {}

  static final class EmptyEnumProvider implements Provider<EmptyEnum> {
    @Override
    public EmptyEnum get() {
      return null;
    }
  }

  // An enum cannot be implemented by anything, so it should not be possible to have a successful
  // binding when the enum is annotated with @ImplementedBy.
  public void testImplementedByEnum() {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(EnumWithImplementedBy.class);
      fail("Expected failure");
    } catch (ConfigurationException expected) {
      Message msg = Iterables.getOnlyElement(expected.getErrorMessages());
      Asserts.assertContains(
          msg.getMessage(),
          "No implementation for " + EnumWithImplementedBy.class.getName() + " was bound.");
    }
  }

  @ImplementedBy(EnumWithImplementedByEnum.class)
  enum EnumWithImplementedBy {}

  private static class EnumWithImplementedByEnum {}

  public void testImplicitJdkBindings() {
    Injector injector = Guice.createInjector();
    // String has a public nullary constructor, so Guice will call it.
    assertEquals("", injector.getInstance(String.class));
    // InetAddress has a package private constructor.  We probably shouldn't be calling it :(
    assertNotNull(injector.getInstance(java.net.InetAddress.class));
  }
}
