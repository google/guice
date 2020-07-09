/*
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

import com.google.inject.internal.BindingImpl;
import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.SingletonScope;
import com.google.inject.spi.BindingScopingVisitor;
import com.google.inject.spi.ExposedBinding;
import com.google.inject.spi.LinkedKeyBinding;
import java.lang.annotation.Annotation;

/**
 * Built-in scope implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  private Scopes() {}

  /** One instance per {@link Injector}. Also see {@code @}{@link Singleton}. */
  public static final Scope SINGLETON = new SingletonScope();

  /**
   * No scope; the same as not applying any scope at all. Each time the Injector obtains an instance
   * of an object with "no scope", it injects this instance then immediately forgets it. When the
   * next request for the same binding arrives it will need to obtain the instance over again.
   *
   * <p>This exists only in case a class has been annotated with a scope annotation such as {@link
   * Singleton @Singleton}, and you need to override this to "no scope" in your binding.
   *
   * @since 2.0
   */
  public static final Scope NO_SCOPE =
      new Scope() {
        @Override
        public <T> Provider<T> scope(Key<T> key, Provider<T> unscoped) {
          return unscoped;
        }

        @Override
        public String toString() {
          return "Scopes.NO_SCOPE";
        }
      };

  private static final BindingScopingVisitor<Boolean> IS_SINGLETON_VISITOR =
      new BindingScopingVisitor<Boolean>() {
        @Override
        public Boolean visitNoScoping() {
          return false;
        }

        @Override
        public Boolean visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
          return scopeAnnotation == Singleton.class
              || scopeAnnotation == javax.inject.Singleton.class;
        }

        @Override
        public Boolean visitScope(Scope scope) {
          return scope == Scopes.SINGLETON;
        }

        @Override
        public Boolean visitEagerSingleton() {
          return true;
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
      boolean singleton = binding.acceptScopingVisitor(IS_SINGLETON_VISITOR);
      if (singleton) {
        return true;
      }

      if (binding instanceof LinkedKeyBinding) {
        LinkedKeyBinding<?> linkedBinding = (LinkedKeyBinding) binding;
        Injector injector = getInjector(linkedBinding);
        if (injector != null) {
          binding = injector.getBinding(linkedBinding.getLinkedKey());
          continue;
        }
      } else if (binding instanceof ExposedBinding) {
        ExposedBinding<?> exposedBinding = (ExposedBinding) binding;
        Injector injector = exposedBinding.getPrivateElements().getInjector();
        if (injector != null) {
          binding = injector.getBinding(exposedBinding.getKey());
          continue;
        }
      }

      return false;
    } while (true);
  }

  /**
   * Returns true if {@code binding} has the given scope. If the binding is a {@link
   * com.google.inject.spi.LinkedKeyBinding linked key binding} and belongs to an injector (ie. it
   * was retrieved via {@link Injector#getBinding Injector.getBinding()}), then this method will
   * also true if the target binding has the given scope.
   *
   * @param binding binding to check
   * @param scope scope implementation instance
   * @param scopeAnnotation scope annotation class
   * @since 4.0
   */
  public static boolean isScoped(
      Binding<?> binding, final Scope scope, final Class<? extends Annotation> scopeAnnotation) {
    do {
      boolean matches =
          binding.acceptScopingVisitor(
              new BindingScopingVisitor<Boolean>() {
                @Override
                public Boolean visitNoScoping() {
                  return false;
                }

                @Override
                public Boolean visitScopeAnnotation(Class<? extends Annotation> visitedAnnotation) {
                  return visitedAnnotation == scopeAnnotation;
                }

                @Override
                public Boolean visitScope(Scope visitedScope) {
                  return visitedScope == scope;
                }

                @Override
                public Boolean visitEagerSingleton() {
                  return false;
                }
              });

      if (matches) {
        return true;
      }

      if (binding instanceof LinkedKeyBinding) {
        LinkedKeyBinding<?> linkedBinding = (LinkedKeyBinding) binding;
        Injector injector = getInjector(linkedBinding);
        if (injector != null) {
          binding = injector.getBinding(linkedBinding.getLinkedKey());
          continue;
        }
      } else if (binding instanceof ExposedBinding) {
        ExposedBinding<?> exposedBinding = (ExposedBinding) binding;
        Injector injector = exposedBinding.getPrivateElements().getInjector();
        if (injector != null) {
          binding = injector.getBinding(exposedBinding.getKey());
          continue;
        }
      }

      return false;
    } while (true);
  }

  private static Injector getInjector(LinkedKeyBinding<?> linkedKeyBinding) {
    if (linkedKeyBinding instanceof BindingImpl) {
      return ((BindingImpl<?>) linkedKeyBinding).getInjector();
    }
    return null;
  }

  /**
   * Returns true if the object is a proxy for a circular dependency, constructed by Guice because
   * it encountered a circular dependency. Scope implementations should be careful to <b>not cache
   * circular proxies</b>, because the proxies are not intended for general purpose use. (They are
   * designed just to fulfill the immediate injection, not all injections. Caching them can lead to
   * IllegalArgumentExceptions or ClassCastExceptions.)
   *
   * @since 4.0
   */
  public static boolean isCircularProxy(Object object) {
    return BytecodeGen.isCircularProxy(object);
  }
}
