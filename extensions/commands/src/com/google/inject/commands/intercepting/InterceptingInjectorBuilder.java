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

package com.google.inject.commands.intercepting;

import com.google.inject.*;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.Objects.nonNull;
import com.google.inject.commands.*;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.util.*;

/**
 * Builds an {@link Injector} that intercepts provision.
 *
 * <h3>Limitations of the current implementation</h3>
 *
 * <p>All intercepted bindings must have binding targets - for example, a type
 * that is bound to itself cannot be intercepted:
 * <pre class="code">bind(MyServiceClass.class);</pre>
 *
 * <p>All intercepted bindings must be bound explicitly. Interception cannot
 * be applied to implicit bindings, or bindings that depend on
 * {@literal @}{@link ProvidedBy}, {@literal @}{@link ImplementedBy}
 * annotations.
 *
 * <p><strong>Implementation note:</strong> To intercept provision, an
 * additional, internal binding is created for each intercepted key. This is
 * used to bind the original (non-intercepted) provisioning strategy, and an
 * intercepting binding is created for the original key. This shouldn't have
 * any side-effects on the behaviour of the injector, but may confuse tools
 * that depend on {@link Injector#getBindings()} and similar methods.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author jmourits@google.com (Jerome Mourits)
 */
public final class InterceptingInjectorBuilder {

  private static final Key<ProvisionInterceptor> INJECTION_INTERCEPTOR_KEY
      = Key.get(ProvisionInterceptor.class);

  private final Collection<Module> modules = new ArrayList<Module>();
  private final Set<Key<?>> keysToIntercept = new HashSet<Key<?>>();
  private boolean tolerateUnmatchedInterceptions = false;

  public InterceptingInjectorBuilder install(Module... modules) {
    this.modules.addAll(Arrays.asList(modules));
    return this;
  }

  public InterceptingInjectorBuilder install(Collection<Module> modules) {
    this.modules.addAll(modules);
    return this;
  }

  public InterceptingInjectorBuilder intercept(Key<?>... keys) {
    this.keysToIntercept.addAll(Arrays.asList(keys));
    return this;
  }

  public InterceptingInjectorBuilder intercept(Collection<Key<?>> keys) {
    if (keys.contains(INJECTION_INTERCEPTOR_KEY)) {
      throw new IllegalArgumentException("Cannot intercept the interceptor!");
    }

    keysToIntercept.addAll(keys);
    return this;
  }

  public InterceptingInjectorBuilder intercept(Class<?>... classes) {
    List<Key<?>> keysAsList = new ArrayList<Key<?>>(classes.length);
    for (Class<?> clas : classes) {
      keysAsList.add(Key.get(clas));
    }

    return intercept(keysAsList);
  }

  public InterceptingInjectorBuilder tolerateUnmatchedInterceptions() {
    this.tolerateUnmatchedInterceptions = true;
    return this;
  }

  public Injector build() {
    FutureInjector futureInjector = new FutureInjector();

    // record commands from the modules
    List<Command> commands = new CommandRecorder(futureInjector).recordCommands(modules);

    // rewrite the commands to insert interception
    CommandRewriter rewriter = new CommandRewriter();
    Module module = rewriter.createModule(commands);

    // create and injector with the rewritten commands
    Injector injector = Guice.createInjector(module);

    // fail if any interceptions were missing
    if (!tolerateUnmatchedInterceptions 
        && !rewriter.keysIntercepted.equals(keysToIntercept)) {
      Set<Key> keysNotIntercepted = new HashSet<Key>(keysToIntercept);
      keysNotIntercepted.removeAll(rewriter.keysIntercepted);
      throw new IllegalArgumentException("An explicit binding is required for "
          + "all intercepted keys, but was not found for " + keysNotIntercepted);
    }

    // make the injector available for callbacks from early providers
    futureInjector.initialize(injector);

    return injector;
  }

  /**
   * Replays commands, inserting the InterceptingProvider where necessary.
   */
  private class CommandRewriter extends CommandReplayer {
    private Set<Key> keysIntercepted = new HashSet<Key>();

    @Override public <T> void replayBind(Binder binder, BindCommand<T> command) {
      Key<T> key = command.getKey();

      if (!keysToIntercept.contains(key)) {
        super.replayBind(binder, command);
        return;
      }

      if (command.getTarget() == null) {
        throw new UnsupportedOperationException(
            String.format("Cannot intercept bare binding of %s.", key));
      }

      Key<T> anonymousKey = Key.get(key.getTypeLiteral(), uniqueAnnotation());
      binder.bind(key).toProvider(new InterceptingProvider<T>(key, anonymousKey));

      LinkedBindingBuilder<T> linkedBindingBuilder = binder.bind(anonymousKey);
      ScopedBindingBuilder scopedBindingBuilder = command.getTarget().execute(linkedBindingBuilder);

      // we scope the user's provider, not the interceptor. This is dangerous,
      // but convenient. It means that although the user's provider will live
      // in its proper scope, the intereptor gets invoked without a scope
      BindScoping scoping = command.getScoping();
      if (scoping != null) {
        scoping.execute(scopedBindingBuilder);
      }

      keysIntercepted.add(key);
    }
  }

  /**
   * Provide {@code T}, with a hook for an {@link ProvisionInterceptor}.
   */
  private static class InterceptingProvider<T> implements Provider<T> {
    private final Key<T> key;
    private final Key<T> anonymousKey;
    private Provider<ProvisionInterceptor> injectionInterceptorProvider;
    private Provider<? extends T> delegateProvider;

    public InterceptingProvider(Key<T> key, Key<T> anonymousKey) {
      this.key = key;
      this.anonymousKey = anonymousKey;
    }

    @Inject void initialize(Injector injector,
        Provider<ProvisionInterceptor> injectionInterceptorProvider) {
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
