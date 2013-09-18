package com.google.inject.internal;

import com.google.inject.Key;

/**
 * Interface for types that can rehash their {@link Key} instances.
 *
 * @author chrispurcell@google.com (Chris Purcell)
 */
public interface RehashableKeys {
  void rehashKeys();

  /** Utility methods for key rehashing. */
  public static class Keys {

    /**
     * Returns true if the cached hashcode for the given key is out-of-date. This will only occur
     * if the key contains a mutable annotation.
     */
    public static boolean needsRehashing(Key<?> key) {
      if (!key.hasAttributes()) {
        return false;
      }
      int newHashCode = key.getTypeLiteral().hashCode() * 31 + key.getAnnotation().hashCode();
      return (key.hashCode() != newHashCode);
    }

    /** Returns a copy of the given key with an up-to-date hashcode. */
    public static <T> Key<T> rehash(Key<T> key) {
      if (key.hasAttributes()) {
        // This will recompute the hashcode for us.
        return Key.get(key.getTypeLiteral(), key.getAnnotation());
      } else {
        return key;
      }
    }

    private Keys() { }
  }
}
