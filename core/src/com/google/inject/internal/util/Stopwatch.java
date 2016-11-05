/*
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

package com.google.inject.internal.util;

import java.util.logging.Logger;

/**
 * Enables simple performance monitoring.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public final class Stopwatch {
  private static final Logger logger = Logger.getLogger(Stopwatch.class.getName());

  private long start = System.currentTimeMillis();

  /** Resets and returns elapsed time in milliseconds. */
  public long reset() {
    long now = System.currentTimeMillis();
    try {
      return now - start;
    } finally {
      start = now;
    }
  }

  /** Resets and logs elapsed time in milliseconds. */
  public void resetAndLog(String label) {
    logger.fine(label + ": " + reset() + "ms");
  }
}
