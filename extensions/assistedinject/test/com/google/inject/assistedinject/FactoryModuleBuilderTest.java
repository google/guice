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

import com.google.inject.AbstractModule;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import junit.framework.TestCase;

import java.awt.*;

public class FactoryModuleBuilderTest extends TestCase {

  public void testImplicitForwardingAssistedBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Car.class).to(Golf.class);
        install(new FactoryModuleBuilder().build(ColoredCarFactory.class));
      }
    });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    assertTrue(factory.create(Color.BLUE) instanceof Golf);
  }

  public void testExplicitForwardingAssistedBinding() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override protected void configure() {
        bind(Volkswagen.class).to(Golf.class);
        install(new FactoryModuleBuilder()
            .implement(Car.class, Volkswagen.class)
            .build(ColoredCarFactory.class));
      }
    });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    assertTrue(factory.create(Color.BLUE) instanceof Golf);
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
    Foo<String> foo = factory.create(new Bar());
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
  
}
