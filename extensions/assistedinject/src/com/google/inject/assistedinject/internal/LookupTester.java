package com.google.inject.assistedinject.internal;

import com.google.errorprone.annotations.Keep;
import java.lang.invoke.MethodHandles;

/**
 * An interface in a different package, so that AssistedInject's main package can't see it. Used at
 * runtime to determine which kind of Lookup method we'll support.
 */
@Keep
class LookupTester {
  @Keep static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  @Keep
  interface Hidden {
    default Hidden method() {
      return null;
    }
  }

  private LookupTester() {}
}
