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

import com.google.inject.AbstractModule;
import com.google.inject.ContainerBuilder;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * A source provider which returns {@code ContainerBuilder}'s caller's {@code
 * StackTraceElement}.
 * 
 * @author crazybob@google.com (Bob Lee)
 */
public class DefaultSourceProvider implements SourceProvider {

  final Set<String> skippedClassNames = new HashSet<String>(Arrays.asList(
      ContainerBuilder.class.getName(),
      AbstractModule.class.getName(),
      DefaultSourceProvider.class.getName(),
      SourceConsumer.class.getName()
  ));

  /**
   * Instructs the provider to skip the given class in the stack trace when
   * determining the source. Use this to keep the container builder from
   * logging utility methods as the sources of bindings (i.e. it will skip to
   * the utility methods' callers instead).
   *
   * <p>Skipping only takes place after this method is called.
   */
  public void skip(Class<?> clazz) {
    skippedClassNames.add(clazz.getName());
  }

  public Object source() {
    // Search up the stack until we find a class outside of this one.
    for (final StackTraceElement element : new Throwable().getStackTrace()) {
      String className = element.getClassName();
      if (!skippedClassNames.contains(className)) {
        return element;
      }
    }
    throw new AssertionError();
  }
}
