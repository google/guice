// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.inject.grapher;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Sets;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ConvertedConstantBinding;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import com.google.inject.spi.UntargettedBinding;
import java.util.Collection;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Tests for {@link InjectorGrapher}.
 *
 * @author bojand@google.com (Bojan Djordjevic)
 */
public class InjectorGrapherTest extends TestCase {
  private static class FakeGraphingVisitor implements BindingTargetVisitor<Object, Void> {
    private final Set<Key> keys = Sets.newHashSet();

    public Void visit(InstanceBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ProviderInstanceBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ProviderKeyBinding<?> binding) {
      return record(binding);
    }
    public Void visit(LinkedKeyBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ExposedBinding<?> binding) {
      return record(binding);
    }
    public Void visit(UntargettedBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ConstructorBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ConvertedConstantBinding<?> binding) {
      return record(binding);
    }
    public Void visit(ProviderBinding<?> binding) {
      return record(binding);
    }

    public Set<Key> getKeys() {
      return keys;
    }

    private Void record(Binding<?> binding) {
      keys.add(binding.getKey());
      return null;
    }
  }

  private static class A {}
  private static class B {}
  private static class C {}
  private static class D {}
  private static class E {}

  private static class ClassModule extends AbstractModule {
    @Override protected void configure() {
      bind(D.class).toInstance(new D());
      bind(E.class).toInstance(new E());
    }

    @Provides A provideA(B b, @Named("test") C c) {
      return new A();
    }

    @Provides B provideB(D d, E e) {
      return new B();
    }

    @Provides @Named("test") C provideC(D d, E e) {
      return new C();
    }
  }

  private final BindingTargetVisitor<Object, Collection<Key<?>>> keyVisitor =
      new TransitiveDependencyVisitor();
  private final Renderer renderer = new Renderer() {
    public void render() {}
  };

  private FakeGraphingVisitor graphingVisitor;
  private Injector injector;
  private InjectorGrapher grapher;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    graphingVisitor = new FakeGraphingVisitor();
    injector = Guice.createInjector(new ClassModule());
    grapher = new InjectorGrapher(keyVisitor, graphingVisitor, renderer);
  }

  /** Tests making a graph rooted at a {@link Class}. */
  public void testRootedAtClass() throws Exception {
    grapher.of(injector)
        .rootedAt(B.class)
        .graph();
    assertEquals(ImmutableSet.<Key<?>>of(
        Key.get(B.class),
        Key.get(D.class),
        Key.get(E.class)), graphingVisitor.getKeys());
  }

  /** Tests making a graph rooted at a {@link Key}. */
  public void testRootedAtKey() throws Exception {
    Key cKey = Key.get(C.class, Names.named("test"));
    grapher.of(injector)
        .rootedAt(cKey)
        .graph();
    assertEquals(ImmutableSet.of(
        cKey,
        Key.get(D.class),
        Key.get(E.class)), graphingVisitor.getKeys());
  }
}