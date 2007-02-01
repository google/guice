// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.util;

import java.util.logging.Logger;

/**
 * Enables simple performance monitoring.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Stopwatch {

  long start = System.currentTimeMillis();

  /**
   * Resets and returns ellapsed time.
   */
  public long reset() {
    long now = System.currentTimeMillis();
    try {
      return now - start;
    } finally {
      start = now;
    }
  }

  /**
   * Resets and logs ellapsed time.
   */
  public void resetAndLog(Logger logger, String label) {
    logger.info(label + ": " + reset() + "ms");
  }
}
