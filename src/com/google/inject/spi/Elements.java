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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding.TargetVisitor;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.internal.ModuleBinding;
import com.google.inject.internal.SourceProvider;
import com.google.inject.matcher.Matcher;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Records elements executed by a module so they can be inspected or
 * {@link ModuleWriter replayed}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class Elements {
  private static final TargetVisitor<Object, Object> GET_INSTANCE_VISITOR
      = new DefaultBindTargetVisitor<Object, Object>() {
    @Override public Object visitInstance(Object instance) {
      return instance;
    }

    @Override protected Object visitTarget() {
      throw new IllegalArgumentException();
    }
  };

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Module... modules) {
    return getElements(Stage.DEVELOPMENT, Arrays.asList(modules));
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Stage stage, Module... modules) {
    return getElements(stage, Arrays.asList(modules));
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Iterable<? extends Module> modules) {
    return getElements(Stage.DEVELOPMENT, modules);
  }

  /**
   * Records the elements executed by {@code modules}.
   */
  public static List<Element> getElements(Stage stage, Iterable<? extends Module> modules) {
    RecordingBinder binder = new RecordingBinder(stage);
    for (Module module : modules) {
      binder.install(module);
    }
    return Collections.unmodifiableList(binder.elements);
  }

  @SuppressWarnings("unchecked")
  public static <T> com.google.inject.Binding.TargetVisitor<T, T> getInstanceVisitor() {
    return (com.google.inject.Binding.TargetVisitor<T, T>) GET_INSTANCE_VISITOR;
  }

  private static class RecordingBinder implements Binder {
    private final Stage stage;
    private final Set<Module> modules;
    private final List<Element> elements;
    private final Object source;
    private final SourceProvider sourceProvider;

    private RecordingBinder(Stage stage) {
      this.stage = stage;
      this.modules = Sets.newHashSet();
      this.elements = Lists.newArrayList();
      this.source = null;
      this.sourceProvider = new SourceProvider()
          .plusSkippedClasses(Elements.class, RecordingBinder.class, AbstractModule.class);
    }

    /**
     * Creates a recording binder that's backed by the same configuration as
     * {@code backingBinder}.
     */
    private RecordingBinder(RecordingBinder parent, Object source, SourceProvider sourceProvider) {
      checkArgument(source == null ^ sourceProvider == null);

      this.stage = parent.stage;
      this.modules = parent.modules;
      this.elements = parent.elements;
      this.source = source;
      this.sourceProvider = sourceProvider;
    }

    public void bindInterceptor(
        Matcher<? super Class<?>> classMatcher,
        Matcher<? super Method> methodMatcher,
        MethodInterceptor... interceptors) {
      elements.add(new BindInterceptor(getSource(), classMatcher, methodMatcher, interceptors));
    }

    public void bindScope(Class<? extends Annotation> annotationType, Scope scope) {
      elements.add(new BindScope(getSource(), annotationType, scope));
    }

    public void requestInjection(Object... instances) {
      elements.add(new RequestInjection(getSource(), instances));
    }

    public void requestStaticInjection(Class<?>... types) {
      elements.add(new RequestStaticInjection(getSource(), types));
    }

    public void install(Module module) {
      if (modules.add(module)) {
        try {
          module.configure(this);
        } catch (RuntimeException e) {
          addError(e);
        }
      }
    }

    public Stage currentStage() {
      return stage;
    }

    public void addError(String message, Object... arguments) {
      elements.add(new Message(getSource(), String.format(message, arguments)));
    }

    public void addError(Throwable t) {
      elements.add(new Message(getSource(), t));
    }

    public void addError(Message message) {
      elements.add(message);
    }

    public <T> ModuleBinding<T>.BindingBuilder bind(Key<T> key) {
      ModuleBinding<T> moduleBindingCommand = new ModuleBinding<T>(getSource(), key);
      elements.add(moduleBindingCommand);
      return moduleBindingCommand.bindingBuilder(RecordingBinder.this);
    }

    public <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
      return bind(Key.get(typeLiteral));
    }

    public <T> AnnotatedBindingBuilder<T> bind(Class<T> type) {
      return bind(Key.get(type));
    }

    public AnnotatedConstantBindingBuilder bindConstant() {
      BindConstant bindConstantCommand = new BindConstant(getSource());
      elements.add(bindConstantCommand);
      return bindConstantCommand.bindingBuilder(RecordingBinder.this);
    }

    public <T> Provider<T> getProvider(final Key<T> key) {
      final GetProvider<T> command = new GetProvider<T>(getSource(), key);
      elements.add(command);
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
      elements.add(new ConvertToTypes(getSource(), typeMatcher, converter));
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
