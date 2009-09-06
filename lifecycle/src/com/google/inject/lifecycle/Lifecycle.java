package com.google.inject.lifecycle;

import com.google.inject.ImplementedBy;
import com.google.inject.matcher.Matcher;
import java.util.concurrent.ExecutorService;

/**
 *  @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
@ImplementedBy(BroadcastingLifecycle.class)
public interface Lifecycle {

  void start();

  <T> T broadcast(Class<T> clazz);

  <T> T broadcast(Class<T> clazz, Matcher<? super T> matcher);

  <T> T broadcast(Class<T> clazz, ExecutorService executorService);

  <T> T broadcast(Class<T> clazz, ExecutorService executorService, Matcher<? super T> matcher);
}
