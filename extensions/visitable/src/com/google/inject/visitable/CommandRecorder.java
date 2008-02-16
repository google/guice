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

package com.google.inject.visitable;

import com.google.inject.*;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.TypeConverter;
import org.aopalliance.intercept.MethodInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Records commands executed by a module so they can be inspected or
 * {@link CommandReplayer replayed}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class CommandRecorder {
  private final Stage stage = Stage.DEVELOPMENT;
  private final EarlyRequestsProvider earlyRequestsProvider;
  private final List<Command> writableCommands = new ArrayList<Command>();
  private final List<Command> commands = Collections.unmodifiableList(writableCommands);
  private final Binder binder = new RecordingBinder();

  public CommandRecorder(EarlyRequestsProvider earlyRequestsProvider) {
    this.earlyRequestsProvider = earlyRequestsProvider;
  }

  /**
   * Returns a mutable list of all commands recorded thus far.
   */
  public List<Command> getCommands() {
    return commands;
  }

  /**
   * Returns the binder used to record commands.
   */
  public Binder getBinder() {
    return binder;
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public void recordCommands(Module... modules) {
    recordCommands(Arrays.asList(modules));
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public void recordCommands(Iterable<Module> modules) {
    for (Module module : modules) {
      module.configure(binder);
    }
  }

  private class RecordingBinder implements Binder {
    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      writableCommands.add(new BindInterceptorCommand(classMatcher, methodMatcher, interceptors));
    }

    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      writableCommands.add(new BindScopeCommand(annotationType, scope));
    }

    public void requestStaticInjection(Class<?>... types) {
      writableCommands.add(new RequestStaticInjectionCommand(types));
    }

    public void install(Module module) {
      module.configure(this);
    }

    public Stage currentStage() {
      return stage;
    }

    public void addError(String message, Object... arguments) {
      writableCommands.add(new AddMessageErrorCommand(message, arguments));
    }

    public void addError(Throwable t) {
      writableCommands.add(new AddThrowableErrorCommand(t));
    }

    public <T> BindCommand<T>.BindingBuilder bind(Key<T> key) {
      BindCommand<T> bindCommand = new BindCommand<T>(key);
      writableCommands.add(bindCommand);
      return bindCommand.bindingBuilder();
    }

    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    public AnnotatedConstantBindingBuilder bindConstant() {
      BindConstantCommand bindConstantCommand = new BindConstantCommand();
      writableCommands.add(bindConstantCommand);
      return bindConstantCommand.bindingBuilder();
    }

    public <T> Provider<T> getProvider(final Key<T> key) {
      writableCommands.add(new GetProviderCommand<T>(key, earlyRequestsProvider));
      return new Provider<T>() {
        public T get() {
          return earlyRequestsProvider.get(key);
        }
      };
    }

    public <T> Provider<T> getProvider(Class<T> type) {
      return getProvider(Key.get(type));
    }

    public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
                               TypeConverter converter) {
      writableCommands.add(new ConvertToTypesCommand(typeMatcher, converter));
    }
  }
}
