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

package com.googlecode.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Jsr330;
import com.googlecode.atinject.Candidate;
import com.googlecode.atinject.Tck;
import com.googlecode.atinject.auto.Car;
import com.googlecode.atinject.auto.Convertible;
import com.googlecode.atinject.auto.Drivers;
import com.googlecode.atinject.auto.DriversSeat;
import com.googlecode.atinject.auto.Engine;
import com.googlecode.atinject.auto.FuelTank;
import com.googlecode.atinject.auto.Seat;
import com.googlecode.atinject.auto.Tire;
import com.googlecode.atinject.auto.V8Engine;
import com.googlecode.atinject.auto.accessories.Cupholder;
import com.googlecode.atinject.auto.accessories.SpareTire;

public class GuiceCandidate implements Candidate {

  private final Injector injector;

  public GuiceCandidate() {
    this.injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(Car.class).to(Convertible.class);
        bind(Seat.class).annotatedWith(Drivers.class).to(DriversSeat.class);
        bind(Engine.class).to(V8Engine.class);
        bind(Tire.class).annotatedWith(Jsr330.named("spare")).to(SpareTire.class);
        bind(Cupholder.class);
        bind(Tire.class);
        bind(FuelTank.class);
      }
    });
  }

  public Car getCar() {
    return injector.getInstance(Car.class);
  }

  public static void main(String[] args) throws IllegalAccessException,
      InstantiationException, ClassNotFoundException {
    Tck.main(new String[] { GuiceCandidate.class.getName() });
  }
}
