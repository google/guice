/*
 * Copyright (C) 2019 Google Inc.
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
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.util.Providers;
import dagger.Binds;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import java.lang.annotation.Retention;
import java.util.Map;
import javax.inject.Qualifier;
import junit.framework.TestCase;

/** {@link IntoMap} tests for {@link DaggerAdapter}. */

public class IntoMapTest extends TestCase {
  @dagger.Module
  static class MapBindingDaggerModule1 {
    @dagger.Provides
    @IntoMap
    @StringKey("five")
    Integer boxedPrimitive() {
      return 5;
    }

    @dagger.Provides
    @IntoMap
    @StringKey("ten")
    int primitive() {
      return 10;
    }
  }

  @dagger.Module
  static class MapBindingDaggerModule2 {
    @dagger.Provides
    @IntoMap
    @StringKey("twenty")
    Integer anInteger() {
      return 20;
    }
  }

  public void testMapBindings() {
    Injector injector =
        Guice.createInjector(
            DaggerAdapter.from(new MapBindingDaggerModule1(), new MapBindingDaggerModule2()));
    assertThat(injector.getInstance(new Key<Map<String, Integer>>() {}))
        .containsExactly("five", 5, "ten", 10, "twenty", 20);
  }

  @Qualifier
  @Retention(RUNTIME)
  public @interface AnnotationOnMap {}

  @dagger.Module
  static class MapBindingWithAnnotationDaggerModule {
    @dagger.Provides
    @IntoMap
    @StringKey("qualified")
    @AnnotationOnMap
    Integer anInteger() {
      return 4;
    }
  }

  public void testMapBindingsWithAnnotation() {
    Injector injector =
        Guice.createInjector(DaggerAdapter.from(new MapBindingWithAnnotationDaggerModule()));
    assertThat(
            injector.getInstance(
                Key.get(new TypeLiteral<Map<String, Integer>>() {}, AnnotationOnMap.class)))
        .containsExactly("qualified", 4);
  }

  static class MultibindingGuiceModule implements Module {
    @Override
    public void configure(Binder binder) {
      MapBinder<String, Integer> mb = MapBinder.newMapBinder(binder, String.class, Integer.class);
      mb.addBinding("guice-zero").toInstance(0);
      mb.addBinding("guice-provider-2").toProvider(Providers.of(2)); // mix'n'match.
    }
  }

  public void testMapBindingsWithGuiceModule() {
    Injector injector =
        Guice.createInjector(
            new MultibindingGuiceModule(),
            DaggerAdapter.from(new MapBindingDaggerModule1(), new MapBindingDaggerModule2()));
    assertThat(injector.getInstance(new Key<Map<String, Integer>>() {}))
        .containsExactly(
            "five", 5, "ten", 10, "twenty", 20, "guice-zero", 0, "guice-provider-2", 2);
  }

  @dagger.MapKey(unwrapValue = false)
  @Retention(RUNTIME)
  @interface Wrapped {
    int i();
    long l();
    String defaultValue() default "";
  }

  @dagger.Module
  static class WrappedMapKeyModule {
    @dagger.Provides
    @IntoMap
    @Wrapped(i = 0, l = 0)
    int defaultValue() {
      return 0;
    }

    @dagger.Provides
    @IntoMap
    @Wrapped(i = 1, l = 1, defaultValue = "1")
    int ones() {
      return 1;
    }

    @dagger.Provides
    @IntoMap
    @Wrapped(i = 2, l = 2, defaultValue = "2")
    int twos() {
      return 2;
    }
  }

  public void testWrappedMapKeys() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(new WrappedMapKeyModule()));
    Map<Wrapped, Integer> map = injector.getInstance(new Key<Map<Wrapped, Integer>>() {});
    assertThat(map).hasSize(3);
    map.forEach((key, value) -> {
      if (value == 0) {
        assertWrappedEquals(0, 0, "", key);
      } else if (value == 1) {
        assertWrappedEquals(1, 1, "1", key);
      } else if (value == 2) {
        assertWrappedEquals(2, 2, "2", key);
      } else {
        throw new AssertionError();
      }
    });
  }

  private static void assertWrappedEquals(int i, long l, String defaultValue, Wrapped actual) {
    assertThat(actual.i()).isEqualTo(i);
    assertThat(actual.l()).isEqualTo(l);
    assertThat(actual.defaultValue()).isEqualTo(defaultValue);
  }

  @dagger.Module
  static class HasConflict {
    @dagger.Provides
    @IntoMap
    @Wrapped(i = 1, l = 1, defaultValue = "1")
    int ones() {
      return 1;
    }
  }

  @dagger.Module
  interface BindsModule {
    @Binds
    @IntoMap
    @StringKey("hello")
    Object binds(String value);

    @dagger.Provides
    static String world() {
      return "world";
    }
  }

  public void testBinds() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(BindsModule.class));
    Map<String, Object> map = injector.getInstance(new Key<Map<String, Object>>() {});
    assertThat(map).containsExactly("hello", "world");
  }
}
