// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.inject.internal;

/**
 * Something that needs some delayed initialization, typically
 * a binding or internal factory that needs to be created & put
 * into the bindings map & then initialized later.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
interface DelayedInitialize {
  
  /** Initializes this binding, throwing any errors if necessary. */
  void initialize(InjectorImpl injector, Errors errors) throws ErrorsException;

}
