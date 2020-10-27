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
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.InjectionRequest;
import com.google.inject.spi.StaticInjectionRequest;
import java.util.List;
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

    initializer.requestInjection(
        injector, request.getInstance(), null, request.getSource(), injectionPoints);
    // When recreating the injection request, we revise the TypeLiteral to be the type
    // of the instance.  This is because currently Guice ignores the user's TypeLiteral
    // when determining the types for members injection.
    // If/when this is fixed, we can report the exact type back to the user.
    // (Otherwise the injection points exposed from the request may be wrong.)
    injector
        .getBindingData()
        .putInjectionRequest(
            new InjectionRequest<>(
                request.getSource(),
                TypeLiteral.get(request.getInstance().getClass()),
                /* instance= */ null));
    return true;
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
            try {
              memberInjector.inject(context, null);
            } catch (InternalProvisionException e) {
              errors.merge(e);
            }
          }
        }
      } finally {
        context.close();
      }
    }
  }
}
