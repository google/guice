/*
 * Copyright (C) 2014 Google Inc.
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

import static com.google.inject.internal.RealOptionalBinder.newRealOptionalBinder;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.RealOptionalBinder;

/**
 * An API to bind optional values, optionally with a default value. OptionalBinder fulfills two
 * roles:
 *
 * <ol>
 *   <li>It allows a framework to define an injection point that may or may not be bound by users.
 *   <li>It allows a framework to supply a default value that can be changed by users.
 * </ol>
 *
 * <p>When an OptionalBinder is added, it will always supply the bindings: {@code Optional<T>} and
 * {@code Optional<Provider<T>>}. Both {@link java.util.Optional java.util.Optional} and {@link
 * com.google.common.base.Optional com.google.common.base.Optional} are bound for compatibility. If
 * {@link #setBinding} or {@link #setDefault} are called, it will also bind {@code T}.
 *
 * <p>{@code setDefault} is intended for use by frameworks that need a default value. User code can
 * call {@code setBinding} to override the default. <b>Warning: Even if setBinding is called, the
 * default binding will still exist in the object graph. If it is a singleton, it will be
 * instantiated in {@code Stage.PRODUCTION}.</b>
 *
 * <p>If setDefault or setBinding are linked to Providers, the Provider may return {@code null}. If
 * it does, {@code Optional<T>} will be bound to an absent Optional. Binding setBinding to a
 * Provider that returns null will not cause OptionalBinder to fall back to the setDefault binding.
 *
 * <p>If neither setDefault nor setBinding are called, it will try to link to a user-supplied
 * binding of the same type. If no binding exists, the optionals will be absent. Otherwise, if a
 * user-supplied binding of that type exists, or if setBinding or setDefault are called, the
 * optionals will return present if they are bound to a non-null value.
 *
 * <p>Values are resolved at injection time. If a value is bound to a provider, that provider's get
 * method will be called each time the optional is injected (unless the binding is also scoped, or
 * an optional of provider is injected).
 *
 * <p>Annotations are used to create different optionals of the same key/value type. Each distinct
 * annotation gets its own independent binding.
 *
 * <pre><code>
 * public class FrameworkModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Renamer.class);
 *   }
 * }</code></pre>
 *
 * <p>With this module, an {@code Optional<Renamer>} can now be injected. With no other bindings,
 * the optional will be absent. Users can specify bindings in one of two ways:
 *
 * <p>Option 1:
 *
 * <pre><code>
 * public class UserRenamerModule extends AbstractModule {
 *   protected void configure() {
 *     bind(Renamer.class).to(ReplacingRenamer.class);
 *   }
 * }</code></pre>
 *
 * <p>or Option 2:
 *
 * <pre><code>
 * public class UserRenamerModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Renamer.class)
 *         .setBinding().to(ReplacingRenamer.class);
 *   }
 * }</code></pre>
 *
 * With both options, the {@code Optional<Renamer>} will be present and supply the ReplacingRenamer.
 *
 * <p>Default values can be supplied using:
 *
 * <pre><code>
 * public class FrameworkModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, LookupUrl.class))
 *         .setDefault().toInstance(DEFAULT_LOOKUP_URL);
 *   }
 * }</code></pre>
 *
 * With the above module, code can inject an {@code @LookupUrl String} and it will supply the
 * DEFAULT_LOOKUP_URL. A user can change this value by binding
 *
 * <pre><code>
 * public class UserLookupModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, LookupUrl.class))
 *         .setBinding().toInstance(CUSTOM_LOOKUP_URL);
 *   }
 * }</code></pre>
 *
 * ... which will override the default value.
 *
 * <p>If one module uses setDefault the only way to override the default is to use setBinding. It is
 * an error for a user to specify the binding without using OptionalBinder if setDefault or
 * setBinding are called. For example,
 *
 * <pre><code>
 * public class FrameworkModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, LookupUrl.class))
 *         .setDefault().toInstance(DEFAULT_LOOKUP_URL);
 *   }
 * }
 * public class UserLookupModule extends AbstractModule {
 *   protected void configure() {
 *     bind(Key.get(String.class, LookupUrl.class)).toInstance(CUSTOM_LOOKUP_URL);
 *   }
 * }</code></pre>
 *
 * ... would generate an error, because both the framework and the user are trying to bind
 * {@code @LookupUrl String}.
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.0
 */
public class OptionalBinder<T> {
  // This class is non-final due to users mocking this in tests :(

  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, Class<T> type) {
    return new OptionalBinder<T>(
        newRealOptionalBinder(binder.skipSources(OptionalBinder.class), Key.get(type)));
  }

  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, TypeLiteral<T> type) {
    return new OptionalBinder<T>(
        newRealOptionalBinder(binder.skipSources(OptionalBinder.class), Key.get(type)));
  }

  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, Key<T> type) {
    return new OptionalBinder<T>(
        newRealOptionalBinder(binder.skipSources(OptionalBinder.class), type));
  }

  private final RealOptionalBinder<T> delegate;

  private OptionalBinder(RealOptionalBinder<T> delegate) {
    this.delegate = delegate;
  }

  /**
   * Returns a binding builder used to set the default value that will be injected. The binding set
   * by this method will be ignored if {@link #setBinding} is called.
   *
   * <p>It is an error to call this method without also calling one of the {@code to} methods on the
   * returned binding builder.
   */
  public LinkedBindingBuilder<T> setDefault() {
    return delegate.setDefault();
  }

  /**
   * Returns a binding builder used to set the actual value that will be injected. This overrides
   * any binding set by {@link #setDefault}.
   *
   * <p>It is an error to call this method without also calling one of the {@code to} methods on the
   * returned binding builder.
   */
  public LinkedBindingBuilder<T> setBinding() {
    return delegate.setBinding();
  }

  // Some tests depend on equals/hashCode behavior of OptionalBinder

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof OptionalBinder) {
      return delegate.equals(((OptionalBinder<?>) obj).delegate);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }
}
