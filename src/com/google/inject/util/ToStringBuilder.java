// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.util;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Helps with {@code toString()} methods.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class ToStringBuilder {

  // Linked hash map ensures ordering.
  final Map<String, Object> map = new LinkedHashMap<String, Object>();

  final String name;

  public ToStringBuilder(String name) {
    this.name = name;
  }

  public ToStringBuilder(Class type) {
    this.name = type.getSimpleName();
  }

  public ToStringBuilder add(String name, Object value) {
    if (map.put(name, value) != null) {
      throw new RuntimeException("Duplicate names: " + name);
    }
    return this;
  }

  public String toString() {
    return name + map.toString();
  }
}
