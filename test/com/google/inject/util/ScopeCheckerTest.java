/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.util;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.ConfigurationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Singleton;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ScopeCheckerTest extends TestCase {

  @Target(TYPE) @Retention(RUNTIME) @ScopeAnnotation
  @interface Annually {}

  @Target(TYPE) @Retention(RUNTIME) @ScopeAnnotation
  @interface Seasonally {}

  @Target(TYPE) @Retention(RUNTIME) @ScopeAnnotation
  @interface Daily {}

  Module scopesModule = new AbstractModule() {
    protected void configure() {
      bindScope(Annually.class, newScope());
      bindScope(Seasonally.class, newScope());
      bindScope(Daily.class, newScope());
    }
  };

  /** change your shirt daily. Depends on the sleeve length appropriate for the weather */
  static class Shirt {
    @Inject SleeveLength sleeveLength;
  }

  /** long sleeves in the winter, short sleeves in the summer, etc. */
  static class SleeveLength {
    @Inject Style style;
  }

  /** fashion evolves over time */
  static class Style {}

  /** pants can be tweaked (with belts) to fit a changing style */
  static class Pants {
    @Inject Provider<Style> style;
  }

  public void testProperlyNestedScopes() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class).in(Annually.class);
        bind(SleeveLength.class).in(Seasonally.class);
        bind(Shirt.class).in(Daily.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
  }

  public void testDependingOnUnscoped() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class);
        bind(SleeveLength.class);
        bind(Shirt.class).in(Daily.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
  }

  public void testUsedByUnscoped() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class).in(Annually.class);
        bind(SleeveLength.class);
        bind(Shirt.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
  }

  public void testDirectViolation() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class).in(Annually.class);
        bind(SleeveLength.class).in(Seasonally.class);
        bind(Shirt.class).in(Annually.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    try {
      scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "1) Illegal scoped dependency: " + Shirt.class.getName() + " in @Annually",
          "  depends on " + SleeveLength.class.getName() + " in @Seasonally");
    }
  }

  public void testDirectDependencyOnProvider() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class).in(Daily.class);
        bind(Pants.class).in(Seasonally.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
  }

  public void testIndirectViolation() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Style.class).in(Seasonally.class);
        bind(Shirt.class).in(Annually.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    try {
      scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "1) Illegal scoped dependency: " + Shirt.class.getName() + " in @Annually",
          "  depends on " + SleeveLength.class.getName(),
          "  depends on " + Style.class.getName() + " in @Seasonally");
    }
  }

  public void testValidCircularDependency() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Chicken.class).in(Daily.class);
        bind(Egg.class).in(Daily.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
  }

  public void testInvalidCircularDependency() {
    Module module = new AbstractModule() {
      protected void configure() {
        bind(Chicken.class).in(Seasonally.class);
        bind(Egg.class).in(Daily.class);
      }
    };

    ScopeChecker scopeChecker = new ScopeChecker(Guice.createInjector(scopesModule, module));
    try {
      scopeChecker.check(Annually.class, Seasonally.class, Daily.class);
      fail();
    } catch (ConfigurationException expected) {
      assertContains(expected.getMessage(),
          "1) Illegal scoped dependency: " + Chicken.class.getName() + " in @Seasonally",
          "  depends on " + Egg.class.getName() + " in @Daily");
    }
  }

  public void testCheckUnboundScope() {
    Injector injector = Guice.createInjector();
    ScopeChecker scopeChecker = new ScopeChecker(injector);

    try {
      scopeChecker.check(Singleton.class, Daily.class);
      fail();
    } catch (IllegalArgumentException expected) {
      assertContains(expected.getMessage(),
          "No scope binding for " + Daily.class);
    }
  }

  static class Chicken {
    @Inject Egg source;
  }
  static class Egg {
    @Inject Chicken source;
  }

  private Scope newScope() {
    return new Scope() {
      public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
        return unscoped;
      }
    };
  }
}
