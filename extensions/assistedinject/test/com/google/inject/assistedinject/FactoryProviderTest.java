/**
 * Copyright (C) 2007 Google Inc.
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
import static com.google.inject.Asserts.assertContains;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import java.awt.Color;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import junit.framework.TestCase;

/**
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class FactoryProviderTest extends TestCase {

  public void testAssistedFactory() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(5.0d);
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Mustang blueMustang = (Mustang) carFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueMustang.color);
    assertEquals(5.0d, blueMustang.engineSize);

    Mustang redMustang = (Mustang) carFactory.create(Color.RED);
    assertEquals(Color.RED, redMustang.color);
    assertEquals(5.0d, redMustang.engineSize);
  }

  public void testFactoryBindingDependencies() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(5.0d);
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
      }
    });

    Binding<?> binding = injector.getBinding(ColoredCarFactory.class);
    HasDependencies hasDependencies = (HasDependencies) binding;
    assertEquals(ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(double.class))),
        hasDependencies.getDependencies());
  }

  public void testAssistedFactoryWithAnnotations() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
        bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Camaro.class));
      }
    });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Camaro blueCamaro = (Camaro) carFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueCamaro.color);
    assertEquals(1984, blueCamaro.modelYear);
    assertEquals(250, blueCamaro.horsePower);

    Camaro redCamaro = (Camaro) carFactory.create(Color.RED);
    assertEquals(Color.RED, redCamaro.color);
    assertEquals(1984, redCamaro.modelYear);
    assertEquals(250, redCamaro.horsePower);
  }

  interface Car {
  }

  interface ColoredCarFactory {
    Car create(Color color);
  }

  public static class Mustang implements Car {
    private final double engineSize;
    private final Color color;

    @AssistedInject
    public Mustang(double engineSize, @Assisted Color color) {
      this.engineSize = engineSize;
      this.color = color;
    }
  }

  public static class Camaro implements Car {
    private final int horsePower;
    private final int modelYear;
    private final Color color;

    @AssistedInject
    public Camaro(
        @Named("horsePower")int horsePower,
        @Named("modelYear")int modelYear,
        @Assisted Color color) {
      this.horsePower = horsePower;
      this.modelYear = modelYear;
      this.color = color;
    }
  }

  interface SummerCarFactory {
    Car create(Color color, boolean convertable);
    Car createConvertible(Color color);
  }

  public void testFactoryWithMultipleMethods() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(float.class).toInstance(140f);
        bind(SummerCarFactory.class).toProvider(
            FactoryProvider.newFactory(SummerCarFactory.class, Corvette.class));
      }
    });

    SummerCarFactory carFactory = injector.getInstance(SummerCarFactory.class);

    Corvette blueCorvette = (Corvette) carFactory.createConvertible(Color.BLUE);
    assertEquals(Color.BLUE, blueCorvette.color);
    assertEquals(100f, blueCorvette.maxMph);
    assertTrue(blueCorvette.isConvertable);

    Corvette redCorvette = (Corvette) carFactory.create(Color.RED, false);
    assertEquals(Color.RED, redCorvette.color);
    assertEquals(140f, redCorvette.maxMph);
    assertFalse(redCorvette.isConvertable);
  }

  public static class Corvette implements Car {
    private boolean isConvertable;
    private Color color;
    private float maxMph;

    @AssistedInject
    public Corvette(@Assisted Color color) {
      this(color, 100f, true);
    }

    public Corvette(@Assisted Color color, @Assisted boolean isConvertable) {
      throw new IllegalStateException("Not an @AssistedInject constructor");
    }

    @AssistedInject
    public Corvette(@Assisted Color color, Float maxMph, @Assisted boolean isConvertable) {
      this.isConvertable = isConvertable;
      this.color = color;
      this.maxMph = maxMph;
    }
  }

  public void testFactoryMethodsMismatch() {
    try {
      FactoryProvider.newFactory(SummerCarFactory.class, Beetle.class);
      fail();
    } catch(ConfigurationException e) {
      assertContains(e.getMessage(), "Constructor mismatch");
    }
  }

  public static class Beetle implements Car {
    @AssistedInject
    public Beetle(@Assisted Color color) {
      throw new IllegalStateException("Conflicting constructors");
    }
    @AssistedInject
    public Beetle(@Assisted Color color, @Assisted boolean isConvertable) {
      throw new IllegalStateException("Conflicting constructors");
    }
    @AssistedInject
    public Beetle(@Assisted Color color, @Assisted boolean isConvertable, float maxMph) {
      throw new IllegalStateException("Conflicting constructors");
    }
  }

  public void testMethodsAndFieldsGetInjected() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).toInstance("turbo");
        bind(int.class).toInstance(911);
        bind(double.class).toInstance(50000d);
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Porshe.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Porshe grayPorshe = (Porshe) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, grayPorshe.color);
    assertEquals(50000d, grayPorshe.price);
    assertEquals(911, grayPorshe.model);
    assertEquals("turbo", grayPorshe.name);
  }

  public static class Porshe implements Car {
    private final Color color;
    private final double price;
    private @Inject String name;
    private int model;

    @AssistedInject
    public Porshe(@Assisted Color color, double price) {
      this.color = color;
      this.price = price;
    }

    @Inject void setModel(int model) {
      this.model = model;
    }
  }

  public void testProviderInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(String.class).toInstance("trans am");
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Firebird.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Firebird blackFirebird = (Firebird) carFactory.create(Color.BLACK);
    assertEquals(Color.BLACK, blackFirebird.color);
    assertEquals("trans am", blackFirebird.modifiersProvider.get());
  }

  public static class Firebird implements Car {
    private final Provider<String> modifiersProvider;
    private final Color color;

    @AssistedInject
    public Firebird(Provider<String> modifiersProvider, @Assisted Color color) {
      this.modifiersProvider = modifiersProvider;
      this.color = color;
    }
  }

  public void testTypeTokenInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<Set<String>>() {}).toInstance(Collections.singleton("Flux Capacitor"));
        bind(new TypeLiteral<Set<Integer>>() {}).toInstance(Collections.singleton(88));
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, DeLorean.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    DeLorean deLorean = (DeLorean) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, deLorean.color);
    assertEquals("Flux Capacitor", deLorean.features.iterator().next());
    assertEquals(new Integer(88), deLorean.featureActivationSpeeds.iterator().next());
  }

  public static class DeLorean implements Car {
    private final Set<String> features;
    private final Set<Integer> featureActivationSpeeds;
    private final Color color;

    @AssistedInject
    public DeLorean(
        Set<String> extraFeatures, Set<Integer> featureActivationSpeeds, @Assisted Color color) {
      this.features = extraFeatures;
      this.featureActivationSpeeds = featureActivationSpeeds;
      this.color = color;
    }
  }

  public void testTypeTokenProviderInjection() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(new TypeLiteral<Set<String>>() { }).toInstance(Collections.singleton("Datsun"));
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Z.class));
      }
    });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Z orangeZ = (Z) carFactory.create(Color.ORANGE);
    assertEquals(Color.ORANGE, orangeZ.color);
    assertEquals("Datsun", orangeZ.manufacturersProvider.get().iterator().next());
  }

  public static class Z implements Car {
    private final Provider<Set<String>> manufacturersProvider;
    private final Color color;

    @AssistedInject
    public Z(Provider<Set<String>> manufacturersProvider, @Assisted Color color) {
      this.manufacturersProvider = manufacturersProvider;
      this.color = color;
    }
  }
  
  public static class Prius implements Car {
    @SuppressWarnings("unused")
    private final Color color;
    
    @AssistedInject
    private Prius(@Assisted Color color) {
      this.color = color;
    }
  }
  
  public void testAssistInjectionInNonPublicConstructor() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class)
            .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Prius.class));
      }
    });
    Car car = injector.getInstance(ColoredCarFactory.class).create(Color.ORANGE);
  }

  public static class ExplodingCar implements Car {
    @AssistedInject
    public ExplodingCar(@Assisted Color color) {
      throw new IllegalStateException("kaboom!");
    }
  }

  public void testExceptionDuringConstruction() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(ColoredCarFactory.class, ExplodingCar.class));
      }
    });
    try {
      injector.getInstance(ColoredCarFactory.class).create(Color.ORANGE);
      fail();
    } catch (IllegalStateException e) {
      assertEquals("kaboom!", e.getMessage());
    }
  }
  
  public static class DefectiveCar implements Car {
    @AssistedInject
    public DefectiveCar() throws ExplosionException, FireException {
      throw new ExplosionException();
    }
  }
  
  public static class ExplosionException extends Exception { }
  public static class FireException extends Exception { }
  
  public interface DefectiveCarFactoryWithNoExceptions {
    Car createCar();
  }

  public interface DefectiveCarFactory {
    Car createCar() throws FireException;
  }

  public void testFactoryMethodMustDeclareAllConstructorExceptions() {
    try {
      FactoryProvider.newFactory(DefectiveCarFactoryWithNoExceptions.class, DefectiveCar.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(), "no compatible exception is thrown");
    }
  }

  public interface CorrectDefectiveCarFactory {
    Car createCar() throws FireException, ExplosionException;
  }
  
  public void testConstructorExceptionsAreThrownByFactory() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(CorrectDefectiveCarFactory.class).toProvider(
            FactoryProvider.newFactory(
                CorrectDefectiveCarFactory.class, DefectiveCar.class));
      }
    });
    try {
      injector.getInstance(CorrectDefectiveCarFactory.class).createCar();
      fail();
    } catch (FireException e) {
      fail();
    } catch (ExplosionException expected) {
    }
  }

  public static class MultipleConstructorDefectiveCar implements Car {
    @AssistedInject
    public MultipleConstructorDefectiveCar() throws ExplosionException {
      throw new ExplosionException();
    }

    @AssistedInject
    public MultipleConstructorDefectiveCar(@Assisted Color c) throws FireException {
      throw new FireException();
    }
  }

  public interface MultipleConstructorDefectiveCarFactory {
    Car createCar() throws ExplosionException;
    Car createCar(Color r) throws FireException;
  }

  public void testMultipleConstructorExceptionMatching() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(MultipleConstructorDefectiveCarFactory.class).toProvider(
            FactoryProvider.newFactory(
                MultipleConstructorDefectiveCarFactory.class,
                MultipleConstructorDefectiveCar.class));
      }
    });
    MultipleConstructorDefectiveCarFactory factory
        = injector.getInstance(MultipleConstructorDefectiveCarFactory.class);
    try {
      factory.createCar();
      fail();
    } catch (ExplosionException expected) {
    }

    try {
      factory.createCar(Color.RED);
      fail();
    } catch (FireException expected) {
    }
  }
  
  public static class WildcardCollection {
    
    public interface Factory {
      WildcardCollection create(Collection<?> items);
    }

    @AssistedInject
    public WildcardCollection(@Assisted Collection<?> items) { }
  }
  
  public void testWildcardGenerics() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(WildcardCollection.Factory.class).toProvider(
            FactoryProvider.newFactory(
                WildcardCollection.Factory.class,
                WildcardCollection.class));
      }
    });
    WildcardCollection.Factory factory = injector.getInstance(WildcardCollection.Factory.class);
    factory.create(Collections.emptyList());
  }
  
  public static class SteeringWheel {}
  
  public static class Fiat implements Car {
    @SuppressWarnings("unused")
    private final SteeringWheel steeringWheel;
    @SuppressWarnings("unused")
    private final Color color;
    
    @AssistedInject
    public Fiat(SteeringWheel steeringWheel, @Assisted Color color) {
      this.steeringWheel = steeringWheel;
      this.color = color;
    }
  }
  
  public void testFactoryWithImplicitBindings() {
    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(ColoredCarFactory.class).toProvider(
            FactoryProvider.newFactory(
                ColoredCarFactory.class, 
                Fiat.class));
      }
    });

    ColoredCarFactory coloredCarFactory = injector.getInstance(ColoredCarFactory.class);
    Fiat fiat = (Fiat) coloredCarFactory.create(Color.GREEN);
    assertEquals(Color.GREEN, fiat.color);
    assertNotNull(fiat.steeringWheel);
  }
  
  public void testFactoryFailsWithMissingBinding() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(ColoredCarFactory.class)
              .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) Parameter of type 'double' is not injectable or annotated with @Assisted");
    }
  }
  
  public void testMethodsDeclaredInObject() {
    Injector injector = Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(Double.class).toInstance(5.0d);
          bind(ColoredCarFactory.class)
              .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
        }
      });
    
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    
    carFactory.equals(carFactory);
    carFactory.hashCode();
    carFactory.toString();
  }

  public void testAssistedInjectConstructorAndAssistedFactoryParameterMustNotMix() {
    try {
      Guice.createInjector(new AbstractModule() {
        @Override protected void configure() {
          bind(Double.class).toInstance(5.0d);
          bind(AssistedParamsFactory.class)
              .toProvider(FactoryProvider.newFactory(AssistedParamsFactory.class, Mustang.class));
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Factory method "
          + AssistedParamsFactory.class.getName() + ".create() has an @Assisted parameter, which "
          + "is incompatible with the deprecated @AssistedInject annotation.");
    }
  }

  interface AssistedParamsFactory {
    Car create(@Assisted Color color);
  }

  interface GenericColoredCarFactory<T extends Car> {
    T create(Color color);
  }

  public void testGenericAssistedFactory() {
    final TypeLiteral<GenericColoredCarFactory<Mustang>> mustangTypeLiteral
        = new TypeLiteral<GenericColoredCarFactory<Mustang>>() {};
    final TypeLiteral<GenericColoredCarFactory<Camaro>> camaroTypeLiteral
        = new TypeLiteral<GenericColoredCarFactory<Camaro>>() {};

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(5.0d);
        bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
        bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
        bind(mustangTypeLiteral).toProvider(
            FactoryProvider.newFactory(mustangTypeLiteral, TypeLiteral.get(Mustang.class)));
        bind(camaroTypeLiteral).toProvider(
            FactoryProvider.newFactory(camaroTypeLiteral, TypeLiteral.get(Camaro.class)));
      }
    });

    GenericColoredCarFactory<Mustang> mustangFactory
        = injector.getInstance(Key.get(mustangTypeLiteral));
    GenericColoredCarFactory<Camaro> camaroFactory
        = injector.getInstance(Key.get(camaroTypeLiteral));

    Mustang blueMustang = mustangFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueMustang.color);
    assertEquals(5.0d, blueMustang.engineSize);

    Camaro redCamaro = camaroFactory.create(Color.RED);
    assertEquals(Color.RED, redCamaro.color);
    assertEquals(1984, redCamaro.modelYear);
    assertEquals(250, redCamaro.horsePower);
  }

  public interface Insurance<T extends Car> {
  }

  public static class MustangInsurance implements Insurance<Mustang> {
    private final double premium;
    private final double limit;
    private Mustang car;

    @AssistedInject
    public MustangInsurance(@Named("lowLimit") double limit, @Assisted Mustang car,
        @Assisted double premium) {
      this.premium = premium;
      this.limit = limit;
      this.car = car;
    }

    public void sell() {}
  }

  public static class CamaroInsurance implements Insurance<Camaro> {
    private final double premium;
    private final double limit;
    private Camaro car;

    @AssistedInject
    public CamaroInsurance(@Named("highLimit") double limit, @Assisted Camaro car,
        @Assisted double premium) {
      this.premium = premium;
      this.limit = limit;
      this.car = car;
    }

    public void sell() {}
  }

  public interface MustangInsuranceFactory {
    public Insurance<Mustang> create(Mustang car, double premium);
  }

  public interface CamaroInsuranceFactory {
    public Insurance<Camaro> create(Camaro car, double premium);
  }

  public void testAssistedFactoryForConcreteType() {

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).annotatedWith(Names.named("lowLimit")).toInstance(50000.0d);
        bind(Double.class).annotatedWith(Names.named("highLimit")).toInstance(100000.0d);
        bind(MustangInsuranceFactory.class).toProvider(
            FactoryProvider.newFactory(MustangInsuranceFactory.class, MustangInsurance.class));
        bind(CamaroInsuranceFactory.class).toProvider(
            FactoryProvider.newFactory(CamaroInsuranceFactory.class, CamaroInsurance.class));
      }
    });

    MustangInsuranceFactory mustangInsuranceFactory =
        injector.getInstance(MustangInsuranceFactory.class);
    CamaroInsuranceFactory camaroInsuranceFactory =
        injector.getInstance(CamaroInsuranceFactory.class);

    Mustang mustang = new Mustang(5000d, Color.BLACK);
    MustangInsurance mustangPolicy =
        (MustangInsurance) mustangInsuranceFactory.create(mustang, 800.0d);
    assertEquals(800.0d, mustangPolicy.premium);
    assertEquals(50000.0d, mustangPolicy.limit);

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    CamaroInsurance camaroPolicy = (CamaroInsurance) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium);
    assertEquals(100000.0d, camaroPolicy.limit);
  }

  public interface InsuranceFactory<T extends Car> {
    public Insurance<T> create(T car, double premium);
  }

  public void testAssistedFactoryForParameterizedType() {
    final TypeLiteral<InsuranceFactory<Mustang>> mustangInsuranceFactoryType =
        new TypeLiteral<InsuranceFactory<Mustang>>() {};
    final TypeLiteral<InsuranceFactory<Camaro>> camaroInsuranceFactoryType =
        new TypeLiteral<InsuranceFactory<Camaro>>() {};

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).annotatedWith(Names.named("lowLimit")).toInstance(50000.0d);
        bind(Double.class).annotatedWith(Names.named("highLimit")).toInstance(100000.0d);
        bind(mustangInsuranceFactoryType).toProvider(FactoryProvider.newFactory(
            mustangInsuranceFactoryType, TypeLiteral.get(MustangInsurance.class)));
        bind(camaroInsuranceFactoryType).toProvider(FactoryProvider.newFactory(
            camaroInsuranceFactoryType, TypeLiteral.get(CamaroInsurance.class)));
      }
    });

    InsuranceFactory<Mustang> mustangInsuranceFactory =
        injector.getInstance(Key.get(mustangInsuranceFactoryType));
    InsuranceFactory<Camaro> camaroInsuranceFactory =
        injector.getInstance(Key.get(camaroInsuranceFactoryType));

    Mustang mustang = new Mustang(5000d, Color.BLACK);
    MustangInsurance mustangPolicy =
        (MustangInsurance) mustangInsuranceFactory.create(mustang, 800.0d);
    assertEquals(800.0d, mustangPolicy.premium);
    assertEquals(50000.0d, mustangPolicy.limit);

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    CamaroInsurance camaroPolicy = (CamaroInsurance) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium);
    assertEquals(100000.0d, camaroPolicy.limit);
  }

  public static class AutoInsurance<T extends Car> implements Insurance<T> {
    private final double premium;
    private final double limit;
    private final T car;

    @AssistedInject
    public AutoInsurance(double limit, @Assisted T car, @Assisted double premium) {
      this.limit = limit;
      this.car = car;
      this.premium = premium;
    }

    public void sell() {}
  }

  public void testAssistedFactoryForTypeVariableParameters() {
    final TypeLiteral<InsuranceFactory<Camaro>> camaroInsuranceFactoryType =
        new TypeLiteral<InsuranceFactory<Camaro>>() {};

    Injector injector = Guice.createInjector(new AbstractModule() {
      @Override
      protected void configure() {
        bind(Double.class).toInstance(50000.0d);
        bind(camaroInsuranceFactoryType).toProvider(FactoryProvider.newFactory(
            camaroInsuranceFactoryType, new TypeLiteral<AutoInsurance<Camaro>>() {}));
      }
    });

    InsuranceFactory<Camaro> camaroInsuranceFactory =
        injector.getInstance(Key.get(camaroInsuranceFactoryType));

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    AutoInsurance camaroPolicy = (AutoInsurance) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium);
    assertEquals(50000.0d, camaroPolicy.limit);
    assertEquals(camaro, camaroPolicy.car);
  }
}