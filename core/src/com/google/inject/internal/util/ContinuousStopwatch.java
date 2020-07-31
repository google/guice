/*
 * Copyright (C) 2020 Google Inc.
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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Stopwatch;
import java.util.logging.Logger;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * A continuously timing stopwatch that is used for simple performance monitoring.
 *
 * @author crazybob@google.com (Bob Lee)
 */
@NotThreadSafe
public final class ContinuousStopwatch {
  private final Logger logger = Logger.getLogger(ContinuousStopwatch.class.getName());
  private final Stopwatch stopwatch;

  /**
   * Constructs a ContinuousStopwatch, which will start timing immediately after construction.
   *
   * @param stopwatch the internal stopwatch used by ContinuousStopwatch
   */
  public ContinuousStopwatch(Stopwatch stopwatch) {
    this.stopwatch = stopwatch;
    reset();
  }

  /** Resets and returns elapsed time in milliseconds. */
  public long reset() {
    long elapsedTimeMs = stopwatch.elapsed(MILLISECONDS);
    stopwatch.reset();
    stopwatch.start();
    return elapsedTimeMs;
  }

  /** Resets and logs elapsed time in milliseconds. */
  public void resetAndLog(String label) {
    logger.fine(label + ": " + reset() + "ms");
  }
}
