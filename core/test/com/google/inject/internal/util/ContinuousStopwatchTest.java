package com.google.inject.internal.util;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.base.Stopwatch;
import com.google.common.testing.FakeTicker;
import com.google.common.testing.TestLogHandler;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ContinuousStopwatchTest {
  private static final Logger logger = Logger.getLogger(ContinuousStopwatch.class.getName());
  // Records ContinuousStopwatch logs.
  private TestLogHandler testLogHandler;
  // Used to reset the log level after this test is over.
  private Level savedLogLevel;

  @Before
  public void addLogHandler() {
    savedLogLevel = logger.getLevel();
    logger.setLevel(Level.FINE);
    testLogHandler = new TestLogHandler();
    logger.addHandler(testLogHandler);
  }

  @After
  public void removeLogHandler() {
    Logger.getLogger(ContinuousStopwatch.class.getName()).removeHandler(testLogHandler);
    logger.setLevel(savedLogLevel);
  }

  @Test
  public void multipleReset() throws Exception {
    FakeTicker fakeTicker = new FakeTicker();
    ContinuousStopwatch continuousStopwatch =
        new ContinuousStopwatch(Stopwatch.createUnstarted(fakeTicker));

    fakeTicker.advance(1, MILLISECONDS);
    assertThat(continuousStopwatch.reset()).isEqualTo(1);
    fakeTicker.advance(2, MILLISECONDS);
    assertThat(continuousStopwatch.reset()).isEqualTo(2);
  }

  @Test
  public void multipleResetAndLog() throws Exception {
    FakeTicker fakeTicker = new FakeTicker();
    ContinuousStopwatch continuousStopwatch =
        new ContinuousStopwatch(Stopwatch.createUnstarted(fakeTicker));

    fakeTicker.advance(1, MILLISECONDS);
    continuousStopwatch.resetAndLog("label one");
    fakeTicker.advance(2, MILLISECONDS);
    continuousStopwatch.resetAndLog("label two");
    List<LogRecord> logs = testLogHandler.getStoredLogRecords();
    assertThat(logs).hasSize(2);
    assertThat(logs.get(0).getMessage()).isEqualTo("label one: 1ms");
    assertThat(logs.get(1).getMessage()).isEqualTo("label two: 2ms");
  }
}
