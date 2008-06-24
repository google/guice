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

import com.google.common.collect.Lists;
import com.google.inject.InjectorImpl.SingleMemberInjector;
import com.google.inject.commands.RequestInjectionCommand;
import com.google.inject.commands.RequestStaticInjectionCommand;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import java.util.List;
import java.util.Set;

/**
 * Handles {@link Binder#requestInjection} and {@link Binder#requestStaticInjection} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author mikeward@google.com (Mike Ward)
 */
class RequestInjectionCommandProcessor extends CommandProcessor {

  private final List<StaticInjection> staticInjections = Lists.newArrayList();
  private final Set<Object> outstandingInjections;

  RequestInjectionCommandProcessor(Errors errors,
      Set<Object> outstandingInjections) {
    super(errors);
    this.outstandingInjections = outstandingInjections;
  }

  @Override public Boolean visitRequestStaticInjection(RequestStaticInjectionCommand command) {
    for (Class<?> type : command.getTypes()) {
      staticInjections.add(new StaticInjection(command.getSource(), type));
    }
    return true;
  }

  @Override public Boolean visitRequestInjection(RequestInjectionCommand command) {
    for (Object instance : command.getInstances()) {
      outstandingInjections.add(instance);
    }
    return true;
  }

  public void validate(InjectorImpl injector) {
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.validate(injector);
    }
  }

  public void injectMembers(InjectorImpl injector) {
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.injectMembers(injector);
    }
  }

  /** A requested static injection. */
  private class StaticInjection {
    final Object source;
    final Class<?> type;
    final List<SingleMemberInjector> memberInjectors = Lists.newArrayList();

    public StaticInjection(Object source, Class type) {
      this.source = source;
      this.type = type;
    }

    void validate(final InjectorImpl injector) {
      Errors errorsForMember = errors.withSource(source);
      injector.addSingleInjectorsForFields(
          type.getDeclaredFields(), true, memberInjectors, errorsForMember);
      injector.addSingleInjectorsForMethods(
          type.getDeclaredMethods(), true, memberInjectors, errorsForMember);
    }

    void injectMembers(InjectorImpl injector) {
      try {
        injector.callInContext(new ContextualCallable<Void>() {
          public Void call(InternalContext context) {
            for (SingleMemberInjector injector : memberInjectors) {
              injector.inject(errors, context, null);
            }
            return null;
          }
        });
      } catch (ErrorsException e) {
        throw new AssertionError();
      }
    }
  }
}
