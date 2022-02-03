package com.google.inject.assistedinject.internal;

import java.lang.invoke.MethodHandles;

/**
 * An interface in a different package, so that AssistedInject's main package can't see it. Used at
 * runtime to determine which kind of Lookup method we'll support.
 */
class LookupTester {
  static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  interface Hidden {
    default Hidden method() {
      return null;
    }
  }

  private LookupTester() {}
}
