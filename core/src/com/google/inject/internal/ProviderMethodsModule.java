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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import com.google.inject.spi.ModuleAnnotatedMethodScanner;
import com.google.inject.util.Modules;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Creates bindings to methods annotated with {@literal @}{@link Provides}. Use the scope and
 * binding annotations on the provider method to configure the binding.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class ProviderMethodsModule implements Module {

  private static ModuleAnnotatedMethodScanner PROVIDES_BUILDER =
      new ModuleAnnotatedMethodScanner() {
        @Override
        public <T> Key<T> prepareMethod(
            Binder binder, Annotation annotation, Key<T> key, InjectionPoint injectionPoint) {
          return key;
        }

        @Override
        public Set<? extends Class<? extends Annotation>> annotationClasses() {
          return ImmutableSet.of(Provides.class);
        }
      };

  private final Object delegate;
  private final TypeLiteral<?> typeLiteral;
  private final boolean skipFastClassGeneration;
  private final ModuleAnnotatedMethodScanner scanner;

  private ProviderMethodsModule(Object delegate, boolean skipFastClassGeneration,
      ModuleAnnotatedMethodScanner scanner) {
    this.delegate = checkNotNull(delegate, "delegate");
    this.typeLiteral = TypeLiteral.get(this.delegate.getClass());
    this.skipFastClassGeneration = skipFastClassGeneration;
    this.scanner = scanner;
  }

  /**
   * Returns a module which creates bindings for provider methods from the given module.
   */
  public static Module forModule(Module module) {
    return forObject(module, false, PROVIDES_BUILDER);
  }

  /**
   * Returns a module which creates bindings methods in the module that match the scanner.
   */
  public static Module forModule(Object module, ModuleAnnotatedMethodScanner scanner) {
    return forObject(module, false, scanner);
  }

  /**
   * Returns a module which creates bindings for provider methods from the given object.
   * This is useful notably for <a href="http://code.google.com/p/google-gin/">GIN</a>
   *
   * <p>This will skip bytecode generation for provider methods, since it is assumed that callers
   * are only interested in Module metadata.
   */
  public static Module forObject(Object object) {
    return forObject(object, true, PROVIDES_BUILDER);
  }

  private static Module forObject(Object object, boolean skipFastClassGeneration,
      ModuleAnnotatedMethodScanner scanner) {
    // avoid infinite recursion, since installing a module always installs itself
    if (object instanceof ProviderMethodsModule) {
      return Modules.EMPTY_MODULE;
    }

    return new ProviderMethodsModule(object, skipFastClassGeneration, scanner);
  }

  public Object getDelegateModule() {
    return delegate;
  }

  @Override
  public synchronized void configure(Binder binder) {
    for (ProviderMethod<?> providerMethod : getProviderMethods(binder)) {
      providerMethod.configure(binder);
    }
  }

  public List<ProviderMethod<?>> getProviderMethods(Binder binder) {
    List<ProviderMethod<?>> result = Lists.newArrayList();
    Multimap<Signature, Method> methodsBySignature = HashMultimap.create();
    for (Class<?> c = delegate.getClass(); c != Object.class; c = c.getSuperclass()) {
      for (Method method : c.getDeclaredMethods()) {
        // private/static methods cannot override or be overridden by other methods, so there is no
        // point in indexing them.
        // Skip synthetic methods and bridge methods since java will automatically generate
        // synthetic overrides in some cases where we don't want to generate an error (e.g.
        // increasing visibility of a subclass).
        if (((method.getModifiers() & (Modifier.PRIVATE | Modifier.STATIC)) == 0)
            && !method.isBridge() && !method.isSynthetic()) {
          methodsBySignature.put(new Signature(method), method);
        }
        Optional<Annotation> annotation = isProvider(binder, method);
        if (annotation.isPresent()) {
          result.add(createProviderMethod(binder, method, annotation.get()));
        }
      }
    }
    // we have found all the providers and now need to identify if any were overridden
    // In the worst case this will have O(n^2) in the number of @Provides methods, but that is only
    // assuming that every method is an override, in general it should be very quick.
    for (ProviderMethod<?> provider : result) {
      Method method = provider.getMethod();
      for (Method matchingSignature : methodsBySignature.get(new Signature(method))) {
        // matching signature is in the same class or a super class, therefore method cannot be
        // overridding it.
        if (matchingSignature.getDeclaringClass().isAssignableFrom(method.getDeclaringClass())) {
          continue;
        }
        // now we know matching signature is in a subtype of method.getDeclaringClass()
        if (overrides(matchingSignature, method)) {
          String annotationString = provider.getAnnotation().annotationType() == Provides.class
              ? "@Provides" : "@" + provider.getAnnotation().annotationType().getCanonicalName();
          binder.addError(
              "Overriding " + annotationString + " methods is not allowed."
                  + "\n\t" + annotationString + " method: %s\n\toverridden by: %s",
              method,
              matchingSignature);
          break;
        }
      }
    }
    return result;
  }

  /**
   * Returns true if the method is a provider.
   *
   * Synthetic bridge methods are excluded. Starting with JDK 8, javac copies annotations onto
   * bridge methods (which always have erased signatures).
   */
  private Optional<Annotation> isProvider(Binder binder, Method method) {
    if (method.isBridge() || method.isSynthetic()) {
      return Optional.absent();
    }
    Annotation annotation = null;
    for (Class<? extends Annotation> annotationClass : scanner.annotationClasses()) {
      Annotation foundAnnotation = method.getAnnotation(annotationClass);
      if (foundAnnotation != null) {
        if (annotation != null) {
          binder.addError("More than one annotation claimed by %s on method %s."
              + " Methods can only have one annotation claimed per scanner.",
              scanner, method);
          return Optional.absent();
        }
        annotation = foundAnnotation;
      }
    }
    return Optional.fromNullable(annotation);
  }

  private final class Signature {
    final Class<?>[] parameters;
    final String name;
    final int hashCode;

    Signature(Method method) {
      this.name = method.getName();
      // We need to 'resolve' the parameters against the actual class type in case this method uses
      // type parameters.  This is so we can detect overrides of generic superclass methods where
      // the subclass specifies the type parameter.  javac implements these kinds of overrides via
      // bridge methods, but we don't want to give errors on bridge methods (but rather the target
      // of the bridge).
      List<TypeLiteral<?>> resolvedParameterTypes = typeLiteral.getParameterTypes(method);
      this.parameters = new Class<?>[resolvedParameterTypes.size()];
      int i = 0;
      for (TypeLiteral<?> type : resolvedParameterTypes) {
        parameters[i] = type.getRawType();
      }
      this.hashCode = name.hashCode() + 31 * Arrays.hashCode(parameters);
    }

    @Override public boolean equals(Object obj) {
      if (obj instanceof Signature) {
        Signature other = (Signature) obj;
        return other.name.equals(name) && Arrays.equals(parameters, other.parameters);
      }
      return false;
    }

    @Override public int hashCode() {
      return hashCode;
    }
  }

  /** Returns true if a overrides b, assumes that the signatures match */
  private static boolean overrides(Method a, Method b) {
    // See JLS section 8.4.8.1
    int modifiers = b.getModifiers();
    if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
      return true;
    }
    if (Modifier.isPrivate(modifiers)) {
      return false;
    }
    // b must be package-private
    return a.getDeclaringClass().getPackage().equals(b.getDeclaringClass().getPackage());
  }

  private <T> ProviderMethod<T> createProviderMethod(Binder binder, Method method,
      Annotation annotation) {
    binder = binder.withSource(method);
    Errors errors = new Errors(method);

    // prepare the parameter providers
    InjectionPoint point = InjectionPoint.forMethod(method, typeLiteral);
    List<Dependency<?>> dependencies = point.getDependencies();
    List<Provider<?>> parameterProviders = Lists.newArrayList();
    for (Dependency<?> dependency : point.getDependencies()) {
      parameterProviders.add(binder.getProvider(dependency));
    }

    @SuppressWarnings("unchecked") // Define T as the method's return type.
    TypeLiteral<T> returnType = (TypeLiteral<T>) typeLiteral.getReturnType(method);
    Key<T> key = getKey(errors, returnType, method, method.getAnnotations());
    try {
      key = scanner.prepareMethod(binder, annotation, key, point);
    } catch(Throwable t) {
      binder.addError(t);
    }
    Class<? extends Annotation> scopeAnnotation
        = Annotations.findScopeAnnotation(errors, method.getAnnotations());
    for (Message message : errors.getMessages()) {
      binder.addError(message);
    }
    return ProviderMethod.create(key, method, delegate, ImmutableSet.copyOf(dependencies),
        parameterProviders, scopeAnnotation, skipFastClassGeneration, annotation);
  }

  <T> Key<T> getKey(Errors errors, TypeLiteral<T> type, Member member, Annotation[] annotations) {
    Annotation bindingAnnotation = Annotations.findBindingAnnotation(errors, member, annotations);
    return bindingAnnotation == null ? Key.get(type) : Key.get(type, bindingAnnotation);
  }

  @Override public boolean equals(Object o) {
    return o instanceof ProviderMethodsModule
        && ((ProviderMethodsModule) o).delegate == delegate
        && ((ProviderMethodsModule) o).scanner == scanner;
  }

  @Override public int hashCode() {
    return delegate.hashCode();
  }
}
