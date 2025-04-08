/*
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

package com.google.inject.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.ConfigurationException;
import com.google.inject.Stage;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InjectionRequest;
import com.google.inject.spi.StaticInjectionRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Handles {@code Binder.requestInjection} and {@code Binder.requestStaticInjection} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author mikeward@google.com (Mike Ward)
 */
final class InjectionRequestProcessor extends AbstractProcessor {

  private final List<StaticInjection> staticInjections = Lists.newArrayList();
  private final Initializer initializer;

  InjectionRequestProcessor(Errors errors, Initializer initializer) {
    super(errors);
    this.initializer = initializer;
  }

  @Override
  public Boolean visit(StaticInjectionRequest request) {
    staticInjections.add(new StaticInjection(injector, request));
    injector.getBindingData().putStaticInjectionRequest(request);
    return true;
  }

  @Override
  public Boolean visit(InjectionRequest<?> request) {
    Set<InjectionPoint> injectionPoints;
    try {
      injectionPoints = request.getInjectionPoints();
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
      injectionPoints = e.getPartialValue();
    }

    requestInjection(request, injectionPoints, errors);

    // Drop the actual instance from the binding data we store for the SPI.
    // TODO(sameb): Why?
    injector
        .getBindingData()
        .putInjectionRequest(
            new InjectionRequest<>(request.getSource(), request.getType(), /* instance= */ null));
    return true;
  }

  // Note: This is extracted to a separate method just to help Java infer the generics correctly.
  private <T> void requestInjection(
      InjectionRequest<T> request, Set<InjectionPoint> injectionPoints, Errors errors) {
    // We don't need to keep the return value, because we're not _using_ the injected value
    // anyway... we're just injecting it.
    Optional<Initializable<T>> unused =
        initializer.requestInjection(
            injector,
            request.getType(),
            request.getInstance(),
            /* binding= */ null,
            request.getSource(),
            injectionPoints,
            errors);
  }

  void validate() {
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.validate();
    }
  }

  void injectMembers() {
    /*
     * TODO: If you request both a parent class and one of its
     * subclasses, the parent class's static members will be
     * injected twice.
     */
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.injectMembers();
    }
  }

  /** A requested static injection. */
  private class StaticInjection {
    final InjectorImpl injector;
    final Object source;
    final StaticInjectionRequest request;
    ImmutableList<SingleMemberInjector> memberInjectors;

    public StaticInjection(InjectorImpl injector, StaticInjectionRequest request) {
      this.injector = injector;
      this.source = request.getSource();
      this.request = request;
    }

    void validate() {
      Errors errorsForMember = errors.withSource(source);
      Set<InjectionPoint> injectionPoints;
      try {
        injectionPoints = request.getInjectionPoints();
      } catch (ConfigurationException e) {
        errorsForMember.merge(e.getErrorMessages());
        injectionPoints = e.getPartialValue();
      }
      if (injectionPoints != null) {
        memberInjectors =
            injector.membersInjectorStore.getInjectors(injectionPoints, errorsForMember);
      } else {
        memberInjectors = ImmutableList.of();
      }

      errors.merge(errorsForMember);
    }

    void injectMembers() {
      InternalContext context = injector.enterContext();
      try {
        boolean isStageTool = injector.options.stage == Stage.TOOL;
        for (SingleMemberInjector memberInjector : memberInjectors) {
          // Run injections if we're not in tool stage (ie, PRODUCTION or DEV),
          // or if we are in tool stage and the injection point is toolable.
          if (!isStageTool || memberInjector.getInjectionPoint().isToolable()) {
            if (InternalFlags.getUseExperimentalMethodHandlesOption()) {
              try {
                // In theory, constructing the handle to invoke it exactly once is expensive and wasteful, and it is true for
                // directly injecting the member this is probably slower than the reflective SingleFieldInjector. However,
                // by taking this path we::
                // 1. Don't need to construct fastclasses for method injections (SingleMethodInjector).
                // 2. construct fast classes for transitive injections (constructors/@Provides methods).
                // 3. Can leverage or initialize caches for transitive InternalFactory.getHandle calls.
                memberInjector.getInjectHandle(new LinkageContext()).invokeExact((Object) null, context);
              } catch (InternalProvisionException e) {
                errors.merge(e);
              } catch (Throwable t) {
                // This will propagate unexpected Errors.
                InternalMethodHandles.sneakyThrow(t);
              }
            } else {
              try {
                memberInjector.inject(context, null);
              } catch (InternalProvisionException e) {
                errors.merge(e);
              }
            }
          }
        }
      } finally {
        context.close();
      }
    }
  }
}
