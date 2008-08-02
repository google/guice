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
import com.google.common.collect.Lists;
import com.google.inject.internal.Errors;
import com.google.inject.internal.Keys;
import com.google.inject.internal.StackTraceElements;
import com.google.inject.internal.TypeResolver;
import com.google.inject.spi.Message;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Creates bindings to methods annotated with {@literal @}
 * {@link com.google.inject.Provides}. Use the scope and binding annotations
 * on the provider method to configure the binding.
 */
public class ProviderMethods {

  /**
   * Returns a module which creates bindings for provider methods from the
   * given object.
   */
  public static Module from(Object providers) {
    return new ProviderMethodsModule(providers);
  }

  static class ProviderMethodsModule extends AbstractModule {
    final Object providers;
    final TypeResolver typeResolver;

    ProviderMethodsModule(Object providers) {
      this.providers = checkNotNull(providers, "providers");
      this.typeResolver = new TypeResolver(providers.getClass());
    }

    protected void configure() {
      bindProviderMethods(providers.getClass());
    }

    void bindProviderMethods(Class<?> clazz) {
      if (clazz == Object.class) {
        return;
      }

      bindProviderMethods(clazz.getSuperclass());

      for (Method method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(Provides.class)) {
          bindProviderMethod(method);
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
        addError(message);
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

      if (scopeAnnotation == null && bindingAnnotation == null) {
        bind(returnType).toProvider(provider);
      } else if (scopeAnnotation == null) {
        bind(returnType).annotatedWith(bindingAnnotation).toProvider(provider);
      } else if (bindingAnnotation == null) {
        bind(returnType).toProvider(provider).in(scopeAnnotation);
      } else {
        bind(returnType)
            .annotatedWith(bindingAnnotation)
            .toProvider(provider)
            .in(scopeAnnotation);
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
        Provider<?> provider = getProvider(key);
        parameterProviders.add(provider);
      }

      return parameterProviders;
    }
  }
}
