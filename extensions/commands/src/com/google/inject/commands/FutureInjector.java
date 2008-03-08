/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.commands;

import com.google.inject.Injector;
import com.google.inject.Key;


/**
 * Satisfies binding requests using an eventually-created Injector. To use:
 *
 * <ol><li>Record commands using a {@link CommandRecorder}.</li>
 * <li>Create an injector by replaying those commands (and possibly rewriting
 * them) using {@link CommandReplayer}.</li>
 * <li>Initialize the injector in the {@code FutureInjector}.</li></ol>
 *
 * <pre>
 * FutureInjector futureInjector = new FutureInjector();
 * List&lt;Command&gt; commands = new CommandRecorder(futureInjector).recordCommands(modules);
 * Module module = new CommandRewriter().createModule(commands);
 * Injector injector = Guice.createInjector(module);
 * futureInjector.initialize(injector);
 * </pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class FutureInjector implements EarlyRequestsProvider {
  /** manually initialized later */
  private Injector injector;

  public void initialize(Injector injector) {
    if (this.injector != null) {
      throw new IllegalStateException("Already initialized");
    }

    this.injector = injector;
  }

  public <T> T get(Key<T> key) {
    if (injector == null) {
      throw new IllegalStateException("This provider cannot be used until the"
          + " Injector has been created.");
    }

    return injector.getInstance(key);
  }
}
