/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.throwingproviders;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.internal.util.Lists;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Message;
import com.google.inject.util.Modules;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

/**
 * Creates bindings to methods annotated with {@literal @}{@link CheckedProvides}. Use the scope
 * and binding annotations on the provider method to configure the binding.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
final class CheckedProviderMethodsModule implements Module {
  private final Object delegate;
  private final TypeLiteral<?> typeLiteral;

  private CheckedProviderMethodsModule(Object delegate) {
    this.delegate = checkNotNull(delegate, "delegate");
    this.typeLiteral = TypeLiteral.get(this.delegate.getClass());
  }

  /**
   * Returns a module which creates bindings for provider methods from the given module.
   */
  static Module forModule(Module module) {
    // avoid infinite recursion, since installing a module always installs itself
    if (module instanceof CheckedProviderMethodsModule) {
      return Modules.EMPTY_MODULE;
    }

    return new CheckedProviderMethodsModule(module);
  }
  
  public synchronized void configure(Binder binder) {
    for (CheckedProviderMethod<?> throwingProviderMethod : getProviderMethods(binder)) {
      throwingProviderMethod.configure(binder);
    }
  }

  List<CheckedProviderMethod<?>> getProviderMethods(Binder binder) {
    List<CheckedProviderMethod<?>> result = Lists.newArrayList();
    for (Class<?> c = delegate.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        CheckedProvides checkedProvides =
          (CheckedProvides)method.getAnnotation(CheckedProvides.class);
        if(checkedProvides != null) {
          result.add(createProviderMethod(binder, method, checkedProvides.value()));
        }
      }
    }
    return result;
  }

  <T> CheckedProviderMethod<T> createProviderMethod(Binder binder, final Method method,
      Class<? extends CheckedProvider> throwingProvider) {
    binder = binder.withSource(method);
    Errors errors = new Errors(method);

    // prepare the parameter providers
    List<Dependency<?>> dependencies = Lists.newArrayList();
    List<Provider<?>> parameterProviders = Lists.newArrayList();
    List<TypeLiteral<?>> parameterTypes = typeLiteral.getParameterTypes(method);
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    for (int i = 0; i < parameterTypes.size(); i++) {
      Key<?> key = getKey(errors, parameterTypes.get(i), method, parameterAnnotations[i]);
      if(key.equals(Key.get(Logger.class))) {
        // If it was a Logger, change the key to be unique & bind it to a
        // provider that provides a logger with a proper name.
        // This solves issue 482 (returning a new anonymous logger on every call exhausts memory)
        Key<Logger> loggerKey = Key.get(Logger.class, UniqueAnnotations.create());
        binder.bind(loggerKey).toProvider(new LogProvider(method));
        key = loggerKey;
      }
      dependencies.add(Dependency.get(key));
      parameterProviders.add(binder.getProvider(key));        
    }

    @SuppressWarnings("unchecked") // Define T as the method's return type.
    TypeLiteral<T> returnType = (TypeLiteral<T>) typeLiteral.getReturnType(method);
    List<TypeLiteral<?>> exceptionTypes = typeLiteral.getExceptionTypes(method);

    Key<T> key = getKey(errors, returnType, method, method.getAnnotations());
    Class<? extends Annotation> scopeAnnotation
        = Annotations.findScopeAnnotation(errors, method.getAnnotations());

    for (Message message : errors.getMessages()) {
      binder.addError(message);
    }

    return new CheckedProviderMethod<T>(key, method, delegate, ImmutableSet.copyOf(dependencies),
        parameterProviders, scopeAnnotation, throwingProvider, exceptionTypes);
  }

  <T> Key<T> getKey(Errors errors, TypeLiteral<T> type, Member member, Annotation[] annotations) {
    Annotation bindingAnnotation = Annotations.findBindingAnnotation(errors, member, annotations);
    return bindingAnnotation == null ? Key.get(type) : Key.get(type, bindingAnnotation);
  }

  @Override public boolean equals(Object o) {
    return o instanceof CheckedProviderMethodsModule
        && ((CheckedProviderMethodsModule) o).delegate == delegate;
  }

  @Override public int hashCode() {
    return delegate.hashCode();
  }
  
  /** A provider that returns a logger based on the method name. */
  private static final class LogProvider implements Provider<Logger> {
    private final String name;
    
    public LogProvider(Method method) {
      this.name = method.getDeclaringClass().getName() + "." + method.getName();
    }
    
    public Logger get() {
      return Logger.getLogger(name);
    }
  }
}
