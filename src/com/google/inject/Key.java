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

import static com.google.inject.util.Objects.nonNull;

/**
 * Binding key. Uniquely identified by the type and name.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Key<T> {

  final Class<T> type;
  final String name;
  final int hashCode;

  private Key(Class<T> type, String name) {
    this.type = nonNull(type, "type");
    this.name = nonNull(name, "name");

    hashCode = type.hashCode() * 31 + name.hashCode();
  }

  Class<T> getType() {
    return type;
  }

  String getName() {
    return name;
  }

  public int hashCode() {
    return hashCode;
  }

  public boolean equals(Object o) {
    if (!(o instanceof Key)) {
      return false;
    }
    if (o == this) {
      return true;
    }
    Key other = (Key) o;
    return name.equals(other.name) && type.equals(other.type);
  }

  public String toString() {
    return "[type=" + type.getName() + ", name='" + name + "']";
  }

  public static <T> Key<T> newInstance(Class<T> type, String name) {
    return new Key<T>(type, name);
  }
}
