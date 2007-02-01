// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import java.util.logging.Logger;

/**
 * @author crazybob@google.com (Bob Lee)
 */
class Stopwatch {

  long start = System.currentTimeMillis();

  /**
   * Resets and returns ellapsed time.
   */
  long reset() {
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
  void resetAndLog(Logger logger, String label) {
    logger.info(label + ": " + reset() + "ms");
  }
}
