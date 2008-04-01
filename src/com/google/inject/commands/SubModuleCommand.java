/*
Copyright (C) 2008 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.google.inject.commands;

import com.google.inject.*;

import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of a sub module installation
 *
 * @author dan.halem@gmail.com (Dan Halem)
 */
public class SubModuleCommand implements Command {
  private final Object source;
  private final FutureInjector earlyRequestsProvider;
  private final List<Command> commands;

  public SubModuleCommand(Object source,
                          Stage stage,
                          Module module) {
    this.source = source;
    earlyRequestsProvider = new FutureInjector();
    CommandRecorder recorder
        = new CommandRecorder(earlyRequestsProvider);
    recorder.setCurrentStage(stage);
    commands = recorder.recordCommands(module);

  }

  public FutureInjector getEarlyRequestsProvider() {
    return earlyRequestsProvider;
  }

  public List<Command> getCommands() {
    return Collections.unmodifiableList(commands);
  }

  public Object getSource() {
    return source;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitSubModule(this);
  }

  public SubModuleBinder subModuleBinder(final Binder binder) {
    return new SubModuleBinder() {
      public <T> SubModuleBinder export(Key<T> key) {
        return exportKeyAs(key, key);
      }

      public <T> SubModuleBinder exportKeyAs(final Key<T> childKey, Key<T> parentKey) {
        binder.bind(parentKey).toProvider(new Provider<T>() {
          public T get() {
            return earlyRequestsProvider.get(childKey);
          }
        });
        return this;
      }

      public <T> SubModuleBinder export(Class<? extends T> clazz) {
        return export(Key.get(clazz));
      }
    };
  }
}
