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

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import dagger.BindsOptionalOf;
import dagger.Module;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import javax.inject.Qualifier;
import junit.framework.TestCase;

/** Tests of {@link BindsOptionalOf} support in {@link DaggerAdapter}. */

public class OptionalBindingsTest extends TestCase {
  @Retention(RetentionPolicy.RUNTIME)
  @Qualifier
  @interface TestQualifier {}

  @Module
  interface BasicModule {
    @BindsOptionalOf
    String optionalString();

    @BindsOptionalOf
    @TestQualifier
    Integer optionalQualifiedInteger();
  }

  public void testBinds() {
    Injector injector = Guice.createInjector(DaggerAdapter.from(BasicModule.class));

    Binding<Optional<String>> optionalBinding = injector.getBinding(new Key<Optional<String>>() {});
    assertThat(optionalBinding).hasProvidedValueThat().isEqualTo(Optional.empty());
    assertThat(optionalBinding).hasSource(BasicModule.class, "optionalString");

    Binding<Optional<Integer>> qualifiedOptionalBinding =
        injector.getBinding(Key.get(new TypeLiteral<Optional<Integer>>() {}, TestQualifier.class));
    assertThat(qualifiedOptionalBinding).hasProvidedValueThat().isEqualTo(Optional.empty());
    assertThat(qualifiedOptionalBinding).hasSource(BasicModule.class, "optionalQualifiedInteger");
  }
}
