/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.assistedinject;

import static com.google.inject.name.Names.named;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.name.Named;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

/**
 * Tests for AssistedInject Spi.
 *
 * @author ramakrishna@google.com (Ramakrishna Rajanna)
 */
public class ExtensionSpiTest extends TestCase {

  public final void testSpiOnElements() throws Exception {
    AssistedInjectSpiVisitor visitor = new AssistedInjectSpiVisitor();
    Integer count = 0;
    for (Element element : Elements.getElements(new Module())) {
      if (element instanceof Binding) {
        assertEquals(count++, ((Binding<?>) element).acceptTargetVisitor(visitor));
      }
    }
    validateVisitor(visitor);
  }

  public void testSpiOnVisitor() throws Exception {
    AssistedInjectSpiVisitor visitor = new AssistedInjectSpiVisitor();
    Integer count = 0;
    Injector injector = Guice.createInjector(new Module());
    for (Binding<?> binding : injector.getBindings().values()) {
      assertEquals(count++, binding.acceptTargetVisitor(visitor));
    }
    validateVisitor(visitor);
  }

  private void validateVisitor(AssistedInjectSpiVisitor visitor) throws Exception {
    assertEquals(1, visitor.assistedBindingCount);
    List<AssistedMethod> assistedMethods =
        Lists.newArrayList(
            Iterables.getOnlyElement(visitor.assistedInjectBindings).getAssistedMethods());
    assertEquals(7, assistedMethods.size());
    assertEquals(1, visitor.assistedBindingCount);
    assertEquals(1, visitor.assistedInjectBindings.size());

    // Validate for each of the methods in AnimalFactory

    Set<String> names = Sets.newHashSet();
    for (AssistedMethod method : assistedMethods) {
      String name = method.getFactoryMethod().getName();
      names.add(name);
      if (name.equals("createAStrangeCatAsAnimal")) {
        validateAssistedMethod(method, name, StrangeCat.class, ImmutableList.<Key<?>>of());
      } else if (name.equals("createStrangeCatWithConstructorForOwner")) {
        validateAssistedMethod(method, name, StrangeCat.class, ImmutableList.<Key<?>>of());
      } else if (name.equals("createStrangeCatWithConstructorForAge")) {
        validateAssistedMethod(method, name, StrangeCat.class, ImmutableList.<Key<?>>of());
      } else if (name.equals("createCatWithANonAssistedDependency")) {
        validateAssistedMethod(
            method,
            name,
            CatWithAName.class,
            ImmutableList.<Key<?>>of(Key.get(String.class, named("catName2"))));
      } else if (name.equals("createCat")) {
        validateAssistedMethod(method, name, Cat.class, ImmutableList.<Key<?>>of());
      } else if (name.equals("createASimpleCatAsAnimal")) {
        validateAssistedMethod(method, name, SimpleCat.class, ImmutableList.<Key<?>>of());
      } else if (name.equals("createCatWithNonAssistedDependencies")) {
        List<Key<?>> dependencyKeys =
            ImmutableList.<Key<?>>of(
                Key.get(String.class, named("catName1")),
                Key.get(String.class, named("petName")),
                Key.get(Integer.class, named("age")));
        validateAssistedMethod(method, name, ExplodingCat.class, dependencyKeys);
      } else {
        fail("Invalid method: " + method);
      }
    }
    assertEquals(
        names,
        ImmutableSet.of(
            "createAStrangeCatAsAnimal",
            "createStrangeCatWithConstructorForOwner",
            "createStrangeCatWithConstructorForAge",
            "createCatWithANonAssistedDependency",
            "createCat",
            "createASimpleCatAsAnimal",
            "createCatWithNonAssistedDependencies"));
  }

  private void validateAssistedMethod(
      AssistedMethod assistedMethod,
      String factoryMethodName,
      Class<?> clazz,
      List<Key<?>> dependencyKeys) {
    assertEquals(factoryMethodName, assistedMethod.getFactoryMethod().getName());
    assertEquals(clazz, assistedMethod.getImplementationConstructor().getDeclaringClass());
    assertEquals(dependencyKeys.size(), assistedMethod.getDependencies().size());
    for (Dependency<?> dependency : assistedMethod.getDependencies()) {
      assertTrue(dependencyKeys.contains(dependency.getKey()));
    }
    assertEquals(clazz, assistedMethod.getImplementationType().getType());
  }

  interface AnimalFactory {
    Cat createCat(String owner);

    CatWithAName createCatWithANonAssistedDependency(String owner);

    @Named("SimpleCat")
    Animal createASimpleCatAsAnimal(String owner);

    Animal createAStrangeCatAsAnimal(String owner);

    StrangeCat createStrangeCatWithConstructorForOwner(String owner);

    StrangeCat createStrangeCatWithConstructorForAge(Integer age);

    ExplodingCat createCatWithNonAssistedDependencies(String owner);
  }

  interface Animal {}

  private static class Cat implements Animal {
    @Inject
    Cat(@Assisted String owner) {}
  }

  private static class SimpleCat implements Animal {
    @Inject
    SimpleCat(@Assisted String owner) {}
  }

  private static class StrangeCat implements Animal {
    @AssistedInject
    StrangeCat(@Assisted String owner) {}

    @AssistedInject
    StrangeCat(@Assisted Integer age) {}
  }

  private static class ExplodingCat implements Animal {
    @Inject
    public ExplodingCat(
        @Named("catName1") String name,
        @Assisted String owner,
        @Named("age") Integer age,
        @Named("petName") String petName) {}
  }

  private static class CatWithAName extends Cat {
    @Inject
    CatWithAName(@Assisted String owner, @Named("catName2") String name) {
      super(owner);
    }
  }

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(String.class).annotatedWith(named("catName1")).toInstance("kitty1");
      bind(String.class).annotatedWith(named("catName2")).toInstance("kitty2");
      bind(String.class).annotatedWith(named("petName")).toInstance("pussy");
      bind(Integer.class).annotatedWith(named("age")).toInstance(12);
      install(
          new FactoryModuleBuilder()
              .implement(Animal.class, StrangeCat.class)
              .implement(Animal.class, named("SimpleCat"), SimpleCat.class)
              .build(AnimalFactory.class));
    }
  }

  public static class AssistedInjectSpiVisitor extends DefaultBindingTargetVisitor<Object, Integer>
      implements AssistedInjectTargetVisitor<Object, Integer> {

    private final Set<Class<?>> allowedClasses =
        ImmutableSet.<Class<?>>of(
            Injector.class, Stage.class, Logger.class, String.class, Integer.class);

    private int assistedBindingCount = 0;
    private int currentCount = 0;
    private List<AssistedInjectBinding<?>> assistedInjectBindings = Lists.newArrayList();

    @Override
    public Integer visit(AssistedInjectBinding<?> assistedInjectBinding) {
      assistedInjectBindings.add(assistedInjectBinding);
      assistedBindingCount++;
      return currentCount++;
    }

    @Override
    protected Integer visitOther(Binding<?> binding) {
      if (!allowedClasses.contains(binding.getKey().getTypeLiteral().getRawType())) {
        throw new AssertionFailedError("invalid other binding: " + binding);
      }
      return currentCount++;
    }
  }
}
