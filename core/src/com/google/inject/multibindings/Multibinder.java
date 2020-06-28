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

package com.google.inject.multibindings;

import static com.google.inject.internal.RealMultibinder.newRealSetBinder;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.RealMultibinder;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Set;

/**
 * An API to bind multiple values separately, only to later inject them as a complete collection.
 * Multibinder is intended for use in your application's module:
 *
 * <pre><code>
 * public class SnacksModule extends AbstractModule {
 *   protected void configure() {
 *     Multibinder&lt;Snack&gt; multibinder
 *         = Multibinder.newSetBinder(binder(), Snack.class);
 *     multibinder.addBinding().toInstance(new Twix());
 *     multibinder.addBinding().toProvider(SnickersProvider.class);
 *     multibinder.addBinding().to(Skittles.class);
 *   }
 * }</code></pre>
 *
 * <p>With this binding, a {@link Set}{@code <Snack>} can now be injected:
 *
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Set&lt;Snack&gt; snacks) { ... }
 * }</code></pre>
 *
 * If desired, {@link Collection}{@code <Provider<Snack>>} can also be injected.
 *
 * <p>Contributing multibindings from different modules is supported. For example, it is okay for
 * both {@code CandyModule} and {@code ChipsModule} to create their own {@code Multibinder<Snack>},
 * and to each contribute bindings to the set of snacks. When that set is injected, it will contain
 * elements from both modules.
 *
 * <p>The set's iteration order is consistent with the binding order. This is convenient when
 * multiple elements are contributed by the same module because that module can order its bindings
 * appropriately. Avoid relying on the iteration order of elements contributed by different modules,
 * since there is no equivalent mechanism to order modules.
 *
 * <p>The set is unmodifiable. Elements can only be added to the set by configuring the multibinder.
 * Elements can never be removed from the set.
 *
 * <p>Elements are resolved at set injection time. If an element is bound to a provider, that
 * provider's get method will be called each time the set is injected (unless the binding is also
 * scoped).
 *
 * <p>Annotations are used to create different sets of the same element type. Each distinct
 * annotation gets its own independent collection of elements.
 *
 * <p><strong>Elements must be distinct.</strong> If multiple bound elements have the same value,
 * set injection will fail.
 *
 * <p><strong>Elements must be non-null.</strong> If any set element is null, set injection will
 * fail.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class Multibinder<T> {
  // This class is non-final due to users mocking this in tests :(

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with no binding annotation.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, TypeLiteral<T> type) {
    return newSetBinder(binder, Key.get(type));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with no binding annotation.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, Class<T> type) {
    return newSetBinder(binder, Key.get(type));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotation}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, TypeLiteral<T> type, Annotation annotation) {
    return newSetBinder(binder, Key.get(type, annotation));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotation}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, Class<T> type, Annotation annotation) {
    return newSetBinder(binder, Key.get(type, annotation));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotationType}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, TypeLiteral<T> type, Class<? extends Annotation> annotationType) {
    return newSetBinder(binder, Key.get(type, annotationType));
  }

  /**
   * Returns a new multibinder that collects instances of the key's type in a {@link Set} that is
   * itself bound with the annotation (if any) of the key.
   *
   * @since 4.0
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, Key<T> key) {
    return new Multibinder<T>(newRealSetBinder(binder.skipSources(Multibinder.class), key));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotationType}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, Class<T> type, Class<? extends Annotation> annotationType) {
    return newSetBinder(binder, Key.get(type, annotationType));
  }

  private final RealMultibinder<T> delegate;

  private Multibinder(RealMultibinder<T> delegate) {
    this.delegate = delegate;
  }

  /**
   * Configures the bound set to silently discard duplicate elements. When multiple equal values are
   * bound, the one that gets included is arbitrary. When multiple modules contribute elements to
   * the set, this configuration option impacts all of them.
   *
   * @return this multibinder
   * @since 3.0
   */
  public Multibinder<T> permitDuplicates() {
    delegate.permitDuplicates();
    return this;
  }

  /**
   * Returns a binding builder used to add a new element in the set. Each bound element must have a
   * distinct value. Bound providers will be evaluated each time the set is injected.
   *
   * <p>It is an error to call this method without also calling one of the {@code to} methods on the
   * returned binding builder.
   *
   * <p>Scoping elements independently is supported. Use the {@code in} method to specify a binding
   * scope.
   */
  public LinkedBindingBuilder<T> addBinding() {
    return delegate.addBinding();
  }

  // Some tests rely on Multibinder implementing equals/hashCode

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Multibinder) {
      return delegate.equals(((Multibinder<?>) obj).delegate);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
