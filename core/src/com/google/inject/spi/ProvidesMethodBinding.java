package com.google.inject.spi;

import com.google.inject.Key;
import com.google.inject.Provides;

import java.lang.reflect.Method;

/**
 * An {@literal @}{@link Provides} binding.
 * 
 * @since 4.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ProvidesMethodBinding<T> extends HasDependencies {
  
  /** Returns the method this binding uses. */
  Method getMethod(); 
  
  /** Returns the instance of the object the method is defined in. */
  Object getEnclosingInstance();
  
  /** Returns the key of the binding. */
  Key<T> getKey();
}
