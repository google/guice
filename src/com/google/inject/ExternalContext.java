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

package com.google.inject;

import java.lang.reflect.Member;
import java.util.LinkedHashMap;

/**
 * An immutable snapshot of the current context which is safe to expose to
 * client code.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ExternalContext<T> implements Context {

  final Member member;
  final Key<T> key;
  final InjectorImpl injector;
  final int parameterIndex;

  public ExternalContext(Member member, int paramterIndex, Key<T> key,
      InjectorImpl injector) {
    this.member = member;
    this.key = key;
    this.injector = injector;
    this.parameterIndex = paramterIndex;
  }

  public Key<?> getKey() {
    return this.key;
  }

  public Injector getInjector() {
    return injector;
  }

  public Member getMember() {
    return member;
  }

  public int getParameterIndex() {
    return parameterIndex;
  }

  public String toString() {
    return "Context" + new LinkedHashMap<String, Object>() {{
      put("member", member);
      put("key", getKey());
      put("injector", injector);
    }}.toString();
  }

  static <T> ExternalContext<T> newInstance(
      Member member, Key<T> key, InjectorImpl injector) {
    return new ExternalContext<T>(member, -1, key, injector);
  }

  static <T> ExternalContext<T> newInstance(Member member, int parameterIndex,
      Key<T> key, InjectorImpl injector) {
    return new ExternalContext<T>(member, parameterIndex, key, injector);
  }
}
