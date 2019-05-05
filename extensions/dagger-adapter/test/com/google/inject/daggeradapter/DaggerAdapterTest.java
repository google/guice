/*
 * Copyright (C) 2015 Google Inc.
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
package com.google.inject.daggeradapter;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.util.Providers;
import dagger.multibindings.ElementsIntoSet;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;
import javax.inject.Qualifier;
import junit.framework.TestCase;

/**
 * Tests for {@link DaggerAdapter}.
 *
 * @author cgruber@google.com (Christian Gruber)
 */

public class DaggerAdapterTest extends TestCase {
  @dagger.Module
  static class SimpleDaggerModule {
    @dagger.Provides
    Integer anInteger() {
      return 1;
    }
  }

  public void testSimpleModule() {
    Injector i = Guice.createInjector(DaggerAdapter.from(new SimpleDaggerModule()));
    assertEquals((Integer) 1, i.getInstance(Integer.class));
  }

  static class SimpleGuiceModule extends AbstractModule {
    @Provides
    String aString(Integer i) {
      return i.toString();
    }
  }

  public void testInteractionWithGuiceModules() {
    Injector i =
        Guice.createInjector(new SimpleGuiceModule(), DaggerAdapter.from(new SimpleDaggerModule()));
    assertEquals("1", i.getInstance(String.class));
  }

  @dagger.Module
  static class SetBindingDaggerModule1 {
    @dagger.Provides
    @IntoSet
    Integer anInteger() {
      return 5;
    }
  }

  @dagger.Module
  static class SetBindingDaggerModule2 {
    @dagger.Provides
    @IntoSet
    Integer anInteger() {
      return 3;
    }
  }

  public void testSetBindings() {
    Injector i =
        Guice.createInjector(
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(3, 5), i.getInstance(new Key<Set<Integer>>() {}));
  }

  @Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  public @interface AnnotationOnSet {}

  @dagger.Module
  static class SetBindingWithAnnotationDaggerModule {
    @dagger.Provides
    @IntoSet
    @AnnotationOnSet
    Integer anInteger() {
      return 4;
    }
  }

  public void testSetBindingsWithAnnotation() {
    Injector i =
        Guice.createInjector(DaggerAdapter.from(new SetBindingWithAnnotationDaggerModule()));
    assertEquals(
        ImmutableSet.of(4),
        i.getInstance(Key.get(new TypeLiteral<Set<Integer>>() {}, AnnotationOnSet.class)));
  }

  static class MultibindingGuiceModule implements Module {
    @Override
    public void configure(Binder binder) {
      Multibinder<Integer> mb = Multibinder.newSetBinder(binder, Integer.class);
      mb.addBinding().toInstance(13);
      mb.addBinding().toProvider(Providers.of(8)); // mix'n'match.
    }
  }

  public void testSetBindingsWithGuiceModule() {
    Injector i =
        Guice.createInjector(
            new MultibindingGuiceModule(),
            DaggerAdapter.from(new SetBindingDaggerModule1(), new SetBindingDaggerModule2()));
    assertEquals(ImmutableSet.of(13, 3, 5, 8), i.getInstance(new Key<Set<Integer>>() {}));
  }

  @dagger.Module
  static class UnsupportedAnnotationModule {
    @dagger.Provides
    @ElementsIntoSet
    Set<Object> noGuiceEquivalentForElementsIntoSet() {
      return ImmutableSet.of();
    }
  }

  @dagger.Module
  static class UnsupportedAnnotationSubclassModule extends UnsupportedAnnotationModule {}

  public void testUnsupportedBindingAnnotation() {
    try {
      Guice.createInjector(DaggerAdapter.from(new UnsupportedAnnotationModule()));
      fail();
    } catch (CreationException expected) {
    }
  }

  public void testUnsupportedBindingAnnotationFromModuleSuperclass() {
    try {
      Guice.createInjector(DaggerAdapter.from(new UnsupportedAnnotationSubclassModule()));
      fail();
    } catch (CreationException expected) {
    }
  }

  // TODO(ronshapiro): break this class into smaller files.

  @dagger.Module
  static class StaticProvidesMethods {
    @dagger.Provides
    static String string() {
      return "class";
    }
  }

  @dagger.Module
  interface StaticProvidesMethodsInterface {
    @dagger.Provides
    static String string() {
      return "interface";
    }
  }

  public void testStaticProvidesMethods() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(new StaticProvidesMethods()));
    String staticProvision = injector.getInstance(String.class);
    assertEquals("class", staticProvision);
  }

  public void testStaticProvidesMethods_classLiteral() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(StaticProvidesMethods.class));
    String staticProvision = injector.getInstance(String.class);
    assertEquals("class", staticProvision);
  }

  public void testStaticProvidesMethods_interface() {
    Injector injector =
        Guice.createInjector(DaggerAdapter.from(StaticProvidesMethodsInterface.class));
    String staticProvision = injector.getInstance(String.class);
    assertEquals("interface", staticProvision);
  }

  @dagger.Module
  static class ModuleWithInstanceMethods {
    @dagger.Provides
    int i() {
      return 0;
    }
  }

  public void testClassLiteralWithInstanceProvidesMethod() {
    try {
      Guice.createInjector(DaggerAdapter.from(ModuleWithInstanceMethods.class));
      fail();
    } catch (CreationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains(
              "ModuleWithInstanceMethods.i() is an instance method, but a class literal was"
                  + " passed. Make this method static or pass an instance of the module instead.");
    }
  }

  public void testModuleObjectsMustBeDaggerModules() {
    try {
      Guice.createInjector(DaggerAdapter.from(new Object()));
      fail();
    } catch (CreationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains("Object must be annotated with @dagger.Module");
    }
  }

  @dagger.producers.ProducerModule
  static class ProducerModuleWithProvidesMethod {
    @dagger.Provides
    int i() {
      return 1;
    }
  }

  public void testProducerModulesNotSupported() {
    try {
      Guice.createInjector(DaggerAdapter.from(new ProducerModuleWithProvidesMethod()));
      fail();
    } catch (CreationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains("ProducerModuleWithProvidesMethod must be annotated with @dagger.Module");
    }
  }
}
