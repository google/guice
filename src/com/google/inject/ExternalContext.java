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
 * An immutable snapshot of the current context which is safe to
 * expose to client code.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ExternalContext<T> implements Context {

  final Member member;
  final Key<T> key;
  final ContainerImpl container;

  public ExternalContext(Member member, Key<T> key, ContainerImpl container) {
    this.member = member;
    this.key = key;
    this.container = container;
  }

  public Key<?> getKey() {
    return this.key;
  }

  public Container getContainer() {
    return container;
  }

  public Member getMember() {
    return member;
  }

  public String toString() {
    return "Context" + new LinkedHashMap<String, Object>() {{
      put("member", member);
      put("key", getKey());
      put("container", container);
    }}.toString();
  }

  static <T> ExternalContext<T> newInstance(Member member, Key<T> key,
      ContainerImpl container) {
    return new ExternalContext<T>(member, key, container);
  }
}
