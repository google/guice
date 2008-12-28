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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.ProvidedBy;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.name.Names;
import com.google.inject.spi.DefaultBindingTargetVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.Elements;
import com.google.inject.spi.ModuleWriter;
import com.google.inject.spi.UntargettedBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Builds an {@link Injector} that intercepts provision.
 *
 * <p>The injector contains an extra binding for {@code Set<Key>} annotated
 * with the name "Interceptable". This bound set contains all intercepted keys.
 *
 * <h3>Limitations of the current implementation</h3>
 *
 * <p>All intercepted bindings must have binding targets - for example, a type
 * that is bound to itself cannot be intercepted:
 * <pre class="code">bind(MyServiceClass.class);</pre>
 *
 * <p>All intercepted bindings must be bound explicitly. Interception cannot
 * be applied to implicit bindings, or bproindings that depend on
 * {@literal @}{@link ProvidedBy}, {@literal @}{@link ImplementedBy}, {@literal @}{@link Provides}
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
  private final Set<Key<?>> keysToIntercept = Sets.newHashSet();
  private boolean tolerateUnmatchedInterceptions = false;

  public InterceptingInjectorBuilder() {
    // bind the keys to intercept
    modules.add(new AbstractModule() {
      protected void configure() {
        bind(new TypeLiteral<Set<Key>>() {})
            .annotatedWith(Names.named("Interceptable"))
            .toInstance(Collections.<Key>unmodifiableSet(keysToIntercept));
      }
    });
  }

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
    checkArgument(!keys.contains(INJECTION_INTERCEPTOR_KEY),
        "Cannot intercept the interceptor!");

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
    // record commands from the modules
    List<Element> elements = Elements.getElements(modules);

    // rewrite the commands to insert interception
    ModuleRewriter rewriter = new ModuleRewriter();
    Module module = rewriter.create(elements);

    // create and injector with the rewritten commands
    Injector injector = Guice.createInjector(module);

    // fail if any interceptions were missing
    if (!tolerateUnmatchedInterceptions 
        && !rewriter.keysIntercepted.equals(keysToIntercept)) {
      Set<Key<?>> keysNotIntercepted = Sets.newHashSet(keysToIntercept);
      keysNotIntercepted.removeAll(rewriter.keysIntercepted);
      throw new IllegalArgumentException("An explicit binding is required for "
          + "all intercepted keys, but was not found for " + keysNotIntercepted);
    }

    return injector;
  }

  /** Replays commands, inserting the InterceptingProvider where necessary. */
  private class ModuleRewriter extends ModuleWriter {
    private Set<Key<?>> keysIntercepted = Sets.newHashSet();

    @Override public <T> void writeBind(Binder binder, Binding<T> binding) {
      final Key<T> key = binding.getKey();

      if (!keysToIntercept.contains(key)) {
        super.writeBind(binder, binding);
        return;
      }

      binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<T, Void>() {
        @Override public Void visitUntargetted(
            UntargettedBinding<? extends T> tUntargettedBinding) {
          throw new UnsupportedOperationException(
              String.format("Cannot intercept bare binding of %s.", key));
        }
      });

      Key<T> anonymousKey = Key.get(key.getTypeLiteral(), UniqueAnnotations.create());
      binder.bind(key).toProvider(new InterceptingProvider<T>(key, anonymousKey));

      ScopedBindingBuilder scopedBindingBuilder = bindKeyToTarget(binding, binder, anonymousKey);

      // we scope the user's provider, not the interceptor. This is dangerous,
      // but convenient. It means that although the user's provider will live
      // in its proper scope, the intereptor gets invoked without a scope
      applyScoping(binding, scopedBindingBuilder);

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
      this.injectionInterceptorProvider
          = checkNotNull(injectionInterceptorProvider, "injectionInterceptorProvider");
      this.delegateProvider
          = checkNotNull(injector.getProvider(anonymousKey), "delegateProvider");
    }

    public T get() {
      checkNotNull(injectionInterceptorProvider, "injectionInterceptorProvider");
      checkNotNull(delegateProvider, "delegateProvider");
      return injectionInterceptorProvider.get().intercept(key, delegateProvider);
    }
  }
}
