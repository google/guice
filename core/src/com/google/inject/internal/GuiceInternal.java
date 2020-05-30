package com.google.inject.internal;

/**
 * Class used for restricting APIs in other packages to only be used by this package.
 *
 * <p>Other packages can reference this class but only this package can reference an instance of it,
 * so adding this class as a method param ensures that only this package can call it (provided null
 * is disallowed).
 */
public final class GuiceInternal {
  static final GuiceInternal GUICE_INTERNAL = new GuiceInternal();

  private GuiceInternal() {}
}
