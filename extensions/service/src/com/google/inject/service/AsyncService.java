/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.service;

import com.google.common.base.Preconditions;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * An asynchronous implementation of {@link com.google.inject.service.Service} that provides
 * convenience callbacks to create your own services.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public abstract class AsyncService implements Service {
  private static final Runnable DO_NOTHING = () -> {};

  private final ExecutorService executor;

  private volatile State state;

  public AsyncService(ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public final synchronized Future<State> start() {
    Preconditions.checkState(
        state != State.STOPPED, "Cannot restart a service that has been stopped");

    // Starts are idempotent.
    if (state == State.STARTED) {
      return new FutureTask<State>(DO_NOTHING, State.STARTED);
    }

    return executor.submit(
        () -> {
          onStart();
          return state = State.STARTED;
        });
  }

  /**
   * Called back when this service must do its start work. Typically occurs in a background thread.
   * The result of this method is returned to the original caller of {@link Service#start()} and can
   * thus be used to return a status message after start completes (or fails as the case may be).
   */
  protected abstract void onStart();

  @Override
  public final synchronized Future<State> stop() {
    Preconditions.checkState(state != null, "Must start this service before you stop it!");

    // Likewise, stops are idempotent.
    if (state == State.STOPPED) {
      return new FutureTask<State>(DO_NOTHING, State.STOPPED);
    }

    return executor.submit(
        () -> {
          onStop();
          return state = State.STOPPED;
        });
  }

  /**
   * Called back when this service must shutdown. Typically occurs in a background thread. The
   * result of this method is returned to the original caller of {@link Service#stop()} and can thus
   * be used to return a status message after stop completes (or fails as the case may be).
   */
  protected abstract void onStop();

  @Override
  public final State state() {
    return state;
  }
}
