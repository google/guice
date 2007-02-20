/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.name;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.spi.SourceProviders;
import com.google.inject.spi.SourceProvider;
import java.util.Map;
import java.util.Properties;

/**
 * Utility methods for use with {@code @}{@link Named}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Names {

  private Names() {}

  static {
    SourceProviders.skip(Names.class);
  }

  /**
   * Creates a {@link Named} annotation with {@code name} as the value.
   */
  public static Named named(String name) {
    return new NamedImpl(name);
  }

  /**
   * Creates a constant binding to {@code @Named(key)} for each property.
   */
  public static void bindProperties(final Binder binder,
      final Map<String, String> properties) {
    SourceProviders.withDefault(
        new SimpleSourceProvider(SourceProviders.defaultSource()),
        new Runnable() {
          public void run() {
            for (Map.Entry<String, String> entry : properties.entrySet()) {
              String key = entry.getKey();
              String value = entry.getValue();
              binder.bind(Key.get(String.class, new NamedImpl(key))).toInstance(value);
            }
          }
        });
  }

  /**
   * Creates a constant binding to {@code @Named(key)} for each property.
   */
  public static void bindProperties(final Binder binder,
      final Properties properties) {
    SourceProviders.withDefault(
        new SimpleSourceProvider(SourceProviders.defaultSource()),
        new Runnable() {
          public void run() {
            for (Map.Entry<Object, Object> entry : properties.entrySet()) {
              String key = (String) entry.getKey();
              String value = (String) entry.getValue();
              binder.bind(Key.get(String.class, new NamedImpl(key))).toInstance(value);
            }
          }
        });
  }

  static class SimpleSourceProvider implements SourceProvider {

    final Object source;

    SimpleSourceProvider(Object source) {
      this.source = source;
    }

    public Object source() {
      return source;
    }
  }
}
