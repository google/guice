/*
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.throwingproviders;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.ScopeAnnotation;
import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple scope that can be explicitly reset.
 *
 * @author jmourits@google.com (Jerome Mourits)
 */
class TestScope implements Scope {

  @Retention(RUNTIME)
  @ScopeAnnotation
  public @interface Scoped {}

  private Map<Key<?>, Object> inScopeObjectsMap = new HashMap<>();

  @Override
  public <T> Provider<T> scope(final Key<T> key, final Provider<T> provider) {
    return new Provider<T>() {
      @Override
      @SuppressWarnings({"unchecked"})
      public T get() {
        T t = (T) inScopeObjectsMap.get(key);
        if (t == null) {
          t = provider.get();
          inScopeObjectsMap.put(key, t);
        }
        return t;
      }
    };
  }

  public void beginNewScope() {
    inScopeObjectsMap = new HashMap<>();
  }
}
