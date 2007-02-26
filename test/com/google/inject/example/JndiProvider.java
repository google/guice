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

package com.google.inject.example;

import com.google.inject.Inject;
import com.google.inject.Provider;
import javax.naming.Context;
import javax.naming.NamingException;

class JndiProvider<T> implements Provider<T> {

  @Inject Context context;
  final String name;
  final Class<T> type;

  JndiProvider(Class<T> type, String name) {
    this.name = name;
    this.type = type;
  }

  public T get() {
    try {
      return type.cast(context.lookup(name));
    }
    catch (NamingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a JNDI provider for the given
   * type and name.
   */
  static <T> Provider<T> fromJndi(
      Class<T> type, String name) {
    return new JndiProvider<T>(type, name);
  }
}
