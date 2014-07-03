/**
 * Copyright (C) 2011 Google Inc.
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

package com.google.inject.servlet;

import static com.google.inject.name.Names.named;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.PrivateElements;
import com.google.inject.util.Providers;

import junit.framework.TestCase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link ServletScopes}.
 *
 * @author forster@google.com (Mike Forster)
 */
public class ServletScopesTest extends TestCase {
  public void testIsRequestScopedPositive() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<Object> e = Key.get(Object.class, named("E"));
    final Key<String> f = Key.get(String.class, named("F"));
    final Key<String> g = Key.get(String.class, named("G"));

    Module requestScopedBindings = new AbstractModule() {
      @Override
      protected void configure() {
        bind(a).to(b);
        bind(b).to(c);
        bind(c).toProvider(Providers.of("c")).in(ServletScopes.REQUEST);
        bind(d).toProvider(Providers.of("d")).in(RequestScoped.class);
        bind(e).to(AnnotatedRequestScopedClass.class);
        install(new PrivateModule() {
          @Override
          protected void configure() {
            bind(f).toProvider(Providers.of("f")).in(RequestScoped.class);
            expose(f);
          }
        });
      }

      @Provides
      @Named("G")
      @RequestScoped
      String provideG() {
        return "g";
      }
    };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(requestScopedBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    // linked bindings are not followed by modules
    assertFalse(ServletScopes.isRequestScoped(map.get(a)));
    assertFalse(ServletScopes.isRequestScoped(map.get(b)));
    assertTrue(ServletScopes.isRequestScoped(map.get(c)));
    assertTrue(ServletScopes.isRequestScoped(map.get(d)));
    // annotated classes are not followed by modules
    assertFalse(ServletScopes.isRequestScoped(map.get(e)));
    assertTrue(ServletScopes.isRequestScoped(map.get(f)));
    assertTrue(ServletScopes.isRequestScoped(map.get(g)));

    Injector injector = Guice.createInjector(requestScopedBindings, new ServletModule());
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(a)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(b)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(c)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(d)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(e)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(f)));
    assertTrue(ServletScopes.isRequestScoped(injector.getBinding(g)));
  }

  public void testIsRequestScopedNegative() {
    final Key<String> a = Key.get(String.class, named("A"));
    final Key<String> b = Key.get(String.class, named("B"));
    final Key<String> c = Key.get(String.class, named("C"));
    final Key<String> d = Key.get(String.class, named("D"));
    final Key<String> e = Key.get(String.class, named("E"));
    final Key<String> f = Key.get(String.class, named("F"));
    final Key<String> g = Key.get(String.class, named("G"));
    final Key<String> h = Key.get(String.class, named("H"));
    final Key<String> i = Key.get(String.class, named("I"));
    final Key<String> j = Key.get(String.class, named("J"));

    Module requestScopedBindings = new AbstractModule() {
      @Override
      protected void configure() {
        bind(a).to(b);
        bind(b).to(c);
        bind(c).toProvider(Providers.of("c")).in(Scopes.NO_SCOPE);
        bind(d).toInstance("d");
        bind(e).toProvider(Providers.of("e")).asEagerSingleton();
        bind(f).toProvider(Providers.of("f")).in(Scopes.SINGLETON);
        bind(g).toProvider(Providers.of("g")).in(Singleton.class);
        bind(h).toProvider(Providers.of("h")).in(CustomScoped.class);
        bindScope(CustomScoped.class, Scopes.NO_SCOPE);
        install(new PrivateModule() {
          @Override
          protected void configure() {
            bind(i).toProvider(Providers.of("i")).in(CustomScoped.class);
            expose(i);
          }
        });
      }

      @Provides
      @Named("J")
      @CustomScoped
      String provideJ() {
        return "j";
      }
    };

    @SuppressWarnings("unchecked") // we know the module contains only bindings
    List<Element> moduleBindings = Elements.getElements(requestScopedBindings);
    ImmutableMap<Key<?>, Binding<?>> map = indexBindings(moduleBindings);
    assertFalse(ServletScopes.isRequestScoped(map.get(a)));
    assertFalse(ServletScopes.isRequestScoped(map.get(b)));
    assertFalse(ServletScopes.isRequestScoped(map.get(c)));
    assertFalse(ServletScopes.isRequestScoped(map.get(d)));
    assertFalse(ServletScopes.isRequestScoped(map.get(e)));
    assertFalse(ServletScopes.isRequestScoped(map.get(f)));
    assertFalse(ServletScopes.isRequestScoped(map.get(g)));
    assertFalse(ServletScopes.isRequestScoped(map.get(h)));
    assertFalse(ServletScopes.isRequestScoped(map.get(i)));
    assertFalse(ServletScopes.isRequestScoped(map.get(j)));

    Injector injector = Guice.createInjector(requestScopedBindings);
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(a)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(b)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(c)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(d)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(e)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(f)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(g)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(h)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(i)));
    assertFalse(ServletScopes.isRequestScoped(injector.getBinding(j)));
  }

  @RequestScoped
  static class AnnotatedRequestScopedClass {}

  @Target({ ElementType.TYPE, ElementType.METHOD })
  @Retention(RUNTIME)
  @ScopeAnnotation
  private @interface CustomScoped {}

  private ImmutableMap<Key<?>, Binding<?>> indexBindings(Iterable<Element> elements) {
    ImmutableMap.Builder<Key<?>, Binding<?>> builder = ImmutableMap.builder();
    for (Element element : elements) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        builder.put(binding.getKey(), binding);
      } else if (element instanceof PrivateElements) {
        PrivateElements privateElements = (PrivateElements) element;
        Map<Key<?>, Binding<?>> privateBindings = indexBindings(privateElements.getElements());
        for (Key<?> exposed : privateElements.getExposedKeys()) {
          builder.put(exposed, privateBindings.get(exposed));
        }
      }
    }
    return builder.build();
  }
}
