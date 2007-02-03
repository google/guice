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

/**
 * Support for classes which use source objects.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class SourceConsumer {

  SourceProvider sourceProvider = new DefaultSourceProvider();

  /**
   * Returns the current source.
   */
  protected Object source() {
    return sourceProvider.source();
  }

  /**
   * Gets the current source provider.
   */
  public SourceProvider getSourceProvider() {
    return sourceProvider;
  }

  /**
   * Sets the current source provider.
   */
  public void setSourceProvider(SourceProvider sourceProvider) {
    this.sourceProvider = sourceProvider;
  }

  /**
   * Sets the source provider, runs the given command, and restores the
   * previous source provider.
   */
  public void withSourceProvider(SourceProvider sourceProvider, Runnable r) {
    SourceProvider previous = this.sourceProvider;
    try {
      this.sourceProvider = sourceProvider;
      r.run();
    } finally {
      this.sourceProvider = previous;
    }
  }
}
