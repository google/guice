/*
Copyright (C) 2008 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject;

import com.google.inject.name.Names;
import junit.framework.TestCase;

/**
 * Tests for the sub module features
 * 
 * @author dan.halem@gmail.com (Dan Halem)
 */
public class SubModuleTest extends TestCase {

  private Key<Truck> smallTruck = Key.get(Truck.class, Names.named("SmallTruck"));
  private Key<Car> bigCar = Key.get(Car.class, Names.named("BigCar"));
  Module baseModule = new AbstractModule() {
    protected void configure() {
      binder().installAsSubModule(childModuleOne)
          .export(Car.class)
          .exportKeyAs(Key.get(Truck.class), smallTruck);
      installAsSubModule(childModuleTwo)
          .export(Truck.class)
          .exportKeyAs(Key.get(Car.class), bigCar);
    }
  };

  Module childModuleOne = new AbstractModule() {
    protected void configure() {
      bind(Engine.class).toInstance(new Engine() {
        public int getCylinders() {
          return 4;
        }
      });
    }
  };


  Module childModuleTwo = new AbstractModule() {
    protected void configure() {
      bind(Engine.class).toInstance(new Engine() {
        public int getCylinders() {
          return 8;
        }
      });
    }
  };

  Injector injector = Guice.createInjector(baseModule);

  public void testBasicSubModules() throws Exception {
    FuelTank tank = injector.getInstance(FuelTank.class);
    Car car = injector.getInstance(Car.class);
    Truck truck = injector.getInstance(Truck.class);

    assertSame(tank, car.getFuelTank());
    assertSame(tank, truck.getFuelTank());

    assertEquals(4, car.getEngine().getCylinders());
    assertEquals(8, truck.getEngine().getCylinders());
  }

  public void testBaseObjectDependingOnChild() throws Exception {
    Fleet fleet = injector.getInstance(Fleet.class);
  }

  public void testNamed() throws Exception {
    Truck smallTruck = injector.getInstance(this.smallTruck);
    assertEquals(4, smallTruck.getEngine().getCylinders());

    Car bigCar = injector.getInstance(this.bigCar);
    assertEquals(8, bigCar.getEngine().getCylinders());
  }

  public void testWithoutSubModules() throws Exception {
    Injector injector = Guice.createInjector();
    try {
      injector.getInstance(Fleet.class);
      fail("Fleet should not be available");
    } catch (ConfigurationException e) {

    }
  }

  @Singleton
  private static class FuelTank {
  }

  private static class Vehicle {
    FuelTank fuelTank;
    Engine engine;

    public Vehicle(FuelTank fuelTank, Engine engine) {
      this.fuelTank = fuelTank;
      this.engine = engine;
    }

    public FuelTank getFuelTank() {
      return fuelTank;
    }

    public Engine getEngine() {
      return engine;
    }
  }

  private static class Car extends Vehicle {
    @Inject
    public Car(FuelTank fuelTank, Engine engine) {
      super(fuelTank, engine);
    }
  }

  private static class Truck extends Vehicle {
    @Inject
    public Truck(FuelTank fuelTank, Engine engine) {
      super(fuelTank, engine);
    }
  }

  private static class Fleet {
    private Truck truck;
    private Car car;

    @Inject
    public Fleet(Truck truck, Car car) {
      this.truck = truck;
      this.car = car;
    }

    public Truck getTruck() {
      return truck;
    }

    public Car getCar() {
      return car;
    }
  }

  private interface Engine {
    int getCylinders();
  }
}
