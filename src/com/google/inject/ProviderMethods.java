/**
 * Copyright (C) 2007 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.collect.Lists;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.internal.Errors;
import com.google.inject.internal.Keys;
import com.google.inject.internal.StackTraceElements;
import com.google.inject.internal.TypeResolver;
import com.google.inject.spi.Message;
import com.google.inject.util.Modules;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Creates bindings to methods annotated with {@literal @}{@link Provides}. Use the scope and
 * binding annotations on the provider method to configure the binding.
 */
public class ProviderMethods {

  /**
   * Returns a module which creates bindings for provider methods from the
   * given object.
   */
  public static Module from(Object providers) {
    // avoid infinite recursion, since installing a module always installs itself
    if (providers instanceof ProviderMethodsModule) {
      return Modules.EMPTY_MODULE;
    }

    return new ProviderMethodsModule(providers);
  }

  static class ProviderMethodsModule implements Module {
    final Object providers;
    final TypeResolver typeResolver;
    Binder binder;

    ProviderMethodsModule(Object providers) {
      this.providers = checkNotNull(providers, "providers");
      this.typeResolver = new TypeResolver(providers.getClass());
    }

    public synchronized void configure(Binder binder) {
      checkState(this.binder == null, "Re-entry is not allowed.");

      for (Class c = providers.getClass(); c != Object.class; c = c.getSuperclass()) {
        for (Method method : c.getDeclaredMethods()) {
          if (!method.isAnnotationPresent(Provides.class)) {
            continue;
          }

          this.binder = binder.withSource(StackTraceElements.forMember(method));
          try {
            bindProviderMethod(method);
          } finally {
            this.binder = null;
          }
        }
      }
    }

    <T> void bindProviderMethod(final Method method) {
      Errors errors = new Errors(StackTraceElements.forMember(method));

      method.setAccessible(true);

      Class<? extends Annotation> scopeAnnotation
          = Scopes.findScopeAnnotation(errors, method.getAnnotations());
      Annotation bindingAnnotation
          = Keys.findBindingAnnotation(errors, method, method.getAnnotations());

      final List<Provider<?>> parameterProviders = findParameterProviders(errors, method);

      for (Message message : errors.getMessages()) {
        binder.addError(message);
      }

      // Define T as the method's return type.
      @SuppressWarnings("unchecked")
      TypeLiteral<T> returnType
          = (TypeLiteral<T>) TypeLiteral.get(typeResolver.getReturnType(method));

      Provider<T> provider = new Provider<T>() {
        public T get() {
          Object[] parameters = new Object[parameterProviders.size()];
          for (int i = 0; i < parameters.length; i++) {
            parameters[i] = parameterProviders.get(i).get();
          }

          try {
            // We know this cast is safe becase T is the method's return type.
            @SuppressWarnings({ "unchecked", "UnnecessaryLocalVariable" })
            T result = (T) method.invoke(providers, parameters);
            return result;
          }
          catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
          catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        }
      };

      AnnotatedBindingBuilder<T> builder = binder.bind(returnType);

      if (bindingAnnotation != null) {
        builder.annotatedWith(bindingAnnotation);
      }

      builder.toProvider(provider);

      if (scopeAnnotation != null) {
        builder.in(scopeAnnotation);
      }
    }

    List<Provider<?>> findParameterProviders(Errors errors, Method method) {
      List<Provider<?>> parameterProviders = Lists.newArrayList();

      List<Type> parameterTypes = typeResolver.getParameterTypes(method);
      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      for (int i = 0; i < parameterTypes.size(); i++) {
        Type parameterType = parameterTypes.get(i);
        Annotation bindingAnnotation
            = Keys.findBindingAnnotation(errors, method, parameterAnnotations[i]);
        Key<?> key = bindingAnnotation == null ? Key.get(parameterType)
            : Key.get(parameterType, bindingAnnotation);
        Provider<?> provider = binder.getProvider(key);
        parameterProviders.add(provider);
      }

      return parameterProviders;
    }

    @Override public boolean equals(Object o) {
      return o instanceof ProviderMethodsModule
          && ((ProviderMethodsModule) o).providers == providers;
    }

    @Override public int hashCode() {
      return providers.hashCode();
    }
  }
}
