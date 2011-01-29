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

package com.google.inject.assistedinject;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;

import java.awt.Color;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Iterables;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.Message;

public class FactoryModuleBuilderTest extends TestCase {

  public void testImplicitForwardingAssistedBindingFailsWithInterface() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Car.class).to(Golf.class);
          install(new FactoryModuleBuilder().build(ColoredCarFactory.class));
        }
      });
      fail();
    } catch (CreationException ce) {
      assertContains(
          ce.getMessage(), "1) " + Car.class.getName() + " is an interface, not a concrete class.",
          "Unable to create AssistedInject factory.",
          "while locating " + Car.class.getName(),
          "at " + ColoredCarFactory.class.getName() + ".create(");
      assertEquals(1, ce.getErrorMessages().size());
    }
  }
  
  public void testImplicitForwardingAssistedBindingFailsWithAbstractClass() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(AbstractCar.class).to(ArtCar.class);
          install(new FactoryModuleBuilder().build(ColoredAbstractCarFactory.class));
        }
      });
      fail();
    } catch (CreationException ce) {
      assertContains(
          ce.getMessage(), "1) " + AbstractCar.class.getName() + " is abstract, not a concrete class.",
          "Unable to create AssistedInject factory.",
          "while locating " + AbstractCar.class.getName(),
          "at " + ColoredAbstractCarFactory.class.getName() + ".create(");
      assertEquals(1, ce.getErrorMessages().size());
    }
  }

  public void testImplicitForwardingAssistedBindingCreatesNewObjects() {
    final Mustang providedMustang = new Mustang(Color.BLUE);
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        install(new FactoryModuleBuilder().build(MustangFactory.class));
      }
      @Provides Mustang provide() { return providedMustang; }
    });
    assertSame(providedMustang, injector.getInstance(Mustang.class));
    MustangFactory factory = injector.getInstance(MustangFactory.class);
    Mustang created = factory.create(Color.GREEN);
    assertNotSame(providedMustang, created);
    assertEquals(Color.BLUE, providedMustang.color);
    assertEquals(Color.GREEN, created.color);
  }

  public void testExplicitForwardingAssistedBindingFailsWithInterface() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(Volkswagen.class).to(Golf.class);
          install(new FactoryModuleBuilder()
            .implement(Car.class, Volkswagen.class)
            .build(ColoredCarFactory.class));
        }
      });
      fail();
    } catch (CreationException ce) {
      assertContains(
          ce.getMessage(), "1) " + Volkswagen.class.getName() + " is an interface, not a concrete class.",
          "Unable to create AssistedInject factory.",
          "while locating " + Volkswagen.class.getName(),
          "while locating " + Car.class.getName(),
          "at " + ColoredCarFactory.class.getName() + ".create(");
      assertEquals(1, ce.getErrorMessages().size());
    }
  }

  public void testExplicitForwardingAssistedBindingFailsWithAbstractClass() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(AbstractCar.class).to(ArtCar.class);
          install(new FactoryModuleBuilder()
            .implement(Car.class, AbstractCar.class)
            .build(ColoredCarFactory.class));
        }
      });
      fail();
    } catch (CreationException ce) {
      assertContains(
          ce.getMessage(), "1) " + AbstractCar.class.getName() + " is abstract, not a concrete class.",
          "Unable to create AssistedInject factory.",
          "while locating " + AbstractCar.class.getName(),
          "while locating " + Car.class.getName(),
          "at " + ColoredCarFactory.class.getName() + ".create(");
      assertEquals(1, ce.getErrorMessages().size());
    }
  }
  
  public void testExplicitForwardingAssistedBindingCreatesNewObjects() {
    final Mustang providedMustang = new Mustang(Color.BLUE);
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        install(new FactoryModuleBuilder().implement(Car.class, Mustang.class).build(
            ColoredCarFactory.class));
      }
      @Provides Mustang provide() { return providedMustang; }
    });
    assertSame(providedMustang, injector.getInstance(Mustang.class));
    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    Mustang created = (Mustang)factory.create(Color.GREEN);
    assertNotSame(providedMustang, created);
    assertEquals(Color.BLUE, providedMustang.color);
    assertEquals(Color.GREEN, created.color);
  }

  public void testAnnotatedAndParentBoundReturnValue() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Car.class).to(Golf.class);

        bind(Integer.class).toInstance(911);
        bind(Double.class).toInstance(5.0d);
        install(new FactoryModuleBuilder()
            .implement(Car.class, Names.named("german"), Beetle.class)
            .implement(Car.class, Names.named("american"), Mustang.class)
            .build(AnnotatedVersatileCarFactory.class));
      }
    });

    AnnotatedVersatileCarFactory factory = injector.getInstance(AnnotatedVersatileCarFactory.class);
    assertTrue(factory.getGermanCar(Color.BLACK) instanceof Beetle);
    assertTrue(injector.getInstance(Car.class) instanceof Golf);
  }

  public void testParentBoundReturnValue() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Car.class).to(Golf.class);
        bind(Double.class).toInstance(5.0d);
        install(new FactoryModuleBuilder()
            .implement(Car.class, Mustang.class)
            .build(ColoredCarFactory.class));
      }
    });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    assertTrue(factory.create(Color.RED) instanceof Mustang);
    assertTrue(injector.getInstance(Car.class) instanceof Golf);
  }

  public void testConfigureAnnotatedReturnValue() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        install(new FactoryModuleBuilder()
            .implement(Car.class, Names.named("german"), Beetle.class)
            .implement(Car.class, Names.named("american"), Mustang.class)
            .build(AnnotatedVersatileCarFactory.class));
      }
    });

    AnnotatedVersatileCarFactory factory = injector.getInstance(AnnotatedVersatileCarFactory.class);
    assertTrue(factory.getGermanCar(Color.GRAY) instanceof Beetle);
    assertTrue(factory.getAmericanCar(Color.BLACK) instanceof Mustang);
  }

  public void testNoBindingAssistedInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(MustangFactory.class));
      }
    });

    MustangFactory factory = injector.getInstance(MustangFactory.class);

    Mustang mustang = factory.create(Color.BLUE);
    assertEquals(Color.BLUE, mustang.color);
  }

  public void testBindingAssistedInject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder()
            .implement(Car.class, Mustang.class)
            .build(ColoredCarFactory.class));
      }
    });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);

    Mustang mustang = (Mustang) factory.create(Color.BLUE);
    assertEquals(Color.BLUE, mustang.color);
  }

  public void testMultipleReturnTypes() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(5.0d);
        install(new FactoryModuleBuilder().build(VersatileCarFactory.class));
      }
    });

    VersatileCarFactory factory = injector.getInstance(VersatileCarFactory.class);

    Mustang mustang = factory.getMustang(Color.RED);
    assertEquals(Color.RED, mustang.color);

    Beetle beetle = factory.getBeetle(Color.GREEN);
    assertEquals(Color.GREEN, beetle.color);
  }
  
  public void testParameterizedClassesWithNoImplements() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(new TypeLiteral<Foo.Factory<String>>() {}));
      }
    });
    
    Foo.Factory<String> factory = injector.getInstance(Key.get(new TypeLiteral<Foo.Factory<String>>() {}));
    @SuppressWarnings("unused")
    Foo<String> foo = factory.create(new Bar());
  }
  
  public void testGenericErrorMessageMakesSense() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
         install(new FactoryModuleBuilder().build(Key.get(Foo.Factory.class))); 
        }
      });
      fail();
    } catch(CreationException ce ) {
      // Assert not only that it's the correct message, but also that it's the *only* message.
      Collection<Message> messages = ce.getErrorMessages();
      assertEquals(
          Foo.Factory.class.getName() + " cannot be used as a key; It is not fully specified.", 
          Iterables.getOnlyElement(messages).getMessage());
    }
  }

  interface Car {}

  interface Volkswagen extends Car {}

  interface ColoredCarFactory {
    Car create(Color color);
  }

  interface MustangFactory {
    Mustang create(Color color);
  }

  interface VersatileCarFactory {
    Mustang getMustang(Color color);
    Beetle getBeetle(Color color);
  }

  interface AnnotatedVersatileCarFactory {
    @Named("german") Car getGermanCar(Color color);
    @Named("american") Car getAmericanCar(Color color);
  }

  public static class Golf implements Volkswagen {}

  public static class Mustang implements Car {
    private final Color color;
    @Inject
    public Mustang(@Assisted Color color) {
      this.color = color;
    }
  }

  public static class Beetle implements Car {
    private final Color color;
    @Inject
    public Beetle(@Assisted Color color) {
      this.color = color;
    }
  }
  
  public static class Foo<E> {
    static interface Factory<E> {
      Foo<E> create(Bar bar);
    }
    @Inject Foo(@Assisted Bar bar, Baz<E> baz) {}
  }
  
  public static class Bar {}
  public static class Baz<E> {}
  
  abstract static class AbstractCar implements Car {}  
  interface ColoredAbstractCarFactory {
    AbstractCar create(Color color);
  }  
  public static class ArtCar extends AbstractCar {}
    
  public void testFactoryBindingDependencies() {
    // validate dependencies work in all stages & as a raw element,
    // and that dependencies work for methods, fields, constructors,
    // and for @AssistedInject constructors too.
    Module module = new AbstractModule() {
      @Override
      protected void configure() {
        bind(Integer.class).toInstance(42);
        bind(Double.class).toInstance(4.2d);
        bind(Float.class).toInstance(4.2f);
        bind(String.class).annotatedWith(named("dog")).toInstance("dog");
        bind(String.class).annotatedWith(named("cat1")).toInstance("cat1");
        bind(String.class).annotatedWith(named("cat2")).toInstance("cat2");
        bind(String.class).annotatedWith(named("cat3")).toInstance("cat3");
        bind(String.class).annotatedWith(named("arbitrary")).toInstance("fail!");
        install(new FactoryModuleBuilder()
                .implement(Animal.class, Dog.class)
                .build(AnimalHouse.class));
      }
    };

    Set<Key<?>> expectedKeys = ImmutableSet.<Key<?>>of(
        Key.get(Integer.class),
        Key.get(Double.class),
        Key.get(Float.class),
        Key.get(String.class, named("dog")),
        Key.get(String.class, named("cat1")),
        Key.get(String.class, named("cat2")),
        Key.get(String.class, named("cat3"))
    );
    
    Injector injector = Guice.createInjector(module);
    validateDependencies(expectedKeys, injector.getBinding(AnimalHouse.class));
    
    injector = Guice.createInjector(Stage.TOOL, module);
    validateDependencies(expectedKeys, injector.getBinding(AnimalHouse.class));
    
    List<Element> elements = Elements.getElements(module);
    boolean found = false;
    for(Element element : elements) {
      if(element instanceof Binding) {
        Binding binding = (Binding)element;
        if(binding.getKey().equals(Key.get(AnimalHouse.class))) {
          found = true;
          validateDependencies(expectedKeys, binding);
          break;
        }
      }
    }
    assertTrue(found);
  }
  
  private void validateDependencies(Set<Key<?>> expectedKeys, Binding<?> binding) {
    Set<Dependency<?>> dependencies = ((HasDependencies)binding).getDependencies();
    Set<Key<?>> actualKeys = new HashSet<Key<?>>();
    for(Dependency dependency : dependencies) {
      actualKeys.add(dependency.getKey());
    }
    assertEquals(expectedKeys, actualKeys);
  }
  
  interface AnimalHouse {
    Animal createAnimal(String name);
    Cat createCat(String name);
    Cat createCat(int age);
  }
  
  interface Animal {}
  private static class Dog implements Animal {
    @Inject int a;
    @Inject Dog(@Assisted String a, double b) {}
    @Inject void register(@Named("dog") String a) {}
  }
  private static class Cat implements Animal {
    @Inject float a;
    @AssistedInject Cat(@Assisted String a, @Named("cat1") String b) {}
    @AssistedInject Cat(@Assisted int a, @Named("cat2") String b) {}
    @AssistedInject Cat(@Assisted byte a, @Named("catfail") String b) {} // not a dependency!
    @Inject void register(@Named("cat3") String a) {}
  }
  
  public void testFactoryPublicAndReturnTypeNotPublic() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override
        protected void configure() {
          install(new FactoryModuleBuilder()
              .implement(Hidden.class, HiddenImpl.class)
              .build(NotHidden.class));
        }
      });
    } catch(CreationException ce) {
      assertEquals(NotHidden.class.getName() + " is public, but has a method that returns a non-public type: "
          + Hidden.class.getName() + ". Due to limitations with java.lang.reflect.Proxy, this is not allowed. "
          + "Please either make the factory non-public or the return type public.",           
          Iterables.getOnlyElement(ce.getErrorMessages()).getMessage());
    }
  }
  
  interface Hidden {}
  public static class HiddenImpl implements Hidden {}
  public interface NotHidden {
    Hidden create();
  }
  
  public void testSingletonScopeOnAssistedClassIsIgnored() {
    // production stage is important, because it will trigger eager singleton creation
    Injector injector = Guice.createInjector(Stage.PRODUCTION, new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder().build(SingletonFactory.class));
      }
    });
    
    SingletonFactory factory = injector.getInstance(SingletonFactory.class);
    assertNotSame(factory.create("foo"), factory.create("bar"));
  }
  
  interface SingletonFactory {
    AssistedSingleton create(String string);
  }
  
  @Singleton
  static class AssistedSingleton {
    @Inject
    public AssistedSingleton(@Assisted String string) {      
    }
  }
  
}
