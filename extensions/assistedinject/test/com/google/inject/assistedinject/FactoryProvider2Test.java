/*
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

package com.google.inject.assistedinject;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Asserts.assertEqualsBothWays;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryProvider2Test.Equals.ComparisonMethod;
import com.google.inject.assistedinject.FactoryProvider2Test.Equals.Impl;
import com.google.inject.internal.Annotations;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import junit.framework.TestCase;

@SuppressWarnings("deprecation")
public class FactoryProvider2Test extends TestCase {

  private enum Color {
    BLUE,
    GREEN,
    RED,
    GRAY,
    BLACK,
    ORANGE,
    PINK
  }

  public void testAssistedFactory() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
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
    assertEquals(5.0d, blueMustang.engineSize, 0.0);

    Mustang redMustang = (Mustang) carFactory.create(Color.RED);
    assertEquals(Color.RED, redMustang.color);
    assertEquals(5.0d, redMustang.engineSize, 0.0);
  }

  public void testAssistedFactoryWithAnnotations() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
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

  public interface Car {}

  interface ColoredCarFactory {
    Car create(Color color);
  }

  public static class Mustang implements Car {
    private final double engineSize;
    private final Color color;

    @Inject
    public Mustang(double engineSize, @Assisted Color color) {
      this.engineSize = engineSize;
      this.color = color;
    }

    public void drive() {}
  }

  public static class Camaro implements Car {
    private final int horsePower;
    private final int modelYear;
    private final Color color;

    @Inject
    public Camaro(
        @Named("horsePower") int horsePower,
        @Named("modelYear") int modelYear,
        @Assisted Color color) {
      this.horsePower = horsePower;
      this.modelYear = modelYear;
      this.color = color;
    }
  }

  interface SummerCarFactory {
    Car create(Color color, boolean convertable);
  }

  public void testFactoryUsesInjectedConstructor() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(float.class).toInstance(140f);
                bind(SummerCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(SummerCarFactory.class, Corvette.class));
              }
            });

    SummerCarFactory carFactory = injector.getInstance(SummerCarFactory.class);

    Corvette redCorvette = (Corvette) carFactory.create(Color.RED, false);
    assertEquals(Color.RED, redCorvette.color);
    assertEquals(140f, redCorvette.maxMph, 0.0f);
    assertFalse(redCorvette.isConvertable);
  }

  public static class Corvette implements Car {
    private boolean isConvertable;
    private Color color;
    private float maxMph;

    @SuppressWarnings("unused")
    public Corvette(Color color, boolean isConvertable) {
      throw new IllegalStateException("Not an @AssistedInject constructor");
    }

    @Inject
    public Corvette(@Assisted Color color, Float maxMph, @Assisted boolean isConvertable) {
      this.isConvertable = isConvertable;
      this.color = color;
      this.maxMph = maxMph;
    }
  }

  public void testConstructorDoesntNeedAllFactoryMethodArguments() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(SummerCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(SummerCarFactory.class, Beetle.class));
              }
            });
    SummerCarFactory factory = injector.getInstance(SummerCarFactory.class);

    Beetle beetle = (Beetle) factory.create(Color.RED, true);
    assertSame(Color.RED, beetle.color);
  }

  public static class Beetle implements Car {
    private final Color color;

    @Inject
    public Beetle(@Assisted Color color) {
      this.color = color;
    }
  }

  public void testMethodsAndFieldsGetInjected() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("turbo");
                bind(int.class).toInstance(911);
                bind(double.class).toInstance(50000d);
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Porsche.class));
              }
            });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Porsche grayPorsche = (Porsche) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, grayPorsche.color);
    assertEquals(50000d, grayPorsche.price, 0.0);
    assertEquals(911, grayPorsche.model);
    assertEquals("turbo", grayPorsche.name);
  }

  public static class Porsche implements Car {
    private final Color color;
    private final double price;
    private @Inject String name;
    private int model;

    @Inject
    public Porsche(@Assisted Color color, double price) {
      this.color = color;
      this.price = price;
    }

    @Inject
    void setModel(int model) {
      this.model = model;
    }
  }

  public void testProviderInjection() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("trans am");
                bind(ColoredCarFactory.class)
                    .toProvider(
                        FactoryProvider.newFactory(ColoredCarFactory.class, Firebird.class));
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

    @Inject
    public Firebird(Provider<String> modifiersProvider, @Assisted Color color) {
      this.modifiersProvider = modifiersProvider;
      this.color = color;
    }
  }

  public void testAssistedProviderInjection() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(String.class).toInstance("trans am");
                bind(ColoredCarFactory.class)
                    .toProvider(
                        FactoryProvider.newFactory(ColoredCarFactory.class, Flamingbird.class));
              }
            });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Flamingbird flamingbird = (Flamingbird) carFactory.create(Color.BLACK);
    assertEquals(Color.BLACK, flamingbird.colorProvider.get());
    assertEquals("trans am", flamingbird.modifiersProvider.get());

    Flamingbird flamingbird2 = (Flamingbird) carFactory.create(Color.RED);
    assertEquals(Color.RED, flamingbird2.colorProvider.get());
    assertEquals("trans am", flamingbird2.modifiersProvider.get());
    // Make sure the original flamingbird is black still.
    assertEquals(Color.BLACK, flamingbird.colorProvider.get());
  }

  public static class Flamingbird implements Car {
    private final Provider<String> modifiersProvider;
    private final Provider<Color> colorProvider;

    @Inject
    public Flamingbird(
        Provider<String> modifiersProvider, @Assisted Provider<Color> colorProvider) {
      this.modifiersProvider = modifiersProvider;
      this.colorProvider = colorProvider;
    }
  }

  public void testTypeTokenInjection() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(new TypeLiteral<Set<String>>() {})
                    .toInstance(Collections.singleton("Flux Capacitor"));
                bind(new TypeLiteral<Set<Integer>>() {}).toInstance(Collections.singleton(88));
                bind(ColoredCarFactory.class)
                    .toProvider(
                        FactoryProvider.newFactory(ColoredCarFactory.class, DeLorean.class));
              }
            });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    DeLorean deLorean = (DeLorean) carFactory.create(Color.GRAY);
    assertEquals(Color.GRAY, deLorean.color);
    assertEquals("Flux Capacitor", deLorean.features.iterator().next());
    assertEquals(Integer.valueOf(88), deLorean.featureActivationSpeeds.iterator().next());
  }

  public static class DeLorean implements Car {
    private final Set<String> features;
    private final Set<Integer> featureActivationSpeeds;
    private final Color color;

    @Inject
    public DeLorean(
        Set<String> extraFeatures, Set<Integer> featureActivationSpeeds, @Assisted Color color) {
      this.features = extraFeatures;
      this.featureActivationSpeeds = featureActivationSpeeds;
      this.color = color;
    }
  }

  public void testTypeTokenProviderInjection() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(new TypeLiteral<Set<String>>() {}).toInstance(Collections.singleton("Datsun"));
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

    @Inject
    public Z(Provider<Set<String>> manufacturersProvider, @Assisted Color color) {
      this.manufacturersProvider = manufacturersProvider;
      this.color = color;
    }
  }

  public static class Prius implements Car {
    final Color color;

    @Inject
    private Prius(@Assisted Color color) {
      this.color = color;
    }
  }

  public void testAssistInjectionInNonPublicConstructor() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Prius.class));
              }
            });
    Prius prius = (Prius) injector.getInstance(ColoredCarFactory.class).create(Color.ORANGE);
    assertEquals(prius.color, Color.ORANGE);
  }

  public static class ExplodingCar implements Car {
    @Inject
    public ExplodingCar(@SuppressWarnings("unused") @Assisted Color color) {
      throw new IllegalStateException("kaboom!");
    }
  }

  public void testExceptionDuringConstruction() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(
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
    @Inject
    public DefectiveCar() throws ExplosionException {
      throw new ExplosionException();
    }
  }

  public static class ExplosionException extends Exception {}

  public static class FireException extends Exception {}

  public interface DefectiveCarFactoryWithNoExceptions {
    Car createCar();
  }

  public interface DefectiveCarFactory {
    Car createCar() throws FireException;
  }

  public interface CorrectDefectiveCarFactory {
    Car createCar() throws FireException, ExplosionException;
  }

  public void testConstructorExceptionsAreThrownByFactory() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(CorrectDefectiveCarFactory.class)
                    .toProvider(
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

  public static class WildcardCollection {

    public interface Factory {
      WildcardCollection create(Collection<?> items);
    }

    @Inject
    public WildcardCollection(@SuppressWarnings("unused") @Assisted Collection<?> items) {}
  }

  public void testWildcardGenerics() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(WildcardCollection.Factory.class)
                    .toProvider(
                        FactoryProvider.newFactory(
                            WildcardCollection.Factory.class, WildcardCollection.class));
              }
            });
    WildcardCollection.Factory factory = injector.getInstance(WildcardCollection.Factory.class);
    factory.create(Collections.emptyList());
  }

  public static class SteeringWheel {}

  public static class Fiat implements Car {
    private final SteeringWheel steeringWheel;
    private final Color color;

    @Inject
    public Fiat(SteeringWheel steeringWheel, @Assisted Color color) {
      this.steeringWheel = steeringWheel;
      this.color = color;
    }
  }

  public void testFactoryWithImplicitBindings() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Fiat.class));
              }
            });

    ColoredCarFactory coloredCarFactory = injector.getInstance(ColoredCarFactory.class);
    Fiat fiat = (Fiat) coloredCarFactory.create(Color.GREEN);
    assertEquals(Color.GREEN, fiat.color);
    assertNotNull(fiat.steeringWheel);
  }

  public void testFactoryFailsWithMissingBinding() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(ColoredCarFactory.class)
                  .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for java.lang.Double (with no qualifier annotation) was bound",
          "at " + ColoredCarFactory.class.getName() + ".create(FactoryProvider2Test.java");
    }
  }

  public void testFactoryFailsWithMissingBindingInToolStage() {
    try {
      Guice.createInjector(
          Stage.TOOL,
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(ColoredCarFactory.class)
                  .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "No implementation for java.lang.Double (with no qualifier annotation) was bound",
          "at " + ColoredCarFactory.class.getName() + ".create(FactoryProvider2Test.java");
    }
  }

  public void testMethodsDeclaredInObject() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).toInstance(5.0d);
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
              }
            });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    assertEqualsBothWays(carFactory, carFactory);
  }

  static class Subaru implements Car {
    @Inject @Assisted Provider<Color> colorProvider;
  }

  public void testInjectingProviderOfParameter() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class));
              }
            });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    Subaru subaru = (Subaru) carFactory.create(Color.RED);

    assertSame(Color.RED, subaru.colorProvider.get());
    assertSame(Color.RED, subaru.colorProvider.get());

    Subaru sedan = (Subaru) carFactory.create(Color.BLUE);
    assertSame(Color.BLUE, sedan.colorProvider.get());
    assertSame(Color.BLUE, sedan.colorProvider.get());

    // and make sure the subaru is still red
    assertSame(Color.RED, subaru.colorProvider.get());
  }

  public void testInjectingNullParameter() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class));
              }
            });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    Subaru subaru = (Subaru) carFactory.create(null);

    assertNull(subaru.colorProvider.get());
    assertNull(subaru.colorProvider.get());
  }

  interface ProviderBasedColoredCarFactory {
    Car createCar(Provider<Color> colorProvider, Provider<String> stringProvider);

    Mustang createMustang(@Assisted("color") Provider<Color> colorProvider);
  }

  public void testAssistedProviderIsDisallowed() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(ProviderBasedColoredCarFactory.class)
                  .toProvider(
                      FactoryProvider.newFactory(
                          ProviderBasedColoredCarFactory.class, Subaru.class));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(expected.getMessage(), 4, expected.getErrorMessages().size());
      // Assert each method individually, because JDK7 doesn't guarantee method ordering.
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [1] with key"
              + " [com.google.inject.Provider<"
              + Color.class.getName()
              + ">] on method ["
              + ProviderBasedColoredCarFactory.class.getName()
              + ".createCar()]");
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [2] with key"
              + " [com.google.inject.Provider<java.lang.String>] on method ["
              + ProviderBasedColoredCarFactory.class.getName()
              + ".createCar()]");
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [1] with key"
              + " [com.google.inject.Provider<"
              + Color.class.getName()
              + ">"
              + " annotated with @com.google.inject.assistedinject.Assisted(value="
              + Annotations.memberValueString("color")
              + ")]"
              + " on method ["
              + ProviderBasedColoredCarFactory.class.getName()
              + ".createMustang()]");
      assertContains(
          expected.getMessage(),
          ") No implementation for com.google.inject.assistedinject."
              + "FactoryProvider2Test$ProviderBasedColoredCarFactory was bound.");
    }
  }

  interface JavaxProviderBasedColoredCarFactory {
    Car createCar(
        javax.inject.Provider<Color> colorProvider, javax.inject.Provider<String> stringProvider);

    Mustang createMustang(@Assisted("color") javax.inject.Provider<Color> colorProvider);
  }

  public void testAssistedJavaxProviderIsDisallowed() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(JavaxProviderBasedColoredCarFactory.class)
                  .toProvider(
                      FactoryProvider.newFactory(
                          JavaxProviderBasedColoredCarFactory.class, Subaru.class));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertEquals(expected.getMessage(), 4, expected.getErrorMessages().size());
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [1] with key"
              + " [com.google.inject.Provider<"
              + Color.class.getName()
              + ">] on method ["
              + JavaxProviderBasedColoredCarFactory.class.getName()
              + ".createCar()]");
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [2] with key"
              + " [com.google.inject.Provider<java.lang.String>] on method ["
              + JavaxProviderBasedColoredCarFactory.class.getName()
              + ".createCar()]");
      assertContains(
          expected.getMessage(),
          ") A Provider may not be a type in a factory method of an AssistedInject."
              + "\n  Offending instance is parameter [1] with key"
              + " [com.google.inject.Provider<"
              + Color.class.getName()
              + ">"
              + " annotated with @com.google.inject.assistedinject.Assisted(value="
              + Annotations.memberValueString("color")
              + ")]"
              + " on method ["
              + JavaxProviderBasedColoredCarFactory.class.getName()
              + ".createMustang()]");
      assertContains(
          expected.getMessage(),
          ") No implementation for com.google.inject.assistedinject."
              + "FactoryProvider2Test$JavaxProviderBasedColoredCarFactory was bound.");
    }
  }

  public void testFactoryUseBeforeInitialization() {
    ColoredCarFactory carFactory =
        FactoryProvider.newFactory(ColoredCarFactory.class, Subaru.class).get();
    try {
      carFactory.create(Color.RED);
      fail();
    } catch (IllegalStateException expected) {
      assertContains(
          expected.getMessage(),
          "Factories.create() factories cannot be used until they're initialized by Guice.");
    }
  }

  interface MustangFactory {
    Mustang create(Color color);
  }

  public void testFactoryBuildingConcreteTypes() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(double.class).toInstance(5.0d);
                // note there is no 'thatMakes()' call here:
                bind(MustangFactory.class)
                    .toProvider(FactoryProvider.newFactory(MustangFactory.class, Mustang.class));
              }
            });
    MustangFactory factory = injector.getInstance(MustangFactory.class);

    Mustang mustang = factory.create(Color.RED);
    assertSame(Color.RED, mustang.color);
    assertEquals(5.0d, mustang.engineSize, 0.0);
  }

  static class Fleet {
    @Inject Mustang mustang;
    @Inject Camaro camaro;
  }

  interface FleetFactory {
    Fleet createFleet(Color color);
  }

  public void testInjectDeepIntoConstructedObjects() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(double.class).toInstance(5.0d);
                bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
                bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
                bind(FleetFactory.class)
                    .toProvider(FactoryProvider.newFactory(FleetFactory.class, Fleet.class));
              }
            });

    FleetFactory fleetFactory = injector.getInstance(FleetFactory.class);
    Fleet fleet = fleetFactory.createFleet(Color.RED);

    assertSame(Color.RED, fleet.mustang.color);
    assertEquals(5.0d, fleet.mustang.engineSize, 0.0);
    assertSame(Color.RED, fleet.camaro.color);
    assertEquals(250, fleet.camaro.horsePower);
    assertEquals(1984, fleet.camaro.modelYear);
  }

  interface TwoToneCarFactory {
    Car create(@Assisted("paint") Color paint, @Assisted("fabric") Color fabric);
  }

  static class Maxima implements Car {
    @Inject
    @Assisted("paint")
    Color paint;

    @Inject
    @Assisted("fabric")
    Color fabric;
  }

  public void testDistinctKeys() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(TwoToneCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(TwoToneCarFactory.class, Maxima.class));
              }
            });

    TwoToneCarFactory factory = injector.getInstance(TwoToneCarFactory.class);
    Maxima maxima = (Maxima) factory.create(Color.BLACK, Color.GRAY);
    assertSame(Color.BLACK, maxima.paint);
    assertSame(Color.GRAY, maxima.fabric);
  }

  interface DoubleToneCarFactory {
    Car create(@Assisted("paint") Color paint, @Assisted("paint") Color morePaint);
  }

  public void testDuplicateKeys() {
    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(DoubleToneCarFactory.class)
                  .toProvider(FactoryProvider.newFactory(DoubleToneCarFactory.class, Maxima.class));
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(),
          "A binding to "
              + Color.class.getName()
              + " annotated with @"
              + Assisted.class.getName()
              + "(value="
              + Annotations.memberValueString("paint")
              + ") was already configured at");
    }
  }

  /*if[AOP]*/
  public void testMethodInterceptorsOnAssistedTypes() {
    final AtomicInteger invocationCount = new AtomicInteger();
    final org.aopalliance.intercept.MethodInterceptor interceptor =
        new org.aopalliance.intercept.MethodInterceptor() {
          @Override
          public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation)
              throws Throwable {
            invocationCount.incrementAndGet();
            return methodInvocation.proceed();
          }
        };

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bindInterceptor(Matchers.any(), Matchers.any(), interceptor);
                bind(Double.class).toInstance(5.0d);
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
              }
            });

    ColoredCarFactory factory = injector.getInstance(ColoredCarFactory.class);
    Mustang mustang = (Mustang) factory.create(Color.GREEN);
    assertEquals(0, invocationCount.get());
    mustang.drive();
    assertEquals(1, invocationCount.get());
  }
  /*end[AOP]*/

  /**
   * Our factories aren't reusable across injectors. Although this behaviour isn't something we
   * like, I have a test case to make sure the error message is pretty.
   */
  public void testFactoryReuseErrorMessageIsPretty() {
    final Provider<ColoredCarFactory> factoryProvider =
        FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class);

    Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(Double.class).toInstance(5.0d);
            bind(ColoredCarFactory.class).toProvider(factoryProvider);
          }
        });

    try {
      Guice.createInjector(
          new AbstractModule() {
            @Override
            protected void configure() {
              bind(Double.class).toInstance(5.0d);
              bind(ColoredCarFactory.class).toProvider(factoryProvider);
            }
          });
      fail();
    } catch (CreationException expected) {
      assertContains(
          expected.getMessage(), "Factories.create() factories may only be used in one Injector!");
    }
  }

  public void testNonAssistedFactoryMethodParameter() {
    try {
      FactoryProvider.newFactory(NamedParameterFactory.class, Mustang.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(
          expected.getMessage(),
          "Only @Assisted is allowed for factory parameters, but found @" + Named.class.getName());
    }
  }

  interface NamedParameterFactory {
    Car create(@Named("seats") int seats, double engineSize);
  }

  public void testDefaultAssistedAnnotation() throws NoSuchFieldException {
    Assisted plainAssisted =
        Subaru.class.getDeclaredField("colorProvider").getAnnotation(Assisted.class);
    assertEqualsBothWays(FactoryProvider2.DEFAULT_ANNOTATION, plainAssisted);
    assertEquals(FactoryProvider2.DEFAULT_ANNOTATION.toString(), plainAssisted.toString());
  }

  interface GenericColoredCarFactory<T extends Car> {
    T create(Color color);
  }

  public void testGenericAssistedFactory() {
    final TypeLiteral<GenericColoredCarFactory<Mustang>> mustangTypeLiteral =
        new TypeLiteral<GenericColoredCarFactory<Mustang>>() {};
    final TypeLiteral<GenericColoredCarFactory<Camaro>> camaroTypeLiteral =
        new TypeLiteral<GenericColoredCarFactory<Camaro>>() {};

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).toInstance(5.0d);
                bind(int.class).annotatedWith(Names.named("horsePower")).toInstance(250);
                bind(int.class).annotatedWith(Names.named("modelYear")).toInstance(1984);
                bind(mustangTypeLiteral)
                    .toProvider(
                        FactoryProvider.newFactory(
                            mustangTypeLiteral, TypeLiteral.get(Mustang.class)));
                bind(camaroTypeLiteral)
                    .toProvider(
                        FactoryProvider.newFactory(
                            camaroTypeLiteral, TypeLiteral.get(Camaro.class)));
              }
            });

    GenericColoredCarFactory<Mustang> mustangFactory =
        injector.getInstance(Key.get(mustangTypeLiteral));
    GenericColoredCarFactory<Camaro> camaroFactory =
        injector.getInstance(Key.get(camaroTypeLiteral));

    Mustang blueMustang = mustangFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueMustang.color);
    assertEquals(5.0d, blueMustang.engineSize, 0.0);

    Camaro redCamaro = camaroFactory.create(Color.RED);
    assertEquals(Color.RED, redCamaro.color);
    assertEquals(1984, redCamaro.modelYear);
    assertEquals(250, redCamaro.horsePower);
  }

  @SuppressWarnings("unused")
  public interface Insurance<T extends Car> {}

  public static class MustangInsurance implements Insurance<Mustang> {
    private final double premium;
    private final double limit;

    @SuppressWarnings("unused")
    private Mustang car;

    @Inject
    public MustangInsurance(
        @Named("lowLimit") double limit, @Assisted Mustang car, @Assisted double premium) {
      this.premium = premium;
      this.limit = limit;
      this.car = car;
    }

    public void sell() {}
  }

  public static class CamaroInsurance implements Insurance<Camaro> {
    private final double premium;
    private final double limit;

    @SuppressWarnings("unused")
    private Camaro car;

    @Inject
    public CamaroInsurance(
        @Named("highLimit") double limit, @Assisted Camaro car, @Assisted double premium) {
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

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).annotatedWith(Names.named("lowLimit")).toInstance(50000.0d);
                bind(Double.class).annotatedWith(Names.named("highLimit")).toInstance(100000.0d);
                bind(MustangInsuranceFactory.class)
                    .toProvider(
                        FactoryProvider.newFactory(
                            MustangInsuranceFactory.class, MustangInsurance.class));
                bind(CamaroInsuranceFactory.class)
                    .toProvider(
                        FactoryProvider.newFactory(
                            CamaroInsuranceFactory.class, CamaroInsurance.class));
              }
            });

    MustangInsuranceFactory mustangInsuranceFactory =
        injector.getInstance(MustangInsuranceFactory.class);
    CamaroInsuranceFactory camaroInsuranceFactory =
        injector.getInstance(CamaroInsuranceFactory.class);

    Mustang mustang = new Mustang(5000d, Color.BLACK);
    MustangInsurance mustangPolicy =
        (MustangInsurance) mustangInsuranceFactory.create(mustang, 800.0d);
    assertEquals(800.0d, mustangPolicy.premium, 0.0);
    assertEquals(50000.0d, mustangPolicy.limit, 0.0);

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    CamaroInsurance camaroPolicy = (CamaroInsurance) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium, 0.0);
    assertEquals(100000.0d, camaroPolicy.limit, 0.0);
  }

  public interface InsuranceFactory<T extends Car> {
    public Insurance<T> create(T car, double premium);
  }

  public void testAssistedFactoryForParameterizedType() {
    final TypeLiteral<InsuranceFactory<Mustang>> mustangInsuranceFactoryType =
        new TypeLiteral<InsuranceFactory<Mustang>>() {};
    final TypeLiteral<InsuranceFactory<Camaro>> camaroInsuranceFactoryType =
        new TypeLiteral<InsuranceFactory<Camaro>>() {};

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).annotatedWith(Names.named("lowLimit")).toInstance(50000.0d);
                bind(Double.class).annotatedWith(Names.named("highLimit")).toInstance(100000.0d);
                bind(mustangInsuranceFactoryType)
                    .toProvider(
                        FactoryProvider.newFactory(
                            mustangInsuranceFactoryType, TypeLiteral.get(MustangInsurance.class)));
                bind(camaroInsuranceFactoryType)
                    .toProvider(
                        FactoryProvider.newFactory(
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
    assertEquals(800.0d, mustangPolicy.premium, 0.0);
    assertEquals(50000.0d, mustangPolicy.limit, 0.0);

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    CamaroInsurance camaroPolicy = (CamaroInsurance) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium, 0.0);
    assertEquals(100000.0d, camaroPolicy.limit, 0.0);
  }

  public static class AutoInsurance<T extends Car> implements Insurance<T> {
    private final double premium;
    private final double limit;
    private final T car;

    @Inject
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

    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).toInstance(50000.0d);
                bind(camaroInsuranceFactoryType)
                    .toProvider(
                        FactoryProvider.newFactory(
                            camaroInsuranceFactoryType,
                            new TypeLiteral<AutoInsurance<Camaro>>() {}));
              }
            });

    InsuranceFactory<Camaro> camaroInsuranceFactory =
        injector.getInstance(Key.get(camaroInsuranceFactoryType));

    Camaro camaro = new Camaro(3000, 1967, Color.BLUE);
    AutoInsurance<?> camaroPolicy =
        (AutoInsurance<?>) camaroInsuranceFactory.create(camaro, 800.0d);
    assertEquals(800.0d, camaroPolicy.premium, 0.0);
    assertEquals(50000.0d, camaroPolicy.limit, 0.0);
    assertEquals(camaro, camaroPolicy.car);
  }

  public void testInjectingAndUsingInjector() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Segway.class));
              }
            });

    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);
    Segway green = (Segway) carFactory.create(Color.GREEN);
    assertSame(Color.GREEN, green.getColor());
    assertSame(Color.GREEN, green.getColor());

    Segway pink = (Segway) carFactory.create(Color.PINK);
    assertSame(Color.PINK, pink.getColor());
    assertSame(Color.PINK, pink.getColor());
    assertSame(Color.GREEN, green.getColor());
  }

  public void testDuplicateAssistedFactoryBinding() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).toInstance(5.0d);
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
                bind(ColoredCarFactory.class)
                    .toProvider(FactoryProvider.newFactory(ColoredCarFactory.class, Mustang.class));
              }
            });
    ColoredCarFactory carFactory = injector.getInstance(ColoredCarFactory.class);

    Mustang blueMustang = (Mustang) carFactory.create(Color.BLUE);
    assertEquals(Color.BLUE, blueMustang.color);
    assertEquals(5.0d, blueMustang.engineSize, 0.0);

    Mustang redMustang = (Mustang) carFactory.create(Color.RED);
    assertEquals(Color.RED, redMustang.color);
    assertEquals(5.0d, redMustang.engineSize, 0.0);
  }

  public interface Equals {

    enum ComparisonMethod {
      SHALLOW,
      DEEP;
    }

    interface Factory {
      Equals equals(Equals.ComparisonMethod comparisonMethod);
    }

    public static class Impl implements Equals {
      private final double sigma;
      private final ComparisonMethod comparisonMethod;

      @AssistedInject
      public Impl(double sigma, @Assisted ComparisonMethod comparisonMethod) {
        this.sigma = sigma;
        this.comparisonMethod = comparisonMethod;
      }
    }
  }

  public void testFactoryMethodCalledEquals() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                bind(Double.class).toInstance(0.01d);
                bind(Equals.Factory.class)
                    .toProvider(
                        FactoryProvider.newFactory(Equals.Factory.class, Equals.Impl.class));
              }
            });
    Equals.Factory equalsFactory = injector.getInstance(Equals.Factory.class);
    Equals.Impl shallowEquals = (Impl) equalsFactory.equals(ComparisonMethod.SHALLOW);
    assertEquals(ComparisonMethod.SHALLOW, shallowEquals.comparisonMethod);
    assertEquals(0.01d, shallowEquals.sigma, 0.0);
  }

  static class Segway implements Car {
    @Inject Injector injector;

    Color getColor() {
      return injector.getInstance(Key.get(Color.class, FactoryProvider2.DEFAULT_ANNOTATION));
    }
  }

  public void testReturnValueMatchesParamValue() {
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              public void configure() {
                install(new FactoryModuleBuilder().build(Delegater.Factory.class));
              }
            });
    Delegater delegate = new Delegater();
    Delegater user = injector.getInstance(Delegater.Factory.class).create(delegate);
    assertSame(delegate, user.delegate);
  }

  static class Delegater {
    interface Factory {
      Delegater create(Delegater delegate);
    }

    private final Delegater delegate;

    @Inject
    Delegater(@Assisted Delegater delegater) {
      this.delegate = delegater;
    }

    Delegater() {
      this.delegate = null;
    }
  }

  public abstract static class AbstractAssisted {
    interface Factory<O extends AbstractAssisted, I extends CharSequence> {
      O create(I string);
    }
  }

  static class ConcreteAssisted extends AbstractAssisted {
    @Inject
    ConcreteAssisted(@SuppressWarnings("unused") @Assisted String string) {}
  }

  static class ConcreteAssistedWithOverride extends AbstractAssisted {
    @AssistedInject
    ConcreteAssistedWithOverride(@SuppressWarnings("unused") @Assisted String string) {}

    @AssistedInject
    ConcreteAssistedWithOverride(@SuppressWarnings("unused") @Assisted StringBuilder sb) {}

    interface Factory extends AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> {
      @Override
      ConcreteAssistedWithOverride create(String string);
    }

    interface Factory2 extends AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> {
      @Override
      ConcreteAssistedWithOverride create(String string);

      ConcreteAssistedWithOverride create(StringBuilder sb);
    }
  }

  static class ConcreteAssistedWithoutOverride extends AbstractAssisted {
    @Inject
    ConcreteAssistedWithoutOverride(@SuppressWarnings("unused") @Assisted String string) {}

    interface Factory extends AbstractAssisted.Factory<ConcreteAssistedWithoutOverride, String> {}
  }

  public static class Public extends AbstractAssisted {
    @AssistedInject
    Public(@SuppressWarnings("unused") @Assisted String string) {}

    @AssistedInject
    Public(@SuppressWarnings("unused") @Assisted StringBuilder sb) {}

    public interface Factory extends AbstractAssisted.Factory<Public, String> {
      @Override
      Public create(String string);

      Public create(StringBuilder sb);
    }
  }

  // See https://github.com/google/guice/issues/904
  public void testGeneratedDefaultMethodsForwardCorrectly() {
    final Key<AbstractAssisted.Factory<ConcreteAssisted, String>> concreteKey =
        new Key<AbstractAssisted.Factory<ConcreteAssisted, String>>() {};
    Injector injector =
        Guice.createInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                install(
                    new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory.class));
                install(
                    new FactoryModuleBuilder().build(ConcreteAssistedWithOverride.Factory2.class));
                install(
                    new FactoryModuleBuilder()
                        .build(ConcreteAssistedWithoutOverride.Factory.class));
                install(new FactoryModuleBuilder().build(Public.Factory.class));
                install(new FactoryModuleBuilder().build(concreteKey));
              }
            });

    ConcreteAssistedWithOverride.Factory factory1 =
        injector.getInstance(ConcreteAssistedWithOverride.Factory.class);
    factory1.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factory1Abstract = factory1;
    factory1Abstract.create("foo");

    ConcreteAssistedWithOverride.Factory2 factory2 =
        injector.getInstance(ConcreteAssistedWithOverride.Factory2.class);
    factory2.create("foo");
    factory2.create(new StringBuilder("foo"));
    AbstractAssisted.Factory<ConcreteAssistedWithOverride, String> factory2Abstract = factory2;
    factory2Abstract.create("foo");

    ConcreteAssistedWithoutOverride.Factory factory3 =
        injector.getInstance(ConcreteAssistedWithoutOverride.Factory.class);
    factory3.create("foo");
    AbstractAssisted.Factory<ConcreteAssistedWithoutOverride, String> factory3Abstract = factory3;
    factory3Abstract.create("foo");

    Public.Factory factory4 = injector.getInstance(Public.Factory.class);
    factory4.create("foo");
    factory4.create(new StringBuilder("foo"));
    AbstractAssisted.Factory<Public, String> factory4Abstract = factory4;
    factory4Abstract.create("foo");

    AbstractAssisted.Factory<ConcreteAssisted, String> factory5 = injector.getInstance(concreteKey);
    factory5.create("foo");
  }
}
