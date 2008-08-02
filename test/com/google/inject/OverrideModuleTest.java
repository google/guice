/**
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

package com.google.inject;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.Guice.createInjector;
import static com.google.inject.name.Names.named;
import com.google.inject.util.Modules;
import java.util.Date;
import junit.framework.TestCase;

/**
 * @author sberlin@gmail.com (Sam Berlin)
 */
public class OverrideModuleTest extends TestCase {

  private static final Key<String> key2 = Key.get(String.class, named("2"));
  private static final Key<String> key3 = Key.get(String.class, named("3"));

  private static final Module EMPTY_MODULE = new Module() {
    public void configure(Binder binder) {}
  };

  public void testOverride() {
    Injector injector = createInjector(Modules.override(newModule("A")).with(newModule("B")));
    assertEquals("B", injector.getInstance(String.class));
  }

  public void testOverrideMultiple() {
    Module module = Modules.override(newModule("A"), newModule(1), newModule(0.5f))
        .with(newModule("B"), newModule(2), newModule(1.5d));
    Injector injector = createInjector(module);
    assertEquals("B", injector.getInstance(String.class));
    assertEquals(2, injector.getInstance(Integer.class).intValue());
    assertEquals(0.5f, injector.getInstance(Float.class));
    assertEquals(1.5d, injector.getInstance(Double.class));
  }

  public void testOverrideUnmatchedTolerated() {
    Injector injector = createInjector(Modules.override(EMPTY_MODULE).with(newModule("B")));
    assertEquals("B", injector.getInstance(String.class));
  }

  public void testOverrideConstant() {
    Module original = new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(named("Test")).to("A");
      }
    };

    Module replacements = new AbstractModule() {
      protected void configure() {
        bindConstant().annotatedWith(named("Test")).to("B");
      }
    };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(Key.get(String.class, named("Test"))));
  }

  public void testGetProviderInModule() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(key2).toProvider(getProvider(String.class));
      }
    };

    Injector injector = createInjector(Modules.override(original).with(EMPTY_MODULE));
    assertEquals("A", injector.getInstance(String.class));
    assertEquals("A", injector.getInstance(key2));
  }

  public void testOverrideWhatGetProviderProvided() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(key2).toProvider(getProvider(String.class));
      }
    };

    Module replacements = newModule("B");

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(String.class));
    assertEquals("B", injector.getInstance(key2));
  }

  public void testOverrideUsingOriginalsGetProvider() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(key2).toInstance("B");
      }
    };

    Module replacements = new AbstractModule() {
      protected void configure() {
        bind(String.class).toProvider(getProvider(key2));
      }
    };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals("B", injector.getInstance(String.class));
    assertEquals("B", injector.getInstance(key2));
  }

  public void testOverrideOfOverride() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A1");
        bind(key2).toInstance("A2");
        bind(key3).toInstance("A3");
      }
    };

    Module replacements1 = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("B1");
        bind(key2).toInstance("B2");
      }
    };

    Module overrides = Modules.override(original).with(replacements1);

    Module replacements2 = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("C1");
        bind(key3).toInstance("C3");
      }
    };

    Injector injector = createInjector(Modules.override(overrides).with(replacements2));
    assertEquals("C1", injector.getInstance(String.class));
    assertEquals("B2", injector.getInstance(key2));
    assertEquals("C3", injector.getInstance(key3));
  }

  public void testOverridesTwiceFails() {
    Module original = newModule("A");

    Module replacements = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("B");
        bind(String.class).toInstance("C");
      }
    };

    Module module = Modules.override(original).with(replacements);
    try {
      createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Error at " + replacements.getClass().getName());
      assertContains(expected.getMessage(), "A binding to java.lang.String "
          + "was already configured at " + replacements.getClass().getName());
    }
  }

  public void testOverridesDoesntFixTwiceBoundInOriginal() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("A");
        bind(String.class).toInstance("B");
      }
    };

    Module replacements = new AbstractModule() {
      protected void configure() {
        bind(String.class).toInstance("C");
      }
    };

    Module module = Modules.override(original).with(replacements);
    try {
      createInjector(module);
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(), "Error at " + original.getClass().getName());
      assertContains(expected.getMessage(), "A binding to java.lang.String "
          + "was already configured at " + replacements.getClass().getName());
    }
  }

  public void testOverrideUntargettedBinding() {
    Module original = new AbstractModule() {
      protected void configure() {
        bind(Date.class);
      }
    };

    Module replacements = new AbstractModule() {
      protected void configure() {
        bind(Date.class).toInstance(new Date(0));
      }
    };

    Injector injector = createInjector(Modules.override(original).with(replacements));
    assertEquals(0, injector.getInstance(Date.class).getTime());
  }

  private static <T> Module newModule(final T bound) {
    return new AbstractModule() {
      protected void configure() {
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) bound.getClass();
        bind(type).toInstance(bound);
      }
    };
  }
}
