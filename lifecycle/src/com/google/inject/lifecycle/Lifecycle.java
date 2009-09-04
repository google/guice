package com.google.inject.lifecycle;

import com.google.inject.ImplementedBy;
import com.google.inject.matcher.Matcher;

/**
 *  @author dhanji@google.com (Dhanji R. Prasanna)
 */
@ImplementedBy(BroadcastingLifecycle.class)
public interface Lifecycle {

  void start();

  <T> T broadcast(Class<T> clazz);

  <T> T broadcast(Class<T> clazz, Matcher<? super T> matcher);
}
