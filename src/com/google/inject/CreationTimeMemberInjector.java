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

package com.google.inject;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Maps;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

/**
 * Injects all instances that were registered for injection at injector-creation time.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class CreationTimeMemberInjector {
  /** the only thread that we'll use to inject members. */
  private final Thread creatingThread = Thread.currentThread();

  /** zero means everything is injected. */
  private final CountDownLatch ready = new CountDownLatch(1);

  /** Maps instances that need injection to a source that registered them */
  private final Map<Object, Object> outstandingInjections = Maps.newIdentityHashMap();
  private final InjectorImpl injector;

  CreationTimeMemberInjector(InjectorImpl injector) {
    this.injector = injector;
  }

  /**
   * Registers an instance for member injection when that step is performed.
   *
   * @param instance an instance that optionally has members to be injected (each annotated with
   *      @Inject).
   * @param source the source location that this injection was requested
   */
  public void requestInjection(Object instance, Object source) {
    checkNotNull(source);
    outstandingInjections.put(instance, source);
  }

  /**
   * Prepares member injectors for all injected instances. This prompts Guice to do static analysis
   * on the injected instances.
   */
  void validateOustandingInjections(Errors errors) {
    for (Map.Entry<Object, Object> entry : outstandingInjections.entrySet()) {
      try {
        Object toInject = entry.getKey();
        Object source = entry.getValue();
        injector.getMemberInjectors(toInject.getClass(), errors.withSource(source));
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }
  }

  /**
   * Performs creation-time injections on all objects that require it. Whenever fulfilling an
   * injection depends on another object that requires injection, we use {@link
   * #ensureInjected(Object,com.google.inject.internal.Errors)} to inject that member first.
   *
   * <p>If the two objects are codependent (directly or transitively), ordering of injection is
   * arbitrary.
   */
  void injectAll(final Errors errors) {
    // loop over a defensive copy since ensureInjected() mutates the set. Unfortunately, that copy
    // is made complicated by a bug in IBM's JDK, wherein entrySet().toArray(Object[]) doesn't work
    for (Object entryObject : outstandingInjections.entrySet().toArray()) {
      @SuppressWarnings("unchecked")
      Entry<Object, Object> entry = (Entry<Object, Object>) entryObject;
      try {
        Object toInject = entry.getKey();
        Object source = entry.getValue();
        ensureInjected(toInject, errors.withSource(source));
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    if (!outstandingInjections.isEmpty()) {
      throw new AssertionError("Failed to satisfy " + outstandingInjections);
    }

    ready.countDown();
  }

  /**
   * Reentrant. If {@code toInject} was registered for injection at injector-creation time, this
   * method will ensure that all its members have been injected before returning. This method is
   * used both internally, and by {@code InternalContext} to satisfy injections while satisfying
   * other injections.
   */
  void ensureInjected(Object toInject, Errors errors) throws ErrorsException {
    if (ready.getCount() == 0) {
      return;
    }

    // just wait for everything to be injected by another thread
    if (Thread.currentThread() != creatingThread) {
      try {
        ready.await();
        return;
      } catch (InterruptedException e) {
        // Give up, since we don't know if our injection is ready
        throw new RuntimeException(e);
      }
    }

    // toInject needs injection, do it right away
    if (outstandingInjections.remove(toInject) != null) {
      injector.injectMembersOrThrow(errors, toInject);
    }
  }
}