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

import com.google.inject.internal.StackTraceElements;
import com.google.inject.spi.SourceProvider;
import com.google.inject.spi.SourceProviders;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates bindings to methods annotated with
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

    Object source;

    final SourceProvider sourceProvider = new SourceProvider() {
      public Object source() {
        return source;
      }
    };

    ProviderMethodsModule(Object providers) {
      this.providers = providers;
    }

    protected void configure() {
      SourceProviders.withDefault(sourceProvider, new Runnable() {
        public void run() {
          bindProviderMethods(providers.getClass());
        }
      });
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

    void bindProviderMethod(final Method method) {
      this.source = StackTraceElements.forMember(method);

      method.setAccessible(true);

      Class<? extends Annotation> scopeAnnotation
          = findScopeAnnotation(method.getAnnotations());
      Annotation bindingAnnotation = findBindingAnnotation(
          method.getAnnotations());

      final List<Provider<?>> parameterProviders
          = findParameterProviders(method);

      Provider<Object> provider = new Provider<Object>() {
        public Object get() {
          Object[] parameters = new Object[parameterProviders.size()];
          for (int i = 0; i < parameters.length; i++) {
            parameters[i] = parameterProviders.get(i).get();
          }

          try {
            return method.invoke(providers, parameters);
          }
          catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
          catch (InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        }
      };

      // TODO: Fix type warnings.
      Class type = method.getReturnType();
      if (scopeAnnotation == null && bindingAnnotation == null) {
        bind(type).toProvider(provider);
      } else if (scopeAnnotation == null) {
        bind(type).annotatedWith(bindingAnnotation).toProvider(provider);
      } else if (bindingAnnotation == null) {
        bind(type).toProvider(provider).in(scopeAnnotation);
      } else {
        bind(type)
            .annotatedWith(bindingAnnotation)
            .toProvider(provider)
            .in(scopeAnnotation);
      }
    }

    List<Provider<?>> findParameterProviders(Method method) {
      List<Provider<?>> parameterProviders = new ArrayList<Provider<?>>();

      Type[] parameterTypes = method.getGenericParameterTypes();
      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      for (int i = 0; i < parameterTypes.length; i++) {
        Annotation bindingAnnotation =
          findBindingAnnotation(parameterAnnotations[i]);
        Key<?> key = bindingAnnotation == null
            ? Key.get(parameterTypes[i])
            : Key.get(parameterTypes[i], bindingAnnotation);
        Provider<?> provider = getProvider(key);
        parameterProviders.add(provider);
      }

      return parameterProviders;
    }

    Class<? extends Annotation> findScopeAnnotation(Annotation[] annotations) {
      Class<? extends Annotation> found = null;

      for (Annotation annotation : annotations) {
        if (annotation.annotationType()
            .isAnnotationPresent(ScopeAnnotation.class)) {
          if (found != null) {
            addError(ErrorMessages.DUPLICATE_SCOPE_ANNOTATIONS,
                "@" + found.getSimpleName(),
                "@" + annotation.annotationType().getSimpleName()
            );
          } else {
            found = annotation.annotationType();
          }
        }
      }

      return found;
    }

    Annotation findBindingAnnotation(Annotation[] annotations) {
      Annotation found = null;

      for (Annotation annotation : annotations) {
        if (annotation.annotationType()
            .isAnnotationPresent(BindingAnnotation.class)) {
          if (found != null) {
            addError(ErrorMessages.DUPLICATE_BINDING_ANNOTATIONS,
                "@" + found.annotationType().getSimpleName(),
                "@" + annotation.annotationType().getSimpleName()
            );
          } else {
            found = annotation;
          }
        }
      }

      return found;
    }
  }
}
