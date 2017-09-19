package com.google.inject;

/**
 * Implementation of a module that will only ever be installed once into
 * the {@link Injector}
 */
public abstract class SingletonModule extends AbstractModule {

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }

    if (!(obj instanceof AbstractModule)) {
      return false;
    }

    return getClass().equals(obj.getClass());
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

}
