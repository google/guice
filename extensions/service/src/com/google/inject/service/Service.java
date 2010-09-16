/**
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * An object with an operational state, asynchronous {@link #start()} and
 * {@link #stop()} lifecycle methods to transition in and out of this state.
 * Example services include http servers, RPC systems and timer tasks.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public interface Service {
  /**
   * If the service has already been started, this method returns
   * immediately without taking action. A stopped service may not be restarted.
   *
   * @return a future for the startup result, regardless of whether this call
   *     initiated startup. Calling {@link Future#get} will block until the
   *     service has finished starting, and returns the resultant state. If
   *     the service fails to start, {@link Future#get} will throw an {@link
   *     ExecutionException}. If it has already finished starting,
   *     {@link Future#get} returns immediately.
   */
  Future<State> start();

  /**
   * If the service is {@link State#STARTED} initiates service shutdown and
   * returns immediately. If the service has already been stopped, this
   * method returns immediately without taking action.
   *
   * @return a future for the shutdown result, regardless of whether this call
   *     initiated shutdown. Calling {@link Future#get} will block until the
   *     service has finished shutting down, and either returns {@link
   *     State#STOPPED} or throws an {@link ExecutionException}. If it has
   *     already finished stopping, {@link Future#get} returns immediately.
   */
  Future<State> stop();

  /**
   * Returns the current state of this service. One of {@link State} possible
   * values, or null if this is a brand new object, i.e., has not been put into
   * any state yet.
   */
  State state();

  /**
   * The lifecycle states of a service.
   */
  enum State { STARTED, STOPPED, FAILED }
}
