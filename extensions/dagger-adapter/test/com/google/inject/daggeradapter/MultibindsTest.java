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

import static com.google.inject.daggeradapter.BindingSubject.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import dagger.Module;
import dagger.multibindings.Multibinds;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.Set;
import javax.inject.Qualifier;
import junit.framework.TestCase;

/** Tests of {@link Multibinds} support in {@link DaggerAdapter}. */

public class MultibindsTest extends TestCase {
  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface TestQualifier {}

  @Module
  interface BasicModule {
    @Multibinds
    Set<Number> set();

    @Multibinds
    Map<Integer, Double> map();

    @Multibinds
    @TestQualifier
    Set<Number> qualifiedSet();

    @Multibinds
    @TestQualifier
    Map<Integer, Double> qualifiedMap();
  }

  public void testBinds() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(BasicModule.class));

    Binding<Set<Number>> setBinding = injector.getBinding(new Key<Set<Number>>() {});
    assertThat(setBinding).hasProvidedValueThat().isEqualTo(ImmutableSet.of());
    assertThat(setBinding).hasSource(BasicModule.class, "set");

    Binding<Map<Integer, Double>> mapBinding =
        injector.getBinding(new Key<Map<Integer, Double>>() {});
    assertThat(mapBinding).hasProvidedValueThat().isEqualTo(ImmutableMap.of());
    assertThat(mapBinding).hasSource(BasicModule.class, "map");

    Binding<Set<Number>> qualifiedSetBinding =
        injector.getBinding(Key.get(new TypeLiteral<Set<Number>>() {}, TestQualifier.class));
    assertThat(qualifiedSetBinding).hasProvidedValueThat().isEqualTo(ImmutableSet.of());
    assertThat(qualifiedSetBinding).hasSource(BasicModule.class, "qualifiedSet");

    Binding<Map<Integer, Double>> qualifiedMapBinding =
        injector.getBinding(
            Key.get(new TypeLiteral<Map<Integer, Double>>() {}, TestQualifier.class));
    assertThat(qualifiedMapBinding).hasProvidedValueThat().isEqualTo(ImmutableMap.of());
    assertThat(qualifiedMapBinding).hasSource(BasicModule.class, "qualifiedMap");
  }
}
