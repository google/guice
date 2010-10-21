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

import com.google.inject.internal.util.Iterables;
import java.util.List;

import com.google.inject.name.Named;
import com.google.inject.name.Names;
import junit.framework.TestCase;

/**
 * @author crazybob@google.com (Bob Lee)
 */
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
    public void go() {}
  }

  static class AlternateImpl implements I {
    public void go() {}
  }

  public void testDefaultProvider() {
    Injector injector = Guice.createInjector();
    Provided provided = injector.getInstance(Provided.class);
    provided.go();
  }

  public void testBindingOverridesImplementedBy() {
    Injector injector = Guice.createInjector(new AbstractModule() {
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
      Asserts.assertContains(expected.getMessage(),
          "1) No implementation for " + I.class.getName(),
          "annotated with @" + Named.class.getName() + "(value=i) was bound.",
          "while locating " + I.class.getName(),
          " annotated with @" + Named.class.getName() + "(value=i)");
    }
  }

  static class ProvidedProvider implements Provider<Provided> {
    public Provided get() {
      return new Provided() {
        public void go() {}
      };
    }
  }

  /**
   * When we're building the binding for A, we temporarily insert that binding to support circular
   * dependencies. And so we can successfully create a binding for B. But later, when the binding
   * for A ultimately fails, we need to clean up the dependent binding for B.
   * 
   * The test loops through linked bindings & bindings with constructor & member injections,
   * to make sure that all are cleaned up and traversed.  It also makes sure we don't touch
   * explicit bindings.
   */
  public void testCircularJitBindingsLeaveNoResidue() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Valid.class);
        bind(Valid2.class);
      }
    });
    
    // Capture good bindings.
    Binding v1 = injector.getBinding(Valid.class);
    Binding v2 = injector.getBinding(Valid2.class);
    Binding jv1 = injector.getBinding(JitValid.class);
    Binding jv2 = injector.getBinding(JitValid2.class);

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
  
  @SuppressWarnings("unchecked")
  private void assertFailure(Injector injector, Class clazz) {
    try {
      injector.getBinding(clazz);
      fail("Shouldn't have been able to get binding of: " + clazz);
    } catch(ConfigurationException expected) {
      List<Object> sources = Iterables.getOnlyElement(expected.getErrorMessages()).getSources();
      // Assert that the first item in the sources if the key for the class we're looking up,
      // ensuring that each lookup is "new".
      assertEquals(Key.get(clazz).toString(), sources.get(0).toString());
      // Assert that the last item in each lookup contains the InvalidInterface class
      Asserts.assertContains(sources.get(sources.size()-1).toString(),
          Key.get(InvalidInterface.class).toString());
    }
  }

  static class Invalid {
    @Inject Valid a;
    @Inject JitValid b;    
    @Inject InvalidProvidedBy c; 
    @Inject Invalid(InvalidLinked a) {}    
    @Inject void foo(InvalidInterface a) {}
    
  }

  @ImplementedBy(InvalidLinkedImpl.class)
  static interface InvalidLinked {}
  static class InvalidLinkedImpl implements InvalidLinked {
    @Inject InvalidLinked2 a;
  }
  
  @ImplementedBy(InvalidLinked2Impl.class)
  static interface InvalidLinked2 {}
  static class InvalidLinked2Impl implements InvalidLinked2 {
    @Inject InvalidLinked2Impl(Invalid2 a) {}
  }
  
  @ProvidedBy(InvalidProvidedByProvider.class)
  static interface InvalidProvidedBy {}
  static class InvalidProvidedByProvider implements Provider<InvalidProvidedBy> {
    @Inject InvalidProvidedBy2 a;
    public InvalidProvidedBy get() {
      return null;
    }
  }
  
  @ProvidedBy(InvalidProvidedBy2Provider.class)
  static interface InvalidProvidedBy2 {}
  static class InvalidProvidedBy2Provider implements Provider<InvalidProvidedBy2> {
    @Inject Invalid2 a;
    public InvalidProvidedBy2 get() {
      return null;
    }
  }  
  
  static class Invalid2 {
    @Inject Invalid a;
  }

  interface InvalidInterface {}
  
  static class Valid { @Inject Valid2 a; }
  static class Valid2 {}
  
  static class JitValid { @Inject JitValid2 a; }
  static class JitValid2 {}
  
  /**
   * Regression test for http://code.google.com/p/google-guice/issues/detail?id=319
   * 
   * The bug is that a class that asks for a provider for itself during injection time, 
   * where any one of the other types required to fulfill the object creation was bound 
   * in a child constructor, explodes when the injected Provider is called.
   * 
   * It works just fine when the other types are bound in a main injector.
   */  
  public void testInstancesRequestingProvidersForThemselvesWithChildInjectors() {       
    final Module testModule = new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class)
          .toProvider(TestStringProvider.class);
      }            
    };    
    
    // Verify it works when the type is setup in the parent.
    Injector parentSetupRootInjector = Guice.createInjector(testModule);
    Injector parentSetupChildInjector = parentSetupRootInjector.createChildInjector();
    assertEquals(TestStringProvider.TEST_VALUE, 
        parentSetupChildInjector.getInstance(
            RequiresProviderForSelfWithOtherType.class).getValue());
        
    // Verify it works when the type is setup in the child, not the parent.
    // If it still occurs, the bug will explode here.
    Injector childSetupRootInjector = Guice.createInjector();
    Injector childSetupChildInjector = childSetupRootInjector.createChildInjector(testModule);      
    assertEquals(TestStringProvider.TEST_VALUE, 
        childSetupChildInjector.getInstance(
            RequiresProviderForSelfWithOtherType.class).getValue());
  }
  
  static class TestStringProvider implements Provider<String> {
    static final String TEST_VALUE = "This is to verify it all works";
    
    public String get() {
      return TEST_VALUE;
    }    
  }    
  
  static class RequiresProviderForSelfWithOtherType {
    private final Provider<RequiresProviderForSelfWithOtherType> selfProvider;
    private final String providedStringValue;
    
    @Inject    
    RequiresProviderForSelfWithOtherType(
        String providedStringValue,
        Provider<RequiresProviderForSelfWithOtherType> selfProvider
        ) {
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

}
