/*
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

package com.google.inject.assistedinject;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Iterables.getOnlyElement;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.internal.util.Classes;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Providers;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The newer implementation of factory provider. This implementation uses a child injector to create
 * values.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @author dtm@google.com (Daniel Martin)
 * @author schmitt@google.com (Peter Schmitt)
 * @author sameb@google.com (Sam Berlin)
 */
final class FactoryProvider2<F>
    implements InvocationHandler,
        ProviderWithExtensionVisitor<F>,
        HasDependencies,
        AssistedInjectBinding<F> {

  /** A constant annotation to denote the return value, instead of creating a new one each time. */
  static final Annotation RETURN_ANNOTATION = UniqueAnnotations.create();

  // use the logger under a well-known name, not FactoryProvider2
  static final Logger logger = Logger.getLogger(AssistedInject.class.getName());

  /** if a factory method parameter isn't annotated, it gets this annotation. */
  static final Assisted DEFAULT_ANNOTATION =
      new Assisted() {
        @Override
        public String value() {
          return "";
        }

        @Override
        public Class<? extends Annotation> annotationType() {
          return Assisted.class;
        }

        @Override
        public boolean equals(Object o) {
          return o instanceof Assisted && ((Assisted) o).value().isEmpty();
        }

        @Override
        public int hashCode() {
          return 127 * "value".hashCode() ^ "".hashCode();
        }

        @Override
        public String toString() {
          return "@"
              + Assisted.class.getName()
              + "(value="
              + Annotations.memberValueString("")
              + ")";
        }
      };

  /** All the data necessary to perform an assisted inject. */
  private static class AssistData implements AssistedMethod {
    /** the constructor the implementation is constructed with. */
    final Constructor<?> constructor;
    /** the return type in the factory method that the constructor is bound to. */
    final Key<?> returnType;
    /** the parameters in the factory method associated with this data. */
    final ImmutableList<Key<?>> paramTypes;
    /** the type of the implementation constructed */
    final TypeLiteral<?> implementationType;

    /** All non-assisted dependencies required by this method. */
    final Set<Dependency<?>> dependencies;
    /** The factory method associated with this data */
    final Method factoryMethod;

    /** true if {@link #isValidForOptimizedAssistedInject} returned true. */
    final boolean optimized;
    /** the list of optimized providers, empty if not optimized. */
    final List<ThreadLocalProvider> providers;
    /** used to perform optimized factory creations. */
    volatile Binding<?> cachedBinding; // TODO: volatile necessary?

    AssistData(
        Constructor<?> constructor,
        Key<?> returnType,
        ImmutableList<Key<?>> paramTypes,
        TypeLiteral<?> implementationType,
        Method factoryMethod,
        Set<Dependency<?>> dependencies,
        boolean optimized,
        List<ThreadLocalProvider> providers) {
      this.constructor = constructor;
      this.returnType = returnType;
      this.paramTypes = paramTypes;
      this.implementationType = implementationType;
      this.factoryMethod = factoryMethod;
      this.dependencies = dependencies;
      this.optimized = optimized;
      this.providers = providers;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(getClass())
          .add("ctor", constructor)
          .add("return type", returnType)
          .add("param type", paramTypes)
          .add("implementation type", implementationType)
          .add("dependencies", dependencies)
          .add("factory method", factoryMethod)
          .add("optimized", optimized)
          .add("providers", providers)
          .add("cached binding", cachedBinding)
          .toString();
    }

    @Override
    public Set<Dependency<?>> getDependencies() {
      return dependencies;
    }

    @Override
    public Method getFactoryMethod() {
      return factoryMethod;
    }

    @Override
    public Constructor<?> getImplementationConstructor() {
      return constructor;
    }

    @Override
    public TypeLiteral<?> getImplementationType() {
      return implementationType;
    }
  }

  /** Mapping from method to the data about how the method will be assisted. */
  private final ImmutableMap<Method, AssistData> assistDataByMethod;

  /** Mapping from method to method handle, for generated default methods. */
  private final ImmutableMap<Method, MethodHandle> methodHandleByMethod;

  /** the hosting injector, or null if we haven't been initialized yet */
  private Injector injector;

  /** the factory interface, implemented and provided */
  private final F factory;

  /** The key that this is bound to. */
  private final Key<F> factoryKey;

  /** The binding collector, for equality/hashing purposes. */
  private final BindingCollector collector;

  /**
   * @param factoryKey a key for a Java interface that defines one or more create methods.
   * @param collector binding configuration that maps method return types to implementation types.
   */
  FactoryProvider2(Key<F> factoryKey, BindingCollector collector) {
    this.factoryKey = factoryKey;
    this.collector = collector;

    TypeLiteral<F> factoryType = factoryKey.getTypeLiteral();
    Errors errors = new Errors();

    @SuppressWarnings("unchecked") // we imprecisely treat the class literal of T as a Class<T>
    Class<F> factoryRawType = (Class<F>) (Class<?>) factoryType.getRawType();

    try {
      if (!factoryRawType.isInterface()) {
        throw errors.addMessage("%s must be an interface.", factoryRawType).toException();
      }

      Multimap<String, Method> defaultMethods = HashMultimap.create();
      Multimap<String, Method> otherMethods = HashMultimap.create();
      ImmutableMap.Builder<Method, AssistData> assistDataBuilder = ImmutableMap.builder();
      // TODO: also grab methods from superinterfaces
      for (Method method : factoryRawType.getMethods()) {
        // Skip static methods
        if (Modifier.isStatic(method.getModifiers())) {
          continue;
        }

        // Skip default methods that java8 may have created.
        if (isDefault(method) && (method.isBridge() || method.isSynthetic())) {
          // Even synthetic default methods need the return type validation...
          // unavoidable consequence of javac8. :-(
          validateFactoryReturnType(errors, method.getReturnType(), factoryRawType);
          defaultMethods.put(method.getName(), method);
          continue;
        }
        otherMethods.put(method.getName(), method);

        TypeLiteral<?> returnTypeLiteral = factoryType.getReturnType(method);
        Key<?> returnType;
        try {
          returnType =
              Annotations.getKey(returnTypeLiteral, method, method.getAnnotations(), errors);
        } catch (ConfigurationException ce) {
          // If this was an error due to returnTypeLiteral not being specified, rephrase
          // it as our factory not being specified, so it makes more sense to users.
          if (isTypeNotSpecified(returnTypeLiteral, ce)) {
            throw errors.keyNotFullySpecified(TypeLiteral.get(factoryRawType)).toException();
          } else {
            throw ce;
          }
        }
        validateFactoryReturnType(errors, returnType.getTypeLiteral().getRawType(), factoryRawType);
        List<TypeLiteral<?>> params = factoryType.getParameterTypes(method);
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        int p = 0;
        List<Key<?>> keys = Lists.newArrayList();
        for (TypeLiteral<?> param : params) {
          Key<?> paramKey = Annotations.getKey(param, method, paramAnnotations[p++], errors);
          Class<?> underlylingType = paramKey.getTypeLiteral().getRawType();
          if (underlylingType.equals(Provider.class)
              || underlylingType.equals(javax.inject.Provider.class)) {
            errors.addMessage(
                "A Provider may not be a type in a factory method of an AssistedInject."
                    + "\n  Offending instance is parameter [%s] with key [%s] on method [%s]",
                p, paramKey, method);
          }
          keys.add(assistKey(method, paramKey, errors));
        }
        ImmutableList<Key<?>> immutableParamList = ImmutableList.copyOf(keys);

        // try to match up the method to the constructor
        TypeLiteral<?> implementation = collector.getBindings().get(returnType);
        if (implementation == null) {
          implementation = returnType.getTypeLiteral();
        }
        Class<? extends Annotation> scope =
            Annotations.findScopeAnnotation(errors, implementation.getRawType());
        if (scope != null) {
          errors.addMessage(
              "Found scope annotation [%s] on implementation class "
                  + "[%s] of AssistedInject factory [%s].\nThis is not allowed, please"
                  + " remove the scope annotation.",
              scope, implementation.getRawType(), factoryType);
        }

        InjectionPoint ctorInjectionPoint;
        try {
          ctorInjectionPoint =
              findMatchingConstructorInjectionPoint(
                  method, returnType, implementation, immutableParamList);
        } catch (ErrorsException ee) {
          errors.merge(ee.getErrors());
          continue;
        }

        Constructor<?> constructor = (Constructor<?>) ctorInjectionPoint.getMember();
        List<ThreadLocalProvider> providers = Collections.emptyList();
        Set<Dependency<?>> deps = getDependencies(ctorInjectionPoint, implementation);
        boolean optimized = false;
        // Now go through all dependencies of the implementation and see if it is OK to
        // use an optimized form of assistedinject2.  The optimized form requires that
        // all injections directly inject the object itself (and not a Provider of the object,
        // or an Injector), because it caches a single child injector and mutates the Provider
        // of the arguments in a ThreadLocal.
        if (isValidForOptimizedAssistedInject(deps, implementation.getRawType(), factoryType)) {
          ImmutableList.Builder<ThreadLocalProvider> providerListBuilder = ImmutableList.builder();
          for (int i = 0; i < params.size(); i++) {
            providerListBuilder.add(new ThreadLocalProvider());
          }
          providers = providerListBuilder.build();
          optimized = true;
        }

        AssistData data =
            new AssistData(
                constructor,
                returnType,
                immutableParamList,
                implementation,
                method,
                removeAssistedDeps(deps),
                optimized,
                providers);
        assistDataBuilder.put(method, data);
      }

      factory =
          factoryRawType.cast(
              Proxy.newProxyInstance(
                  factoryRawType.getClassLoader(), new Class<?>[] {factoryRawType}, this));

      // Now go back through default methods. Try to use MethodHandles to make things
      // work.  If that doesn't work, fallback to trying to find compatible method
      // signatures.
      Map<Method, AssistData> dataSoFar = assistDataBuilder.build();
      ImmutableMap.Builder<Method, MethodHandle> methodHandleBuilder = ImmutableMap.builder();
      for (Map.Entry<String, Method> entry : defaultMethods.entries()) {
        Method defaultMethod = entry.getValue();
        MethodHandle handle = createMethodHandle(defaultMethod, factory);
        if (handle != null) {
          methodHandleBuilder.put(defaultMethod, handle);
        } else {
          boolean foundMatch = false;
          for (Method otherMethod : otherMethods.get(defaultMethod.getName())) {
            if (dataSoFar.containsKey(otherMethod) && isCompatible(defaultMethod, otherMethod)) {
              if (foundMatch) {
                errors.addMessage(
                    "Generated default method %s with parameters %s is"
                        + " signature-compatible with more than one non-default method."
                        + " Unable to create factory. As a workaround, remove the override"
                        + " so javac stops generating a default method.",
                    defaultMethod, Arrays.asList(defaultMethod.getParameterTypes()));
              } else {
                assistDataBuilder.put(defaultMethod, dataSoFar.get(otherMethod));
                foundMatch = true;
              }
            }
          }
          if (!foundMatch) {
            throw new IllegalStateException("Can't find method compatible with: " + defaultMethod);
          }
        }
      }

      // If we generated any errors (from finding matching constructors, for instance), throw an
      // exception.
      if (errors.hasErrors()) {
        throw errors.toException();
      }

      assistDataByMethod = assistDataBuilder.build();
      methodHandleByMethod = methodHandleBuilder.build();
    } catch (ErrorsException e) {
      throw new ConfigurationException(e.getErrors().getMessages());
    }
  }

  static boolean isDefault(Method method) {
    // Per the javadoc, default methods are non-abstract, public, non-static.
    // They're also in interfaces, but we can guarantee that already since we only act
    // on interfaces.
    return (method.getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC))
        == Modifier.PUBLIC;
  }

  private boolean isCompatible(Method src, Method dst) {
    if (!src.getReturnType().isAssignableFrom(dst.getReturnType())) {
      return false;
    }
    Class<?>[] srcParams = src.getParameterTypes();
    Class<?>[] dstParams = dst.getParameterTypes();
    if (srcParams.length != dstParams.length) {
      return false;
    }
    for (int i = 0; i < srcParams.length; i++) {
      if (!srcParams[i].isAssignableFrom(dstParams[i])) {
        return false;
      }
    }
    return true;
  }

  @Override
  public F get() {
    return factory;
  }

  @Override
  public Set<Dependency<?>> getDependencies() {
    Set<Dependency<?>> combinedDeps = new HashSet<>();
    for (AssistData data : assistDataByMethod.values()) {
      combinedDeps.addAll(data.dependencies);
    }
    return ImmutableSet.copyOf(combinedDeps);
  }

  @Override
  public Key<F> getKey() {
    return factoryKey;
  }

  // Safe cast because values are typed to AssistedData, which is an AssistedMethod, and
  // the collection is immutable.
  @Override
  @SuppressWarnings("unchecked")
  public Collection<AssistedMethod> getAssistedMethods() {
    return (Collection<AssistedMethod>) (Collection<?>) assistDataByMethod.values();
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T, V> V acceptExtensionVisitor(
      BindingTargetVisitor<T, V> visitor, ProviderInstanceBinding<? extends T> binding) {
    if (visitor instanceof AssistedInjectTargetVisitor) {
      return ((AssistedInjectTargetVisitor<T, V>) visitor).visit((AssistedInjectBinding<T>) this);
    }
    return visitor.visit(binding);
  }

  private void validateFactoryReturnType(Errors errors, Class<?> returnType, Class<?> factoryType) {
    if (Modifier.isPublic(factoryType.getModifiers())
        && !Modifier.isPublic(returnType.getModifiers())) {
      errors.addMessage(
          "%s is public, but has a method that returns a non-public type: %s. "
              + "Due to limitations with java.lang.reflect.Proxy, this is not allowed. "
              + "Please either make the factory non-public or the return type public.",
          factoryType, returnType);
    }
  }

  /**
   * Returns true if the ConfigurationException is due to an error of TypeLiteral not being fully
   * specified.
   */
  private boolean isTypeNotSpecified(TypeLiteral<?> typeLiteral, ConfigurationException ce) {
    Collection<Message> messages = ce.getErrorMessages();
    if (messages.size() == 1) {
      Message msg =
          Iterables.getOnlyElement(new Errors().keyNotFullySpecified(typeLiteral).getMessages());
      return msg.getMessage().equals(Iterables.getOnlyElement(messages).getMessage());
    } else {
      return false;
    }
  }

  /**
   * Finds a constructor suitable for the method. If the implementation contained any constructors
   * marked with {@link AssistedInject}, this requires all {@link Assisted} parameters to exactly
   * match the parameters (in any order) listed in the method. Otherwise, if no {@link
   * AssistedInject} constructors exist, this will default to looking for an {@literal @}{@link
   * Inject} constructor.
   */
  private <T> InjectionPoint findMatchingConstructorInjectionPoint(
      Method method, Key<?> returnType, TypeLiteral<T> implementation, List<Key<?>> paramList)
      throws ErrorsException {
    Errors errors = new Errors(method);
    if (returnType.getTypeLiteral().equals(implementation)) {
      errors = errors.withSource(implementation);
    } else {
      errors = errors.withSource(returnType).withSource(implementation);
    }

    Class<?> rawType = implementation.getRawType();
    if (Modifier.isInterface(rawType.getModifiers())) {
      errors.addMessage(
          "%s is an interface, not a concrete class.  Unable to create AssistedInject factory.",
          implementation);
      throw errors.toException();
    } else if (Modifier.isAbstract(rawType.getModifiers())) {
      errors.addMessage(
          "%s is abstract, not a concrete class.  Unable to create AssistedInject factory.",
          implementation);
      throw errors.toException();
    } else if (Classes.isInnerClass(rawType)) {
      errors.cannotInjectInnerClass(rawType);
      throw errors.toException();
    }

    Constructor<?> matchingConstructor = null;
    boolean anyAssistedInjectConstructors = false;
    // Look for AssistedInject constructors...
    for (Constructor<?> constructor : rawType.getDeclaredConstructors()) {
      if (constructor.isAnnotationPresent(AssistedInject.class)) {
        anyAssistedInjectConstructors = true;
        if (constructorHasMatchingParams(implementation, constructor, paramList, errors)) {
          if (matchingConstructor != null) {
            errors.addMessage(
                "%s has more than one constructor annotated with @AssistedInject"
                    + " that matches the parameters in method %s.  Unable to create "
                    + "AssistedInject factory.",
                implementation, method);
            throw errors.toException();
          } else {
            matchingConstructor = constructor;
          }
        }
      }
    }

    if (!anyAssistedInjectConstructors) {
      // If none existed, use @Inject or a no-arg constructor.
      try {
        return InjectionPoint.forConstructorOf(implementation);
      } catch (ConfigurationException e) {
        errors.merge(e.getErrorMessages());
        throw errors.toException();
      }
    } else {
      // Otherwise, use it or fail with a good error message.
      if (matchingConstructor != null) {
        // safe because we got the constructor from this implementation.
        @SuppressWarnings("unchecked")
        InjectionPoint ip =
            InjectionPoint.forConstructor(
                (Constructor<? super T>) matchingConstructor, implementation);
        return ip;
      } else {
        errors.addMessage(
            "%s has @AssistedInject constructors, but none of them match the"
                + " parameters in method %s.  Unable to create AssistedInject factory.",
            implementation, method);
        throw errors.toException();
      }
    }
  }

  /**
   * Matching logic for constructors annotated with AssistedInject. This returns true if and only if
   * all @Assisted parameters in the constructor exactly match (in any order) all @Assisted
   * parameters the method's parameter.
   */
  private boolean constructorHasMatchingParams(
      TypeLiteral<?> type, Constructor<?> constructor, List<Key<?>> paramList, Errors errors)
      throws ErrorsException {
    List<TypeLiteral<?>> params = type.getParameterTypes(constructor);
    Annotation[][] paramAnnotations = constructor.getParameterAnnotations();
    int p = 0;
    List<Key<?>> constructorKeys = Lists.newArrayList();
    for (TypeLiteral<?> param : params) {
      Key<?> paramKey = Annotations.getKey(param, constructor, paramAnnotations[p++], errors);
      constructorKeys.add(paramKey);
    }
    // Require that every key exist in the constructor to match up exactly.
    for (Key<?> key : paramList) {
      // If it didn't exist in the constructor set, we can't use it.
      if (!constructorKeys.remove(key)) {
        return false;
      }
    }
    // If any keys remain and their annotation is Assisted, we can't use it.
    for (Key<?> key : constructorKeys) {
      if (key.getAnnotationType() == Assisted.class) {
        return false;
      }
    }
    // All @Assisted params match up to the method's parameters.
    return true;
  }

  /** Calculates all dependencies required by the implementation and constructor. */
  private Set<Dependency<?>> getDependencies(
      InjectionPoint ctorPoint, TypeLiteral<?> implementation) {
    ImmutableSet.Builder<Dependency<?>> builder = ImmutableSet.builder();
    builder.addAll(ctorPoint.getDependencies());
    if (!implementation.getRawType().isInterface()) {
      for (InjectionPoint ip : InjectionPoint.forInstanceMethodsAndFields(implementation)) {
        builder.addAll(ip.getDependencies());
      }
    }
    return builder.build();
  }

  /** Return all non-assisted dependencies. */
  private Set<Dependency<?>> removeAssistedDeps(Set<Dependency<?>> deps) {
    ImmutableSet.Builder<Dependency<?>> builder = ImmutableSet.builder();
    for (Dependency<?> dep : deps) {
      Class<?> annotationType = dep.getKey().getAnnotationType();
      if (annotationType == null || !annotationType.equals(Assisted.class)) {
        builder.add(dep);
      }
    }
    return builder.build();
  }

  /**
   * Returns true if all dependencies are suitable for the optimized version of AssistedInject. The
   * optimized version caches the binding & uses a ThreadLocal Provider, so can only be applied if
   * the assisted bindings are immediately provided. This looks for hints that the values may be
   * lazily retrieved, by looking for injections of Injector or a Provider for the assisted values.
   */
  private boolean isValidForOptimizedAssistedInject(
      Set<Dependency<?>> dependencies, Class<?> implementation, TypeLiteral<?> factoryType) {
    Set<Dependency<?>> badDeps = null; // optimization: create lazily
    for (Dependency<?> dep : dependencies) {
      if (isInjectorOrAssistedProvider(dep)) {
        if (badDeps == null) {
          badDeps = Sets.newHashSet();
        }
        badDeps.add(dep);
      }
    }
    if (badDeps != null && !badDeps.isEmpty()) {
      logger.log(
          Level.WARNING,
          "AssistedInject factory {0} will be slow "
              + "because {1} has assisted Provider dependencies or injects the Injector. "
              + "Stop injecting @Assisted Provider<T> (instead use @Assisted T) "
              + "or Injector to speed things up. (It will be a ~6500% speed bump!)  "
              + "The exact offending deps are: {2}",
          new Object[] {factoryType, implementation, badDeps});
      return false;
    }
    return true;
  }

  /**
   * Returns true if the dependency is for {@link Injector} or if the dependency is a {@link
   * Provider} for a parameter that is {@literal @}{@link Assisted}.
   */
  private boolean isInjectorOrAssistedProvider(Dependency<?> dependency) {
    Class<?> annotationType = dependency.getKey().getAnnotationType();
    if (annotationType != null && annotationType.equals(Assisted.class)) { // If it's assisted..
      if (dependency
          .getKey()
          .getTypeLiteral()
          .getRawType()
          .equals(Provider.class)) { // And a Provider...
        return true;
      }
    } else if (dependency
        .getKey()
        .getTypeLiteral()
        .getRawType()
        .equals(Injector.class)) { // If it's the Injector...
      return true;
    }
    return false;
  }

  /**
   * Returns a key similar to {@code key}, but with an {@literal @}Assisted binding annotation. This
   * fails if another binding annotation is clobbered in the process. If the key already has the
   * {@literal @}Assisted annotation, it is returned as-is to preserve any String value.
   */
  private <T> Key<T> assistKey(Method method, Key<T> key, Errors errors) throws ErrorsException {
    if (key.getAnnotationType() == null) {
      return key.withAnnotation(DEFAULT_ANNOTATION);
    } else if (key.getAnnotationType() == Assisted.class) {
      return key;
    } else {
      errors
          .withSource(method)
          .addMessage(
              "Only @Assisted is allowed for factory parameters, but found @%s",
              key.getAnnotationType());
      throw errors.toException();
    }
  }

  /**
   * At injector-creation time, we initialize the invocation handler. At this time we make sure all
   * factory methods will be able to build the target types.
   */
  @Inject
  @Toolable
  void initialize(Injector injector) {
    if (this.injector != null) {
      throw new ConfigurationException(
          ImmutableList.of(
              new Message(
                  FactoryProvider2.class,
                  "Factories.create() factories may only be used in one Injector!")));
    }

    this.injector = injector;

    for (Map.Entry<Method, AssistData> entry : assistDataByMethod.entrySet()) {
      Method method = entry.getKey();
      AssistData data = entry.getValue();
      Object[] args;
      if (!data.optimized) {
        args = new Object[method.getParameterTypes().length];
        Arrays.fill(args, "dummy object for validating Factories");
      } else {
        args = null; // won't be used -- instead will bind to data.providers.
      }
      getBindingFromNewInjector(
          method, args, data); // throws if the binding isn't properly configured
    }
  }

  /**
   * Creates a child injector that binds the args, and returns the binding for the method's result.
   */
  public Binding<?> getBindingFromNewInjector(
      final Method method, final Object[] args, final AssistData data) {
    checkState(
        injector != null,
        "Factories.create() factories cannot be used until they're initialized by Guice.");

    final Key<?> returnType = data.returnType;

    // We ignore any pre-existing binding annotation.
    final Key<?> returnKey = Key.get(returnType.getTypeLiteral(), RETURN_ANNOTATION);

    Module assistedModule =
        new AbstractModule() {
          @Override
          @SuppressWarnings({
            "unchecked",
            "rawtypes"
          }) // raw keys are necessary for the args array and return value
          protected void configure() {
            Binder binder = binder().withSource(method);

            int p = 0;
            if (!data.optimized) {
              for (Key<?> paramKey : data.paramTypes) {
                // Wrap in a Provider to cover null, and to prevent Guice from injecting the
                // parameter
                binder.bind((Key) paramKey).toProvider(Providers.of(args[p++]));
              }
            } else {
              for (Key<?> paramKey : data.paramTypes) {
                // Bind to our ThreadLocalProviders.
                binder.bind((Key) paramKey).toProvider(data.providers.get(p++));
              }
            }

            Constructor constructor = data.constructor;
            // Constructor *should* always be non-null here,
            // but if it isn't, we'll end up throwing a fairly good error
            // message for the user.
            if (constructor != null) {
              binder
                  .bind(returnKey)
                  .toConstructor(constructor, (TypeLiteral) data.implementationType)
                  .in(Scopes.NO_SCOPE); // make sure we erase any scope on the implementation type
            }
          }
        };

    Injector forCreate = injector.createChildInjector(assistedModule);
    Binding<?> binding = forCreate.getBinding(returnKey);
    // If we have providers cached in data, cache the binding for future optimizations.
    if (data.optimized) {
      data.cachedBinding = binding;
    }
    return binding;
  }

  /**
   * When a factory method is invoked, we create a child injector that binds all parameters, then
   * use that to get an instance of the return type.
   */
  @Override
  public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
    // If we setup a method handle earlier for this method, call it.
    // This is necessary for default methods that java8 creates, so we
    // can call the default method implementation (and not our proxied version of it).
    if (methodHandleByMethod.containsKey(method)) {
      return methodHandleByMethod.get(method).invokeWithArguments(args);
    }

    if (method.getDeclaringClass().equals(Object.class)) {
      if ("equals".equals(method.getName())) {
        return proxy == args[0];
      } else if ("hashCode".equals(method.getName())) {
        return System.identityHashCode(proxy);
      } else {
        return method.invoke(this, args);
      }
    }

    AssistData data = assistDataByMethod.get(method);
    checkState(data != null, "No data for method: %s", method);
    Provider<?> provider;
    if (data.cachedBinding != null) { // Try to get optimized form...
      provider = data.cachedBinding.getProvider();
    } else {
      provider = getBindingFromNewInjector(method, args, data).getProvider();
    }
    try {
      int p = 0;
      for (ThreadLocalProvider tlp : data.providers) {
        tlp.set(args[p++]);
      }
      return provider.get();
    } catch (ProvisionException e) {
      // if this is an exception declared by the factory method, throw it as-is
      if (e.getErrorMessages().size() == 1) {
        Message onlyError = getOnlyElement(e.getErrorMessages());
        Throwable cause = onlyError.getCause();
        if (cause != null && canRethrow(method, cause)) {
          throw cause;
        }
      }
      throw e;
    } finally {
      for (ThreadLocalProvider tlp : data.providers) {
        tlp.remove();
      }
    }
  }

  @Override
  public String toString() {
    return factory.getClass().getInterfaces()[0].getName();
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(factoryKey, collector);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FactoryProvider2)) {
      return false;
    }
    FactoryProvider2<?> other = (FactoryProvider2<?>) obj;
    return factoryKey.equals(other.factoryKey) && Objects.equal(collector, other.collector);
  }

  /** Returns true if {@code thrown} can be thrown by {@code invoked} without wrapping. */
  static boolean canRethrow(Method invoked, Throwable thrown) {
    if (thrown instanceof Error || thrown instanceof RuntimeException) {
      return true;
    }

    for (Class<?> declared : invoked.getExceptionTypes()) {
      if (declared.isInstance(thrown)) {
        return true;
      }
    }

    return false;
  }

  // not <T> because we'll never know and this is easier than suppressing warnings.
  private static class ThreadLocalProvider extends ThreadLocal<Object> implements Provider<Object> {
    @Override
    protected Object initialValue() {
      throw new IllegalStateException(
          "Cannot use optimized @Assisted provider outside the scope of the constructor."
              + " (This should never happen.  If it does, please report it.)");
    }
  }

  // Note: this isn't a public API, but we need to use it in order to call default methods on (or
  // with) non-public types.  If it doesn't exist, the code falls back to a less precise check.
  private static final Constructor<MethodHandles.Lookup> methodHandlesLookupCxtor =
      findMethodHandlesLookupCxtor();

  private static Constructor<MethodHandles.Lookup> findMethodHandlesLookupCxtor() {
    try {
      Constructor<MethodHandles.Lookup> cxtor =
          MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
      cxtor.setAccessible(true);
      return cxtor;
    } catch (ReflectiveOperationException ignored) {
      // Ignore, the code falls back to a less-precise check if we can't create method handles.
      return null;
    }
  }

  private static MethodHandle createMethodHandle(Method method, Object proxy) {
    if (methodHandlesLookupCxtor == null) {
      return null;
    }
    Class<?> declaringClass = method.getDeclaringClass();
    int allModes =
        Modifier.PRIVATE | Modifier.STATIC /* package */ | Modifier.PUBLIC | Modifier.PROTECTED;
    try {
      MethodHandles.Lookup lookup = methodHandlesLookupCxtor.newInstance(declaringClass, allModes);
      method.setAccessible(true);
      return lookup.unreflectSpecial(method, declaringClass).bindTo(proxy);
    } catch (ReflectiveOperationException roe) {
      throw new RuntimeException("Unable to access method: " + method, roe);
    }
  }
}
