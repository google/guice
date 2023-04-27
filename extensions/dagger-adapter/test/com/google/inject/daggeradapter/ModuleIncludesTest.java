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
import static org.junit.jupiter.api.Assertions.fail;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dagger.Module;
import dagger.Provides;
import org.junit.jupiter.api.Test;

/** Tests for {@code @Module.includes} */

public class ModuleIncludesTest {
  @Module(includes = Included.class)
  static class Declared {
    @Provides
    static Object object() {
      return new Object();
    }
  }

  @Module
  static class Included {
    @Provides
    static String string(Object object) {
      return "included";
    }
  }

  @Test
  public void testIncludedModules() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(Declared.class, Included.class));
    assertThat(injector.getInstance(String.class)).isEqualTo("included");
  }

  @Module
  static class Deduplicated {
    @Provides
    static String string() {
      return "deduplicated";
    }
  }

  @Module(includes = Deduplicated.class)
  static class Includes1 {}

  @Module(includes = Deduplicated.class)
  static class Includes2 {}

  @Test
  public void testDeduplicated() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(Includes1.class, Includes2.class));
    assertThat(injector.getInstance(String.class)).isEqualTo("deduplicated");
  }

  @Test
  public void testInstanceOfModuleAndClassLiteral() {
    Guice.createInjector(DaggerAdapter.from(Deduplicated.class, new Deduplicated()));
  }

  // ProviderMethodsModule, which DaggerAdapter uses under the hood, de-duplicates modules that have
  // the same Scanner instance and same delegate module. So any Class object passed to DaggerAdapter
  // should be fine to use.
  @Test
  public void testDeduplicatedModulesFromSeparateDaggerAdapters() {
    Injector injector =
        Guice.createInjector(
            DaggerAdapter.from(Includes1.class),
            DaggerAdapter.from(Includes1.class),
            DaggerAdapter.from(Includes2.class),
            DaggerAdapter.from(Includes2.class));
    assertThat(injector.getInstance(String.class)).isEqualTo("deduplicated");
  }

  @Module
  static final class ModuleWithIdentity {}

  @Test
  public void testConflictingModuleInstances() {
    try {
      Guice.createInjector(DaggerAdapter.from(new ModuleWithIdentity(), new ModuleWithIdentity()));
      fail();
    } catch (CreationException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              "Duplicate module instances provided for ModuleIncludesTest$ModuleWithIdentity:");
    }
  }

  @Module
  static final class ModuleWithInstanceProvidesMethod {
    private int i;

    @Provides
    int i() {
      return i++;
    }
  }

  @Test
  public void testInstanceOfModuleAndClassLiteral_InstanceWins() {
    Injector instanceModuleFirst =
        Guice.createInjector(
            DaggerAdapter.from(
                new ModuleWithInstanceProvidesMethod(), ModuleWithInstanceProvidesMethod.class));
    assertThat(instanceModuleFirst.getInstance(Integer.class)).isEqualTo(0);
    assertThat(instanceModuleFirst.getInstance(Integer.class)).isEqualTo(1);

    Injector classLiteralFirst =
        Guice.createInjector(
            DaggerAdapter.from(
                ModuleWithInstanceProvidesMethod.class, new ModuleWithInstanceProvidesMethod()));
    assertThat(classLiteralFirst.getInstance(Integer.class)).isEqualTo(0);
    assertThat(classLiteralFirst.getInstance(Integer.class)).isEqualTo(1);
  }

  @Module(includes = ModuleWithInstanceProvidesMethod.class)
  static final class IncludesInstanceModule {}

  @Test
  public void testIncludesInstanceModule() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(IncludesInstanceModule.class));
    assertThat(injector.getInstance(Integer.class)).isEqualTo(0);
    assertThat(injector.getInstance(Integer.class)).isEqualTo(1);
  }

  @Module
  static final class NonInstantiableModuleWithInstanceProvidesMethod {
    private int i;

    NonInstantiableModuleWithInstanceProvidesMethod(int i) {
      throw new RuntimeException("nothing should ever instantiate this");
    }

    @Provides
    int i() {
      return i++;
    }
  }

  @Module(includes = NonInstantiableModuleWithInstanceProvidesMethod.class)
  static final class IncludesNonInstantiableInstanceModule {}

  @Test
  public void testIncludesNonInstantiableInstanceModule() {
    try {
      Guice.createInjector(DaggerAdapter.from(IncludesNonInstantiableInstanceModule.class));
      fail();
    } catch (CreationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains(
              "NonInstantiableModuleWithInstanceProvidesMethod.i() is an instance method,"
                  + " but a class literal was passed. Make this method static or pass an instance"
                  + " of the module instead.");
    }
  }
}
