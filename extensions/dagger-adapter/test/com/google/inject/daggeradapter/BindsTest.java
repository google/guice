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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import junit.framework.TestCase;

/** Tests of {@link Binds} support in {@link DaggerAdapter}. */

public class BindsTest extends TestCase {
  @Module
  interface BasicModule {
    @Provides
    static String string() {
      return "bound";
    }

    @Binds
    CharSequence charSequence(String string);

    @Binds
    Object object(CharSequence charSequence);
  }

  public void testBinds() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(BasicModule.class));
    Binding<Object> binding = injector.getBinding(Object.class);
    assertThat(binding).hasProvidedValueThat().isEqualTo("bound");
    assertThat(binding).hasSource(BasicModule.class, "object", CharSequence.class);
  }

  @Module
  static class CountingMultibindingProviderModule {
    int count = 0;

    @Provides
    String provider() {
      count++;
      return "multibound-" + count;
    }
  }

  @Module
  interface MultibindingBindsModule {
    @Binds
    @IntoSet
    Object fromString(String string);

    @Binds
    CharSequence toCharSequence(String string);

    @Binds
    @IntoSet
    Object fromCharSequence(CharSequence charSequence);
  }

  public void testMultibindings() {
    Injector injector =
        Guice.createInjector(
            DaggerAdapter.from(
                new CountingMultibindingProviderModule(), MultibindingBindsModule.class));

    Binding<Set<Object>> binding = injector.getBinding(new Key<Set<Object>>() {});
    assertThat(binding)
        .hasProvidedValueThat()
        .isEqualTo(ImmutableSet.of("multibound-1", "multibound-2"));
    assertThat(binding)
        .hasProvidedValueThat()
        .isEqualTo(ImmutableSet.of("multibound-3", "multibound-4"));
  }

  @Module
  interface ScopedMultibindingBindsModule {
    @Binds
    @IntoSet
    @Singleton
    Object fromString(String string);

    @Binds
    CharSequence toCharSequence(String string);

    @Binds
    @IntoSet
    @Singleton
    Object fromCharSequence(CharSequence charSequence);
  }

  public void testScopedMultibindings() {
    Injector injector =
        Guice.createInjector(
            DaggerAdapter.from(
                new CountingMultibindingProviderModule(), ScopedMultibindingBindsModule.class));

    Binding<Set<Object>> binding = injector.getBinding(new Key<Set<Object>>() {});
    assertThat(binding)
        .hasProvidedValueThat()
        .isEqualTo(ImmutableSet.of("multibound-1", "multibound-2"));
    assertThat(binding)
        .hasProvidedValueThat()
        .isEqualTo(ImmutableSet.of("multibound-1", "multibound-2"));
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface ProvidesQualifier {}

  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface BindsQualifier {}

  @Module
  interface QualifiedBinds {
    @Provides
    @ProvidesQualifier
    static String provides() {
      return "qualifiers";
    }

    @Binds
    @BindsQualifier
    String bindsToProvides(@ProvidesQualifier String provides);

    @Binds
    String unqualifiedToBinds(@BindsQualifier String binds);
  }

  public void testQualifiers() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(QualifiedBinds.class));

    Binding<String> stringBinding = injector.getBinding(String.class);
    assertThat(stringBinding).hasProvidedValueThat().isEqualTo("qualifiers");
    assertThat(stringBinding).hasSource(QualifiedBinds.class, "unqualifiedToBinds", String.class);

    Binding<String> qualifiedBinds =
        injector.getBinding(Key.get(String.class, BindsQualifier.class));
    assertThat(qualifiedBinds).hasProvidedValueThat().isEqualTo("qualifiers");
    assertThat(qualifiedBinds).hasSource(QualifiedBinds.class, "bindsToProvides", String.class);
  }
}
