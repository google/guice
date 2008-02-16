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

package com.google.inject.visitable.intercepting;

import com.google.inject.*;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.Objects.nonNull;
import com.google.inject.visitable.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.*;

/**
 * Constructs an {@link Injector} that can intercept object provision.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author jmourits@google.com (Jerome Mourits)
 */
public final class InterceptingInjectorBuilder {

  private static final Key<InjectionInterceptor> INJECTION_INTERCEPTOR_KEY
      = Key.get(InjectionInterceptor.class);

  final Collection<Module> modules = new ArrayList<Module>();
  final Set<Key<?>> interceptedKeys = new HashSet<Key<?>>();

  public InterceptingInjectorBuilder bindModules(Module... modules) {
    this.modules.addAll(Arrays.asList(modules));
    return this;
  }

  public InterceptingInjectorBuilder bindModules(Collection<Module> modules) {
    this.modules.addAll(modules);
    return this;
  }

  public InterceptingInjectorBuilder intercept(Key<?>... keys) {
    this.interceptedKeys.addAll(Arrays.asList(keys));
    return this;
  }

  public InterceptingInjectorBuilder intercept(Collection<Key<?>> keys) {
    interceptedKeys.addAll(keys);
    return this;
  }

  public InterceptingInjectorBuilder intercept(Class<?>... classes) {
    List<Key<?>> keysAsList = new ArrayList<Key<?>>(classes.length);
    for (Class<?> clas : classes) {
      keysAsList.add(Key.get(clas));
    }

    return intercept(keysAsList);
  }

  public Injector build() {
    if (interceptedKeys.contains(INJECTION_INTERCEPTOR_KEY)) {
      throw new IllegalArgumentException("Cannot intercept the interceptor!");
    }

    FutureInjector futureInjector = new FutureInjector();

    // record commands from the modules
    List<Command> commands = new CommandRecorder(futureInjector).recordCommands(modules);

    // rewrite the commands to insert interception
    Module module = new CommandRewriter().createModule(commands);

    // create and injector with the rewritten commands
    Injector injector = Guice.createInjector(module);

    // make the injector available for callbacks from early providers
    futureInjector.initialize(injector);

    return injector;
  }

  /**
   * Replays commands, inserting the InterceptingProvider where necessary.
   */
  private class CommandRewriter extends CommandReplayer {
    @Override public <T> Void visitBinding(BindCommand<T> command) {
      Key<T> key = command.getKey();

      if (!interceptedKeys.contains(key)) {
        return super.visitBinding(command);
      }

      if (command.getTarget() == null) {
        throw new UnsupportedOperationException(
            String.format("Cannot intercept bare binding of %s.", key));
      }

      Key<T> anonymousKey = Key.get(key.getTypeLiteral(), uniqueAnnotation());
      binder().bind(key).toProvider(new InterceptingProvider<T>(key, anonymousKey));

      LinkedBindingBuilder<T> linkedBindingBuilder = binder().bind(anonymousKey);
      ScopedBindingBuilder scopedBindingBuilder = command.getTarget().execute(linkedBindingBuilder);

      BindScoping scoping = command.getScoping();
      if (scoping != null) {
        scoping.execute(scopedBindingBuilder);
      }

      return null;
    }
  }

  /**
   * Provide {@code T}, with a hook for an {@link InjectionInterceptor}.
   */
  private static class InterceptingProvider<T> implements Provider<T> {
    private final Key<T> key;
    private final Key<T> anonymousKey;
    private Provider<InjectionInterceptor> injectionInterceptorProvider;
    private Provider<? extends T> delegateProvider;

    public InterceptingProvider(Key<T> key, Key<T> anonymousKey) {
      this.key = key;
      this.anonymousKey = anonymousKey;
    }

    @Inject void initialize(Injector injector, Provider<InjectionInterceptor> injectionInterceptorProvider) {
      this.injectionInterceptorProvider = nonNull(
          injectionInterceptorProvider, "injectionInterceptorProvider");
      this.delegateProvider = nonNull(
          injector.getProvider(anonymousKey), "delegateProvider");
    }

    public T get() {
      nonNull(injectionInterceptorProvider, "injectionInterceptorProvider");
      nonNull(delegateProvider, "delegateProvider");
      return injectionInterceptorProvider.get().intercept(key, delegateProvider);
    }
  }

  /**
   * Returns an annotation instance that is not equal to any other annotation
   * instances, for use in creating distinct {@link Key}s.
   */
  private static Annotation uniqueAnnotation() {
    return new Annotation() {
      public Class<? extends Annotation> annotationType() {
        return Internal.class;
      }
      @Override public String toString() {
        return "InterceptingBinderPrivate";
      }
    };
  }
  @Retention(RUNTIME) @BindingAnnotation
  private @interface Internal { }
}
