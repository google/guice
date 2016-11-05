package com.google.inject.servlet;

import java.io.Closeable;

/** Object that can be used to apply a request scope to a block of code. */
public interface RequestScoper {
  /**
   * Opens up the request scope until the returned object is closed. Implementations should ensure
   * (e.g. by blocking) that multiple threads cannot open the same request scope concurrently. It is
   * allowable to open the same request scope on the same thread, as long as open/close calls are
   * correctly nested.
   */
  CloseableScope open();

  /** Closeable subclass that does not throw any exceptions from close. */
  public interface CloseableScope extends Closeable {
    @Override
    void close();
  }
}
