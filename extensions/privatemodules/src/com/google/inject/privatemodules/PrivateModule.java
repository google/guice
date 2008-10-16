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

package com.google.inject.privatemodules;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Sets;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.ProviderMethod;
import com.google.inject.internal.ProviderMethodsModule;
import com.google.inject.internal.SourceProvider;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.DefaultElementVisitor;
import com.google.inject.spi.Element;
import com.google.inject.spi.ElementVisitor;
import com.google.inject.spi.Elements;
import com.google.inject.spi.Message;
import com.google.inject.spi.ModuleWriter;
import com.google.inject.spi.TypeConverter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * A module whose configuration information is hidden from other modules. Only bindings that are
 * explicitly {@link #expose(Class) exposed} will be available to other modules and to the injector.
 * Exposed keys must be explicitly bound, either directly or via another module that's installed
 * by the private module.
 *
 * <p>In addition to the bindings configured via {@link #configurePrivateBindings()}, bindings will
 * be created for all methods with the {@literal @}{@link com.google.inject.Provides Provides}
 * annotation. These bindings will be hidden from other modules unless the methods also have the
 * {@literal @}{@link Exposed} annotation:
 *
 * <pre>
 * public class FooBarBazModule extends PrivateModule {
 *   protected void configurePrivateBindings() {
 *     bind(Foo.class).to(RealFoo.class);
 *     expose(Foo.class);
 *
 *     install(new TransactionalBarModule());
 *     expose(Bar.class).annotatedWith(Transactional.class);
 *
 *     bind(SomeImplementationDetail.class);
 *     install(new MoreImplementationDetailsModule());
 *   }
 *
 *   {@literal @}Provides {@literal @}Exposed
 *   public Baz provideBaz() {
 *     return new SuperBaz();
 *   }
 * }
 * </pre>
 *
 * <p>Private modules are implemented with {@link Injector#createChildInjector(Module[]) parent
 * injectors}. Types that inject an {@link Injector} will be provided with the child injector. This
 * injector includes private bindings that are not available from the parent injector.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class PrivateModule implements Module {

  private final SourceProvider sourceProvider
      = new SourceProvider().plusSkippedClasses(PrivateModule.class);

  /** When this provider returns, the private injector creation has started. */
  private Provider<Ready> readyProvider;

  /** Keys exposed to the public injector */
  private Set<Expose> exposes;

  /** Like abstract module, the binder of the current private module */
  private Binder privateBinder;

  /*
   * This implementation is complicated in order to satisfy two different roles in one class:
   *
   *  1. As a public module (the one that installs this), we bind only the exposed keys. This is the
   *     role we play first, when configure() is called by the installing binder. It collects the
   *     exposed keys and their corresponding providers by executing itself as a private module.
   *
   *  2. As a private module, we bind everything. This is performed our own indirect re-entrant call
   *     to configure() via the Elements.getElements() API.
   *
   * Throwing further wrenches into the mix:
   *
   *  o Provider methods. The ProviderMethodsModule class special cases modules that extend
   *    PrivateModules to skip them by default. We have our own provider methods backdoor API
   *    called ProviderMethodsModule.forPrivateModule so provider methods are only applied when
   *    we're running as a private module. We also need to iterate through the provider methods
   *    by hand to gather the ones with the @Exposed annotation
   *
   *  o Injector creation time. Dependencies can flow freely between child and parent injectors.
   *    When providers are being exercised, we need to make sure the child injector construction
   *    has started.
   */
  public final synchronized void configure(Binder binder) {
    // when 'exposes' is null, we're being run for the public injector
    if (exposes == null) {
      configurePublicBindings(binder);
      return;
    }

    // otherwise we're being run for the private injector
    checkState(this.privateBinder == null, "Re-entry is not allowed.");
    privateBinder = binder.skipSources(PrivateModule.class);
    try {
      configurePrivateBindings();

      ProviderMethodsModule providerMethods = ProviderMethodsModule.forPrivateModule(this);
      for (ProviderMethod<?> providerMethod : providerMethods.getProviderMethods(privateBinder)) {
        providerMethod.configure(privateBinder);
        if (providerMethod.getMethod().isAnnotationPresent(Exposed.class)) {
          expose(providerMethod.getKey());
        }
      }

      for (Expose<?> expose : exposes) {
        expose.initPrivateProvider(binder);
      }
    } finally {
      privateBinder = null;
    }
  }


  private void configurePublicBindings(Binder publicBinder) {
    exposes = Sets.newLinkedHashSet();
    Key<Ready> readyKey = Key.get(Ready.class, UniqueAnnotations.create());
    readyProvider = publicBinder.getProvider(readyKey);
    try {
      List<Element> privateElements = Elements.getElements(this); // reentrant on configure()
      Set<Key<?>> privatelyBoundKeys = getBoundKeys(privateElements);
      final Module privateModule = new ModuleWriter().create(privateElements);

      for (Expose<?> expose : exposes) {
        if (!privatelyBoundKeys.contains(expose.key)) {
          publicBinder.addError("Could not expose() at %s%n %s must be explicitly bound.",
              expose.source, expose.key);
        } else {
          expose.configure(publicBinder);
        }
      }

      // create the private injector while the public injector is injecting its members. This is
      // necessary so the providers from getProvider() will work. We use provider injection as our
      // hook. Guice promises that initialize() will be called before a Ready is returned.
      publicBinder.bind(readyKey).toProvider(new Provider<Ready>() {
        @Inject void initialize(Injector publicInjector) {
          publicInjector.createChildInjector(privateModule);
        }

        public Ready get() {
          return new Ready();
        }
      });

    } finally {
      readyProvider = null;
      exposes = null;
    }
  }

  /** Marker object used to indicate the private injector has been created */
  private static class Ready {}

  /**
   * Creates bindings and other configurations private to this module. Use {@link #expose(Class)
   * expose()} to make the bindings in this module available externally.
   */
  protected abstract void configurePrivateBindings();

  /** Makes the binding for {@code key} available to other modules and the injector. */
  protected final <T> void expose(Key<T> key) {
    checkState(exposes != null, "Cannot expose %s, private module is not ready");
    exposes.add(new Expose<T>(sourceProvider.get(), readyProvider, key));
  }

  /**
   * Makes a binding for {@code type} available to other modules and the injector. Use {@link
   * ExposedKeyBuilder#annotatedWith(Class) annotatedWith()} to expose {@code type} with a binding
   * annotation.
   */
  protected final <T> ExposedKeyBuilder expose(Class<T> type) {
    checkState(exposes != null, "Cannot expose %s, private module is not ready");
    Expose<T> expose = new Expose<T>(sourceProvider.get(), readyProvider, Key.get(type));
    exposes.add(expose);
    return expose;
  }

  /**
   * Makes a binding for {@code type} available to other modules and the injector. Use {@link
   * ExposedKeyBuilder#annotatedWith(Class) annotatedWith()} to expose {@code type} with a binding
   * annotation.
   */
  protected final <T> ExposedKeyBuilder expose(TypeLiteral<T> type) {
    checkState(exposes != null, "Cannot expose %s, private module is not ready");
    Expose<T> expose = new Expose<T>(sourceProvider.get(), readyProvider, Key.get(type));
    exposes.add(expose);
    return expose;
  }

  /** Qualifies an exposed type with a binding annotation. */
  public interface ExposedKeyBuilder {
    void annotatedWith(Class<? extends Annotation> annotationType);
    void annotatedWith(Annotation annotation);
  }

  /** A binding from the private injector make visible to the public injector. */
  private static class Expose<T> implements ExposedKeyBuilder, Provider<T> {
    private final Object source;
    private final Provider<Ready> readyProvider;
    private Key<T> key; // mutable, a binding annotation may be assigned after Expose creation
    private Provider<T> privateProvider;

    private Expose(Object source, Provider<Ready> readyProvider, Key<T> key) {
      this.source = checkNotNull(source, "source");
      this.readyProvider = checkNotNull(readyProvider, "readyProvider");
      this.key = checkNotNull(key, "key");
    }

    public void annotatedWith(Class<? extends Annotation> annotationType) {
      checkState(key.getAnnotationType() == null, "already annotated");
      key = Key.get(key.getTypeLiteral(), annotationType);
    }

    public void annotatedWith(Annotation annotation) {
      checkState(key.getAnnotationType() == null, "already annotated");
      key = Key.get(key.getTypeLiteral(), annotation);
    }

    /** Sets the provider in the private injector, to be used by the public injector */
    private void initPrivateProvider(Binder privateBinder) {
      privateProvider = privateBinder.withSource(source).getProvider(key);
    }

    /** Creates a binding in the public binder */
    private void configure(Binder publicBinder) {
      publicBinder.withSource(source).bind(key).toProvider(this);
    }

    public T get() {
      readyProvider.get(); // force creation of the private injector
      return privateProvider.get();
    }
  }

  /** Returns the set of keys bound by {@code elements}. */
  private Set<Key<?>> getBoundKeys(Iterable<? extends Element> elements) {
    final Set<Key<?>> privatelyBoundKeys = Sets.newHashSet();
    ElementVisitor<Void> visitor = new DefaultElementVisitor<Void>() {
      public <T> Void visitBinding(Binding<T> command) {
        privatelyBoundKeys.add(command.getKey());
        return null;
      }
    };

    for (Element element : elements) {
      element.acceptVisitor(visitor);
    }

    return privatelyBoundKeys;
  }

  // prevent classes migrated from AbstractModule from implementing the wrong method.
  protected final void configure() {}

  // everything below is copied from AbstractModule

  protected final Binder binder() {
    return privateBinder;
  }

  protected final void bindScope(Class<? extends Annotation> scopeAnnotation, Scope scope) {
    privateBinder.bindScope(scopeAnnotation, scope);
  }

  protected final <T> LinkedBindingBuilder<T> bind(Key<T> key) {
    return privateBinder.bind(key);
  }

  protected final <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
    return privateBinder.bind(typeLiteral);
  }

  protected final <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
    return privateBinder.bind(clazz);
  }

  protected final AnnotatedConstantBindingBuilder bindConstant() {
    return privateBinder.bindConstant();
  }

  protected final void install(Module module) {
    privateBinder.install(module);
  }

  protected final void addError(String message, Object... arguments) {
    privateBinder.addError(message, arguments);
  }

  protected final void addError(Throwable t) {
    privateBinder.addError(t);
  }

  protected final void addError(Message message) {
    privateBinder.addError(message);
  }

  protected final void requestInjection(Object... objects) {
    privateBinder.requestInjection(objects);
  }

  protected final void requestStaticInjection(Class<?>... types) {
    privateBinder.requestStaticInjection(types);
  }

  protected final void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    privateBinder.bindInterceptor(classMatcher, methodMatcher, interceptors);
  }

  protected final void requireBinding(Key<?> key) {
    privateBinder.getProvider(key);
  }

  protected final void requireBinding(Class<?> type) {
    privateBinder.getProvider(type);
  }

  protected final <T> Provider<T> getProvider(Key<T> key) {
    return privateBinder.getProvider(key);
  }

  protected final <T> Provider<T> getProvider(Class<T> type) {
    return privateBinder.getProvider(type);
  }

  protected final void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter converter) {
    privateBinder.convertToTypes(typeMatcher, converter);
  }

  protected final Stage currentStage() {
    return privateBinder.currentStage();
  }
}
