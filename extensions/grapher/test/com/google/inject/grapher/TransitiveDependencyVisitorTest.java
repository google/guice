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

package com.google.inject.grapher;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.name.Names;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import java.util.Collection;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link TransitiveDependencyVisitor}.
 *
 * @author phopkins@gmail.com (Pete Hopkins)
 */
public class TransitiveDependencyVisitorTest extends TestCase {
  private TransitiveDependencyVisitor visitor;
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    
    visitor = new TransitiveDependencyVisitor();
  }

  public void testVisitConstructor() {
    Binding<?> binding = getBinding(Key.get(ConstructedClass.class));
    Collection<Key<?>> dependencies = visitor.visit((ConstructorBinding<?>) binding);
    
    assertDependencies(dependencies, Key.get(A.class), Key.get(B.class), Key.get(C.class),
        Key.get(D.class));
  }

  public void testVisitConvertedConstant() {
    Binding<?> binding = getBinding(Key.get(Integer.class, Names.named("number")),
        new ConvertedConstantModule());
    Collection<Key<?>> dependencies = visitor.visit(
        (ConvertedConstantBinding<?>) binding);
    
    assertDependencies(dependencies, Key.get(String.class, Names.named("number")));
  }

  public void testVisitInstance() {
    Binding<?> binding = getBinding(Key.get(ConstructedClass.class), new InstanceModule());
    Collection<Key<?>> dependencies = visitor.visit(
        (InstanceBinding<?>) binding);
    
    // Dependencies will only be on the field- and method-injected classes.
    assertDependencies(dependencies, Key.get(A.class), Key.get(D.class));
  }

  public void testVisitInstance_instanceHasDependencies() {
    Binding<?> binding = getBinding(Key.get(Interface.class), new HasDependenciesModule());
    Collection<Key<?>> dependencies = visitor.visit(
        (InstanceBinding<?>) binding);
    
    // Dependencies should only be on the stated
    // HasDependencies#getDependencies() values
    assertDependencies(dependencies, Key.get(G.class));
  }

  public void testVisitLinkedKey() {
    Binding<?> binding = getBinding(Key.get(Interface.class), new LinkedKeyModule());
    Collection<Key<?>> dependencies = visitor.visit((LinkedKeyBinding<?>) binding);

    // Dependency should be to the class this interface is bound to.
    assertDependencies(dependencies, Key.get(ConstructedClass.class));
  }

  public void testVisitProviderBinding() {
    Binding<?> binding = getBinding(Key.get(new TypeLiteral<Provider<ConstructedClass>>() {}));
    Collection<Key<?>> dependencies = visitor.visit((ProviderBinding<?>) binding);
    
    assertDependencies(dependencies, Key.get(ConstructedClass.class));
  }

  public void testVisitProviderInstance() {
    Binding<?> binding = getBinding(Key.get(ConstructedClass.class),
        new ProviderInstanceModule());
    Collection<Key<?>> dependencies = visitor.visit(
        (ProviderInstanceBinding<?>) binding);
    
    // Dependencies will only be on the field- and method-injected classes.
    assertDependencies(dependencies, Key.get(E.class), Key.get(F.class));
  }

  public void testVisitProviderKey() {
    Binding<?> binding = getBinding(Key.get(ConstructedClass.class), new ProviderKeyModule());
    Collection<Key<?>> dependencies = visitor.visit((ProviderKeyBinding<?>) binding);

    // Dependency should be to the class that provides this one.
    assertDependencies(dependencies, Key.get(ConstructedClassProvider.class));
  }
  
  private Binding<?> getBinding(Key<?> key, Module... modules) {
    return Guice.createInjector(modules).getBinding(key);
  }
  
  private void assertDependencies(Collection<Key<?>> dependencies, Key<?>... keys) {
    assertNotNull("Dependencies should not be null", dependencies);
    assertEquals("There should be " + keys.length + " dependencies",
        keys.length, dependencies.size());

    for (Key<?> key : keys) {
      assertTrue("Dependencies should contain " + key, dependencies.contains(key));
    }
  }

  private static class A {}
  private static class B {}
  private static class C {}
  private static class D {}
  private static class E {}
  private static class F {}
  private static class G {}
  
  private static interface Interface {}
  
  private static class ConstructedClass implements Interface {
    @Inject A a;
    ConstructedClass() {}
    @Inject ConstructedClass(B b, C c) {}
    @Inject void setD(D d) {}
  }

  private static class ConstructedClassProvider implements Provider<ConstructedClass> {
    @Inject E e;
    ConstructedClassProvider() {}
    @Inject ConstructedClassProvider(A a, B b, C c) {}
    @Inject void setF(F f) {}
    
    public ConstructedClass get() {
      return null;
    }
  }

  private static class HasDependenciesClass implements Interface, HasDependencies {
    @Inject A a;
    @Inject B b;
    
    public Set<Dependency<?>> getDependencies() {
      return ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(G.class)));
    }
  }

  private static class ConvertedConstantModule extends AbstractModule {
    @Override
    protected void configure() {
      bindConstant().annotatedWith(Names.named("number")).to("2008");
    }
  }

  private static class InstanceModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ConstructedClass.class).toInstance(new ConstructedClass());
    }
  }

  private static class LinkedKeyModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Interface.class).to(ConstructedClass.class);
    }
  }
  
  private static class ProviderInstanceModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ConstructedClass.class).toProvider(new ConstructedClassProvider());
    }
  }

  private static class HasDependenciesModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Interface.class).toInstance(new HasDependenciesClass());
    }
  }
  
  private static class ProviderKeyModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(ConstructedClass.class).toProvider(ConstructedClassProvider.class);
    }
  }
}
