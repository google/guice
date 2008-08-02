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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProviderMethods;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.internal.SourceProvider;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.TypeConverter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Records commands executed by a module so they can be inspected or
 * {@link CommandReplayer replayed}.
 *
 * @deprecated replaced with {@link com.google.inject.spi.Elements#getElements(Module[])}
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Deprecated
public final class CommandRecorder {
  private Stage currentStage = Stage.DEVELOPMENT;

  /**
   * Sets the stage reported by the binder.
   */
  public void setCurrentStage(Stage currentStage) {
    this.currentStage = currentStage;
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public List<Command> recordCommands(Module... modules) {
    return recordCommands(Arrays.asList(modules));
  }

  /**
   * Records the commands executed by {@code modules}.
   */
  public List<Command> recordCommands(Iterable<? extends Module> modules) {
    RecordingBinder binder = new RecordingBinder();
    for (Module module : modules) {
      binder.install(module);
    }
    return Collections.unmodifiableList(binder.commands);
  }

  private class RecordingBinder implements Binder {
    private final Set<Module> modules;
    private final List<Command> commands;
    private final Object source;
    private final SourceProvider sourceProvider;

    private RecordingBinder() {
      modules = Sets.newHashSet();
      commands = Lists.newArrayList();
      source = null;
      sourceProvider = new SourceProvider()
          .plusSkippedClasses(CommandRecorder.class, RecordingBinder.class, AbstractModule.class);
    }

    /**
     * Creates a recording binder that's backed by the same configuration as
     * {@code backingBinder}.
     */
    private RecordingBinder(RecordingBinder parent, Object source, SourceProvider sourceProvider) {
      checkArgument(source == null ^ sourceProvider == null);

      modules = parent.modules;
      commands = parent.commands;
      this.source = source;
      this.sourceProvider = sourceProvider;
    }

    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      commands.add(new BindInterceptorCommand(getSource(), classMatcher, methodMatcher, interceptors));
    }

    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      commands.add(new BindScopeCommand(getSource(), annotationType, scope));
    }

    public void requestInjection(Object... instances) {
      commands.add(new RequestInjectionCommand(getSource(), instances));
    }

    public void requestStaticInjection(Class<?>... types) {
      commands.add(new RequestStaticInjectionCommand(getSource(), types));
    }

    public void install(Module module) {
      if (modules.add(module)) {
        try {
          module.configure(this);
        } catch (RuntimeException e) {
          addError(e);
        }
        install(ProviderMethods.from(module));
      }
    }

    public Stage currentStage() {
      return currentStage;
    }

    public void addError(String message, Object... arguments) {
      commands.add(new AddMessageCommand(getSource(), message, arguments));
    }

    public void addError(Throwable t) {
      commands.add(new AddMessageCommand(getSource(), t));
    }

    public void addError(Message message) {
      commands.add(new AddMessageCommand(message));
    }

    public <T> BindCommand<T>.BindingBuilder bind(Key<T> key) {
      BindCommand<T> bindCommand = new BindCommand<T>(getSource(), key);
      commands.add(bindCommand);
      return bindCommand.bindingBuilder(RecordingBinder.this);
    }

    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    public AnnotatedConstantBindingBuilder bindConstant() {
      BindConstantCommand bindConstantCommand = new BindConstantCommand(getSource());
      commands.add(bindConstantCommand);
      return bindConstantCommand.bindingBuilder(RecordingBinder.this);
    }

    public <T> Provider<T> getProvider(final Key<T> key) {
      final GetProviderCommand<T> command = new GetProviderCommand<T>(getSource(), key);
      commands.add(command);
      return new Provider<T>() {
        public T get() {
          Provider<T> delegate = command.getDelegate();
          checkState(delegate != null,
              "This provider cannot be used until the Injector has been created.");
          return delegate.get();
        }

        @Override public String toString() {
          return "Provider<" + key.getTypeLiteral() + ">";
        }
      };
    }

    public <T> Provider<T> getProvider(Class<T> type) {
      return getProvider(Key.get(type));
    }

    public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
        TypeConverter converter) {
      commands.add(new ConvertToTypesCommand(getSource(), typeMatcher, converter));
    }

    public Binder withSource(final Object source) {
      return new RecordingBinder(this, source, null);
    }

    public Binder skipSources(Class... classesToSkip) {
      // if a source is specified explicitly, we don't need to skip sources
      if (source != null) {
        return this;
      }

      SourceProvider newSourceProvider = sourceProvider.plusSkippedClasses(classesToSkip);
      return new RecordingBinder(this, null, newSourceProvider);
    }

    protected Object getSource() {
      return sourceProvider != null
          ? sourceProvider.get()
          : source;
    }

    @Override public String toString() {
      return "Binder";
    }
  }
}
