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

package com.google.inject.spi;

import com.google.common.collect.ImmutableSet;
import java.util.Set;

/**
 * Provides access to the calling line of code.
 * 
 * @author crazybob@google.com (Bob Lee)
 */
public class SourceProvider {

  /** Indicates that the source is unknown. */
  public static final Object UNKNOWN_SOURCE = "[unknown source]";

  private final Set<String> classNamesToSkip;

  public SourceProvider(Class... classesToSkip) {
    String[] classNamesToSkip = new String[classesToSkip.length + 1];
    for (int i = 0; i < classesToSkip.length; i++) {
      classNamesToSkip[i] = classesToSkip[i].getName();
    }
    classNamesToSkip[classesToSkip.length] = SourceProvider.class.getName();
    this.classNamesToSkip = ImmutableSet.of(classNamesToSkip);
  }

  public Object get() {
    for (final StackTraceElement element : new Throwable().getStackTrace()) {
      String className = element.getClassName();
      if (!classNamesToSkip.contains(className)) {
        return element;
      }
    }
    throw new AssertionError();
  }
}
