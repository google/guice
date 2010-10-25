/**
 * Copyright (C) 2006 Google Inc.
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

import com.google.inject.internal.CircularDependencyProxy;
import com.google.inject.internal.InternalInjectorCreator;
import com.google.inject.internal.LinkedBindingImpl;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ExposedBinding;

import java.lang.annotation.Annotation;

/**
 * Built-in scope implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  private Scopes() {}

  /** A sentinel value representing null. */
  private static final Object NULL = new Object();

  /**
   * One instance per {@link Injector}. Also see {@code @}{@link Singleton}.
   */
  public static final Scope SINGLETON = new Scope() {
    public <T> Provider<T> scope(final Key<T> key, final Provider<T> creator) {
      return new Provider<T>() {
        /*
         * The lazily initialized singleton instance. Once set, this will either have type T or will
         * be equal to NULL.
         */
        private volatile Object instance;

        // DCL on a volatile is safe as of Java 5, which we obviously require.
        @SuppressWarnings("DoubleCheckedLocking")
        public T get() {
          if (instance == null) {
            /*
             * Use a pretty coarse lock. We don't want to run into deadlocks
             * when two threads try to load circularly-dependent objects.
             * Maybe one of these days we will identify independent graphs of
             * objects and offer to load them in parallel.
             *
             * This block is re-entrant for circular dependencies.
             */
            synchronized (InternalInjectorCreator.class) {
              if (instance == null) {
                T provided = creator.get();

                // don't remember proxies; these exist only to serve circular dependencies
                if (provided instanceof CircularDependencyProxy) {
                  return provided;
                }

                Object providedOrSentinel = (provided == null) ? NULL : provided;
                if (instance != null && instance != providedOrSentinel) {
                  throw new ProvisionException(
                      "Provider was reentrant while creating a singleton");
                }

                instance = providedOrSentinel;
              }
            }
          }

          Object localInstance = instance;
          // This is safe because instance has type T or is equal to NULL
          @SuppressWarnings("unchecked")
          T returnedInstance = (localInstance != NULL) ? (T) localInstance : null;
          return returnedInstance;
        }

        public String toString() {
          return String.format("%s[%s]", creator, SINGLETON);
        }
      };
    }

    @Override public String toString() {
      return "Scopes.SINGLETON";
    }
  };

  /**
   * No scope; the same as not applying any scope at all.  Each time the
   * Injector obtains an instance of an object with "no scope", it injects this
   * instance then immediately forgets it.  When the next request for the same
   * binding arrives it will need to obtain the instance over again.
   *
   * <p>This exists only in case a class has been annotated with a scope
   * annotation such as {@link Singleton @Singleton}, and you need to override
   * this to "no scope" in your binding.
   *
   * @since 2.0
   */
  public static final Scope NO_SCOPE = new Scope() {
    public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
      return unscoped;
    }
    @Override public String toString() {
      return "Scopes.NO_SCOPE";
    }
  };

  /**
   * Returns true if {@code binding} is singleton-scoped. If the binding is a {@link
   * com.google.inject.spi.LinkedKeyBinding linked key binding} and belongs to an injector (ie. it
   * was retrieved via {@link Injector#getBinding Injector.getBinding()}), then this method will
   * also true if the target binding is singleton-scoped.
   *
   * @since 3.0
   */
  public static boolean isSingleton(Binding<?> binding) {
    do {
      boolean singleton = binding.acceptScopingVisitor(new BindingScopingVisitor<Boolean>() {
        public Boolean visitNoScoping() {
          return false;
        }

        public Boolean visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
          return scopeAnnotation == Singleton.class
              || scopeAnnotation == javax.inject.Singleton.class;
        }

        public Boolean visitScope(Scope scope) {
          return scope == Scopes.SINGLETON;
        }

        public Boolean visitEagerSingleton() {
          return true;
        }
      });

      if (singleton) {
        return true;
      }

      if (binding instanceof LinkedBindingImpl) {
        LinkedBindingImpl<?> linkedBinding = (LinkedBindingImpl) binding;
        Injector injector = (Injector) linkedBinding.getInjector();
        if (injector != null) {
          binding = injector.getBinding(linkedBinding.getLinkedKey());
          continue;
        }
      } else if(binding instanceof ExposedBinding) {
        ExposedBinding<?> exposedBinding = (ExposedBinding)binding;
        Injector injector = exposedBinding.getPrivateElements().getInjector();
        if (injector != null) {
          binding = injector.getBinding(exposedBinding.getKey());
          continue;
        }
      }

      return false;
    } while (true);
  }
}
