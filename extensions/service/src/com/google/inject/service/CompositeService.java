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

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.internal.util.ImmutableList;
import com.google.inject.internal.util.Lists;
import com.google.inject.internal.util.Preconditions;
import com.google.inject.internal.util.Sets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * A service that composes other services together in a fixed order.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class CompositeService {
  private final Injector injector;

  private final Set<Key<? extends Service>> services = Sets.newLinkedHashSet();

  /**
   * Represents the state of this composite service. Will equal FAILED
   * even if only one component service fails to start or stop. In other
   * words, all component services must start successfully for this
   * service to be considered started and similarly for stopped.
   */
  private volatile Service.State compositeState;
  private boolean composed;

  @Inject
  CompositeService(Injector injector) {
    this.injector = injector;
  }

  public CompositeService add(Class<? extends Service> service) {
    return add(Key.get(service));
  }

  public CompositeService add(Key<? extends Service> service) {
    Preconditions.checkState(!composed,
        "Cannot reuse a CompositeService after it has been compose()d. Please create a new one.");
    // Verify that the binding exists. Throws an exception if not.
    injector.getBinding(service);

    services.add(service);
    return this;
  }

  public Service compose() {
    Preconditions.checkState(!composed,
        "Cannot reuse a CompositeService after it has been compose()d. Please create a new one.");
    composed = true;

    // Defensive copy.
    final List<Key<? extends Service>> services = ImmutableList.copyOf(this.services);

    return new Service() {
      public Future<State> start() {
        final List<Future<State>> tasks = Lists.newArrayList();
        for (Key<? extends Service> service : services) {
          tasks.add(injector.getInstance(service).start());
        }

        return futureGet(tasks, State.STARTED);
      }

      public Future<State> stop() {
        final List<Future<State>> tasks = Lists.newArrayList();
        for (Key<? extends Service> service : services) {
          tasks.add(injector.getInstance(service).stop());
        }

        return futureGet(tasks, State.STOPPED);
      }

      public State state() {
        return compositeState;
      }
    };
  }

  private FutureTask<Service.State> futureGet(final List<Future<Service.State>> tasks,
      final Service.State state) {
    return new FutureTask<Service.State>(new Callable<Service.State>() {
      public Service.State call() {
        boolean ok = true;
        for (Future<Service.State> task : tasks) {
          try {
            ok = state == task.get();
          } catch (InterruptedException e) {
            return compositeState = Service.State.FAILED;
          } catch (ExecutionException e) {
            return compositeState = Service.State.FAILED;
          }
        }

        return compositeState = ok ? state : Service.State.FAILED;
      }
    });
  }
}
