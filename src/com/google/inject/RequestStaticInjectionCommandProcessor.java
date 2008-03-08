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

import com.google.inject.commands.RequestStaticInjectionCommand;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@link Binder#requestStaticInjection} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class RequestStaticInjectionCommandProcessor extends CommandProcessor {

  private final List<StaticInjection> staticInjections
      = new ArrayList<StaticInjection>();

  @Override public Boolean visitRequestStaticInjection(RequestStaticInjectionCommand command) {
    for (Class<?> type : command.getTypes()) {
      staticInjections.add(new StaticInjection(command.getSource(), type));
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

  /**
   * A requested static injection.
   */
  private class StaticInjection {
    final Object source;
    final Class<?> type;
    final List<InjectorImpl.SingleMemberInjector> memberInjectors
        = new ArrayList<InjectorImpl.SingleMemberInjector>();

    public StaticInjection(Object source, Class type) {
      this.source = source;
      this.type = type;
    }

    void validate(final InjectorImpl injector) {
      injector.withDefaultSource(source,
          new Runnable() {
            public void run() {
              injector.addSingleInjectorsForFields(
                  type.getDeclaredFields(), true, memberInjectors);
              injector.addSingleInjectorsForMethods(
                  type.getDeclaredMethods(), true, memberInjectors);
            }
          });
    }

    void injectMembers(InjectorImpl injector) {
      injector.callInContext(new ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (InjectorImpl.SingleMemberInjector injector : memberInjectors) {
            injector.inject(context, null);
          }
          return null;
        }
      });
    }
  }
}