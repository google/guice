// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

/**
 * @author crazybob@google.com (Bob Lee)
*/
interface ContextualCallable<T> {
  T call(InternalContext context);
}
