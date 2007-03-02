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

import com.google.inject.spi.SourceProviders;
import com.google.inject.util.GuiceFastClass;
import com.google.inject.util.Objects;
import com.google.inject.util.ReferenceCache;
import com.google.inject.util.StackTraceElements;
import com.google.inject.util.Strings;
import com.google.inject.util.ToStringBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

/**
 * Default {@link Injector} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see BinderImpl
 */
class InjectorImpl implements Injector {

  /**
   * Maps between primitive types and their wrappers and vice versa.
   */
  private static final Map<Class<?>, Class<?>> PRIMITIVE_COUNTERPARTS;
  static {
    Map<Class<?>, Class<?>> primitiveToWrapper =
        new HashMap<Class<?>, Class<?>>() {{
          put(int.class, Integer.class);
          put(long.class, Long.class);
          put(boolean.class, Boolean.class);
          put(byte.class, Byte.class);
          put(short.class, Short.class);
          put(float.class, Float.class);
          put(double.class, Double.class);
          put(char.class, Character.class);
        }};

    Map<Class<?>, Class<?>> counterparts = new HashMap<Class<?>, Class<?>>();
    for (Map.Entry<Class<?>, Class<?>> entry : primitiveToWrapper.entrySet()) {
      Class<?> key = entry.getKey();
      Class<?> value = entry.getValue();
      counterparts.put(key, value);
      counterparts.put(value, key);
    }

    PRIMITIVE_COUNTERPARTS = Collections.unmodifiableMap(counterparts);
  }

  private static final Map<Class<?>, Converter<?>> PRIMITIVE_CONVERTERS
      = new PrimitiveConverters();

  final ConstructionProxyFactory constructionProxyFactory;
  final Map<Key<?>, BindingImpl<?>> bindings;
  final BindingsMultimap bindingsMultimap = new BindingsMultimap();
  final Map<Class<? extends Annotation>, Scope> scopes;

  ErrorHandler errorHandler = new InvalidErrorHandler();
  Object defaultSource = SourceProviders.UNKNOWN_SOURCE;

  InjectorImpl(ConstructionProxyFactory constructionProxyFactory,
      Map<Key<?>, BindingImpl<?>> bindings,
      Map<Class<? extends Annotation>, Scope> scopes) {
    this.constructionProxyFactory = constructionProxyFactory;
    this.bindings = bindings;
    this.scopes = scopes;
  }

  /**
   * Indexes bindings by type.
   */
  void index() {
    for (BindingImpl<?> binding : bindings.values()) {
      index(binding);
    }
  }

  <T> void index(BindingImpl<T> binding) {
    bindingsMultimap.put(binding.getKey().getTypeLiteral(), binding);
  }

  // not test-covered
  public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
    return Collections.<Binding<T>>unmodifiableList(
        bindingsMultimap.getAll(type));
  }

  // not test-covered
  <T> List<String> getNamesOfBindingAnnotations(TypeLiteral<T> type) {
    List<String> names = new ArrayList<String>();
    for (Binding<T> binding : findBindingsByType(type)) {
      Key<T> key = binding.getKey();
      if (!key.hasAnnotationType()) {
        names.add("[no annotation]");
      } else {
        names.add(key.getAnnotationName());
      }
    }
    return names;
  }

  /**
   * This is only used during Injector building.
   */
  void withDefaultSource(Object defaultSource, Runnable runnable) {
    Object previous = this.defaultSource;
    this.defaultSource = defaultSource;
    try {
      runnable.run();
    }
    finally {
      this.defaultSource = previous;
    }
  }

  void setErrorHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  <T> InternalFactory<? extends T> getInternalFactory(
      final Member member, Key<T> key) {
    // TODO: Clean up unchecked type warnings.

    // Do we have a factory for the specified type and name?
    BindingImpl<T> binding = getBinding(key);
    if (binding != null) {
      return binding.getInternalFactory();
    }

    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    // Handle cases where T is a Provider<?>.
    if (rawType.equals(Provider.class)) {
      Type providerType = key.getTypeLiteral().getType();
      if (!(providerType instanceof ParameterizedType)) {
        // Raw Provider.
        return null;
      }
      Type entryType
          = ((ParameterizedType) providerType).getActualTypeArguments()[0];

      try {
        final Provider<?> provider = getProvider(key.ofType(entryType));
        return new InternalFactory<T>() {
          @SuppressWarnings("unchecked")
          public T get(InternalContext context) {
            return (T) provider;
          }
        };
      }
      catch (ConfigurationException e) {
        // Look for a factory bound to a key without annotation attributes if
        // necessary.
        if (key.hasAttributes()) {
          return getInternalFactory(member, key.withoutAttributes());
        }

        // End of the road.
        ErrorMessages.handleMissingBinding(errorHandler, member, key,
            getNamesOfBindingAnnotations(key.getTypeLiteral()));
        return invalidFactory();
      }
    }

    // Auto[un]box primitives.
    Class<?> primitiveCounterpart
        = PRIMITIVE_COUNTERPARTS.get(rawType);
    if (primitiveCounterpart != null) {
      BindingImpl<?> counterpartBinding
          = getBinding(key.ofType(primitiveCounterpart));
      if (counterpartBinding != null) {
        return (InternalFactory<? extends T>)
            counterpartBinding.getInternalFactory();
      }
    }

    // TODO: Should we try to convert from a String first, or should we look
    // for a binding to the annotation type sans attributes? Right now, we
    // convert from a String.

    // Can we convert from a String constant?
    Key<String> stringKey = key.ofType(String.class);
    BindingImpl<String> stringBinding = getBinding(stringKey);
    if (stringBinding != null && stringBinding.isConstant()) {
      // We don't need do pass in an InternalContext because we know this is
      // a ConstantFactory which will not use it.
      String value = stringBinding.getInternalFactory().get(null);

      // TODO: Generalize everything below here and enable users to plug in
      // their own converters.

      // Do we need a primitive?
      Converter<T> converter = (Converter<T>) PRIMITIVE_CONVERTERS.get(rawType);
      if (converter != null) {
        try {
          T t = converter.convert(member, key, value);
          return new ConstantFactory<T>(t);
        }
        catch (ConstantConversionException e) {
          return handleConstantConversionError(
              member, stringBinding, rawType, e);
        }
      }

      // Do we need an enum?
      if (Enum.class.isAssignableFrom(rawType)) {
        T t;
        try {
          t = (T) Enum.valueOf((Class) rawType, value);
        }
        catch (IllegalArgumentException e) {
          return handleConstantConversionError(
              member, stringBinding, rawType, e);
        }
        return new ConstantFactory<T>(t);
      }

      // Do we need a class?
      if (rawType == Class.class) {
        try {
          // TODO: Make sure we use the right classloader.
          return new ConstantFactory<T>((T) Class.forName(value));
        }
        catch (ClassNotFoundException e) {
          return handleConstantConversionError(
              member, stringBinding, rawType, e);
        }
      }
    }

    // Don't try to inject primitives, arrays, or enums.
    int modifiers = rawType.getModifiers();
    if (rawType.isArray() || rawType.isEnum() || rawType.isPrimitive()) {
      // Look for a factory bound to a key without annotation attributes if
      // necessary.
      if (key.hasAttributes()) {
        return getInternalFactory(member, key.withoutAttributes());
      }

      return null;
    }

    // We don't want to implicitly inject a member if we have a binding
    // annotation.
    if (key.hasAnnotationType()) {
      // Look for a factory bound to a key without annotation attributes if
      // necessary.
      if (key.hasAttributes()) {
        return getInternalFactory(member, key.withoutAttributes());
      }

      return null;
    }

    // Last resort: inject the type itself.
    if (member != null) {
      // If we're injecting into a member, include it in the error messages.
      final ErrorHandler previous = this.errorHandler;
      this.errorHandler = new AbstractErrorHandler() {
        public void handle(Object source, String message) {
          previous.handle(source, "Error while injecting at "
              + StackTraceElements.forMember(member) + ": " + message);
        }
      };
      try {
        // note: intelliJ thinks this cast is superfluous, but is it?
        return (InternalFactory<? extends T>) getImplicitBinding(member,
            rawType, null);
      }
      finally {
        this.errorHandler = previous;
      }
    }
    // note: intelliJ thinks this cast is superfluous, but is it?
    return (InternalFactory<? extends T>) getImplicitBinding(member, rawType,
        null);
  }

  private <T> InternalFactory<T> handleConstantConversionError(
      Member member, Binding<String> stringBinding, Class<?> rawType,
      Exception e) {
    errorHandler.handle(
        StackTraceElements.forMember(member),
        ErrorMessages.CONSTANT_CONVERSION_ERROR,
        stringBinding.getSource(),
        rawType,
        e.getMessage());
    return invalidFactory();
  }

  /**
   * Field and method injectors.
   */
  final Map<Class<?>, List<SingleMemberInjector>> injectors
      = new ReferenceCache<Class<?>, List<SingleMemberInjector>>() {
    protected List<SingleMemberInjector> create(Class<?> key) {
      List<SingleMemberInjector> injectors
          = new ArrayList<SingleMemberInjector>();
      addInjectors(key, injectors);
      return injectors;
    }
  };

  /**
   * Recursively adds injectors for fields and methods from the given class to
   * the given list. Injects parent classes before sub classes.
   */
  void addInjectors(Class clazz, List<SingleMemberInjector> injectors) {
    if (clazz == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectors(clazz.getSuperclass(), injectors);

    // TODO (crazybob): Filter out overridden members.
    addSingleInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
    addSingleInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
  }

  void addSingleInjectorsForMethods(Method[] methods, boolean statics,
      List<SingleMemberInjector> injectors) {
    addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
        new SingleInjectorFactory<Method>() {
          public SingleMemberInjector create(InjectorImpl injector,
              Method method) throws MissingDependencyException {
            return new SingleMethodInjector(injector, method);
          }
        });
  }

  void addSingleInjectorsForFields(Field[] fields, boolean statics,
      List<SingleMemberInjector> injectors) {
    addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
        new SingleInjectorFactory<Field>() {
          public SingleMemberInjector create(InjectorImpl injector,
              Field field) throws MissingDependencyException {
            return new SingleFieldInjector(injector, field);
          }
        });
  }

  <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      List<M> members, boolean statics, List<SingleMemberInjector> injectors,
      SingleInjectorFactory<M> injectorFactory) {
    for (M member : members) {
      if (isStatic(member) == statics) {
        Inject inject = member.getAnnotation(Inject.class);
        if (inject != null) {
          try {
            injectors.add(injectorFactory.create(this, member));
          }
          catch (MissingDependencyException e) {
            if (!inject.optional()) {
              // TODO: Report errors for more than one parameter per member.
              e.handle(errorHandler);
            }
          }
        }
      }
    }
  }

  Map<Key<?>, BindingImpl<?>> internalBindings() {
    return bindings;
  }

  // not test-covered
  public Map<Key<?>, Binding<?>> getBindings() {
    return Collections.<Key<?>, Binding<?>>unmodifiableMap(bindings);
  }

  @SuppressWarnings("unchecked")
  public <T> BindingImpl<T> getBinding(Key<T> key) {
    return (BindingImpl<T>) bindings.get(key);
  }

  interface SingleInjectorFactory<M extends Member & AnnotatedElement> {
    SingleMemberInjector create(InjectorImpl injector, M member)
        throws MissingDependencyException;
  }

  private boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private static class BindingsMultimap {
    private final Map<TypeLiteral<?>, List<? extends BindingImpl<?>>> map
        = new HashMap<TypeLiteral<?>, List<? extends BindingImpl<?>>>();

    public <T> void put(TypeLiteral<T> type, BindingImpl<T> binding) {
      List<BindingImpl<T>> bindingsForThisType = getFromMap(type);
      if (bindingsForThisType == null) {
        bindingsForThisType = new ArrayList<BindingImpl<T>>();
        // We only put matching entries into the map
        map.put(type, bindingsForThisType);
      }
      bindingsForThisType.add(binding);
    }

    public <T> List<BindingImpl<T>> getAll(TypeLiteral<T> type) {
      List<BindingImpl<T>> list = getFromMap(type);
      return list == null ? Collections.<BindingImpl<T>>emptyList() : list;
    }

    // safe because we only put matching entries into the map
    @SuppressWarnings("unchecked")
    private <T> List<BindingImpl<T>> getFromMap(TypeLiteral<T> type) {
      return (List<BindingImpl<T>>) map.get(type);
    }
  }

  class SingleFieldInjector implements SingleMemberInjector {

    final Field field;
    final InternalFactory<?> factory;
    final ExternalContext<?> externalContext;

    public SingleFieldInjector(InjectorImpl injector, Field field)
        throws MissingDependencyException {
      this.field = field;

      // Ewwwww...
      field.setAccessible(true);

      Key<?> key = Key.get(
          field.getGenericType(), field, field.getAnnotations(), errorHandler);
      factory = injector.getInternalFactory(field, key);
      if (factory == null) {
        throw new MissingDependencyException(key, field);
      }

      this.externalContext = ExternalContext.newInstance(field, key, injector);
    }

    public void inject(InternalContext context, Object o) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        Object value = factory.get(context);
        if (value == null) {
          throw new AssertionError(); // we should have prevented this
        }
        field.set(o, value);
      }
      catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
      catch (ConfigurationException e) {
        throw e;
      }
      catch (Throwable throwable) {
        throw new ProvisionException(externalContext, throwable);
      }
      finally {
        context.setExternalContext(previous);
      }
    }
  }

  /**
   * Gets parameter injectors.
   *
   * @param member to which the parameters belong
   * @param annotations on the parameters
   * @param parameterTypes parameter types
   * @return injections
   */
  <M extends AccessibleObject & Member>
  SingleParameterInjector<?>[] getParametersInjectors(M member,
      Annotation[][] annotations, Type[] parameterTypes)
      throws MissingDependencyException {
    SingleParameterInjector<?>[] parameterInjectors
        = new SingleParameterInjector<?>[parameterTypes.length];
    Iterator<Annotation[]> annotationsIterator
        = Arrays.asList(annotations).iterator();
    int index = 0;
    for (Type parameterType : parameterTypes) {
      Annotation[] parameterAnnotations = annotationsIterator.next();
      Key<?> key = Key.get(
          parameterType, member, parameterAnnotations, errorHandler);
      parameterInjectors[index] = createParameterInjector(key, member, index);
      index++;
    }

    return parameterInjectors;
  }

  <T> SingleParameterInjector<T> createParameterInjector(
      Key<T> key, Member member, int index) throws MissingDependencyException {
    InternalFactory<? extends T> factory = getInternalFactory(member, key);
    if (factory == null) {
      throw new MissingDependencyException(key, member);
    }

    ExternalContext<T> externalContext
        = ExternalContext.newInstance(member, index, key, this);
    return new SingleParameterInjector<T>(externalContext, factory);
  }

  static class SingleMethodInjector implements SingleMemberInjector {

    final MethodInvoker methodInvoker;
    final SingleParameterInjector<?>[] parameterInjectors;

    public SingleMethodInjector(InjectorImpl injector, final Method method)
        throws MissingDependencyException {
      // We can't use FastMethod if the method is private.
      if (Modifier.isPrivate(method.getModifiers())
          || Modifier.isProtected(method.getModifiers())) {
        method.setAccessible(true);
        this.methodInvoker = new MethodInvoker() {
          public Object invoke(Object target, Object... parameters) throws
              IllegalAccessException, InvocationTargetException {
            Objects.assertNoNulls(parameters);
            return method.invoke(target, parameters);
          }
        };
      }
      else {
        FastClass fastClass = GuiceFastClass.create(method.getDeclaringClass());
        final FastMethod fastMethod = fastClass.getMethod(method);

        this.methodInvoker = new MethodInvoker() {
          public Object invoke(Object target, Object... parameters)
          throws IllegalAccessException, InvocationTargetException {
            Objects.assertNoNulls(parameters);
            return fastMethod.invoke(target, parameters);
          }
        };
      }

      Type[] parameterTypes = method.getGenericParameterTypes();
      parameterInjectors = parameterTypes.length > 0
          ? injector.getParametersInjectors(
              method, method.getParameterAnnotations(), parameterTypes)
          : null;
    }

    public void inject(InternalContext context, Object o) {
      try {
        methodInvoker.invoke(o, getParameters(context, parameterInjectors));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Invokes a method.
   */
  interface MethodInvoker {
    Object invoke(Object target, Object... parameters) throws
        IllegalAccessException, InvocationTargetException;
  }

  final Map<Class<?>, ConstructorInjector> constructors
      = new ReferenceCache<Class<?>, ConstructorInjector>() {
    @SuppressWarnings("unchecked")
    protected ConstructorInjector<?> create(Class<?> implementation) {
      if (implementation.isInterface()) {
        errorHandler.handle(defaultSource,
            ErrorMessages.CANNOT_INJECT_ABSTRACT_TYPE, implementation);
        return ConstructorInjector.invalidConstructor();
      }

      return new ConstructorInjector(InjectorImpl.this, implementation);
    }
  };

  /**
   * A placeholder. This enables us to continue processing and gather more
   * errors but blows up if you actually try to use it.
   */
  static class InvalidConstructor {
    InvalidConstructor() {
      throw new AssertionError();
    }
  }

  @SuppressWarnings("unchecked")
  static <T> Constructor<T> invalidConstructor() {
    try {
      return (Constructor<T>) InvalidConstructor.class.getDeclaredConstructor();
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  static class SingleParameterInjector<T> {

    final ExternalContext<T> externalContext;
    final InternalFactory<? extends T> factory;

    public SingleParameterInjector(ExternalContext<T> externalContext,
        InternalFactory<? extends T> factory) {
      this.externalContext = externalContext;
      this.factory = factory;
    }

    T inject(InternalContext context) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        return factory.get(context);
      }
      catch (ConfigurationException e) {
        throw e;
      }
      catch (Throwable throwable) {
        throw new ProvisionException(externalContext, throwable);
      }
      finally {
        context.setExternalContext(previous);
      }
    }
  }

  /**
   * Iterates over parameter injectors and creates an array of parameter
   * values.
   */
  static Object[] getParameters(InternalContext context,
      SingleParameterInjector[] parameterInjectors) {
    if (parameterInjectors == null) {
      return null;
    }

    Object[] parameters = new Object[parameterInjectors.length];
    for (int i = 0; i < parameters.length; i++) {
      parameters[i] = parameterInjectors[i].inject(context);
    }
    return parameters;
  }

  void injectMembers(Object o, InternalContext context) {
    List<SingleMemberInjector> injectorsForClass = injectors.get(o.getClass());
    for (SingleMemberInjector injector : injectorsForClass) {
      injector.inject(context, o);
    }
  }

  // Not test-covered
  public void injectMembers(final Object o) {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        injectMembers(o, context);
        return null;
      }
    });
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  public <T> Provider<T> getProvider(final Key<T> key) {
    final InternalFactory<? extends T> factory = getInternalFactory(null, key);

    if (factory == null) {
      throw new ConfigurationException(
          "Missing binding to " + ErrorMessages.convert(key) + ".");
    }

    return new Provider<T>() {
      public T get() {
        return callInContext(new ContextualCallable<T>() {
          public T call(InternalContext context) {
            ExternalContext<?> previous = context.getExternalContext();
            context.setExternalContext(
                ExternalContext.newInstance(null, key, InjectorImpl.this));
            try {
              return factory.get(context);
            }
            finally {
              context.setExternalContext(previous);
            }
          }
        });
      }

      public String toString() {
        return factory.toString();
      }
    };
  }

  public <T> T getInstance(Key<T> key) {
    return getProvider(key).get();
  }

  public <T> T getInstance(Class<T> type) {
    return getProvider(type).get();
  }

  final ThreadLocal<InternalContext[]> localContext
      = new ThreadLocal<InternalContext[]>() {
    protected InternalContext[] initialValue() {
      return new InternalContext[1];
    }
  };

  /**
   * Looks up thread local context. Creates (and removes) a new context if
   * necessary.
   */
  <T> T callInContext(ContextualCallable<T> callable) {
    InternalContext[] reference = localContext.get();
    if (reference[0] == null) {
      reference[0] = new InternalContext(this);
      try {
        return callable.call(reference[0]);
      }
      finally {
        // Only remove the context if this call created it.
        reference[0] = null;
      }
    }
    else {
      // Someone else will clean up this context.
      return callable.call(reference[0]);
    }
  }

  /**
   * Gets a constructor function for a given implementation class.
   */
  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(Class<T> implementation) {
    return constructors.get(implementation);
  }

  @SuppressWarnings("unchecked")
  <T> ConstructorInjector<T> getConstructor(TypeLiteral<T> implementation) {
    return constructors.get(implementation.getRawType());
  }

  /**
   * Injects a field or method in a given object.
   */
  interface SingleMemberInjector {
    void inject(InternalContext context, Object o);
  }

  class MissingDependencyException extends Exception {

    final Key<?> key;
    final Member member;

    MissingDependencyException(Key<?> key, Member member) {
      this.key = key;
      this.member = member;
    }

    void handle(ErrorHandler errorHandler) {
      ErrorMessages.handleMissingBinding(errorHandler, member, key,
          getNamesOfBindingAnnotations(key.getTypeLiteral()));
    }
  }

  /**
   * Map of primitive type converters.
   */
  static class PrimitiveConverters extends HashMap<Class<?>, Converter<?>> {

    PrimitiveConverters() {
      putParser(int.class);
      putParser(long.class);
      putParser(boolean.class);
      putParser(byte.class);
      putParser(short.class);
      putParser(float.class);
      putParser(double.class);

      // Character doesn't follow the same pattern.
      Converter<Character> characterConverter = new Converter<Character>() {
        public Character convert(Member member, Key<Character> key,
            String value) throws ConstantConversionException {
          value = value.trim();
          if (value.length() != 1) {
            throw new ConstantConversionException(member, key, value,
                "Length != 1.");
          }
          return value.charAt(0);
        }
      };
      put(char.class, characterConverter);
      put(Character.class, characterConverter);
    }

    <T> void putParser(final Class<T> primitive) {
      try {
        Class<?> wrapper = PRIMITIVE_COUNTERPARTS.get(primitive);
        final Method parser = wrapper.getMethod(
            "parse" + Strings.capitalize(primitive.getName()), String.class);
        Converter<T> converter = new Converter<T>() {
          @SuppressWarnings("unchecked")
          public T convert(Member member, Key<T> key, String value)
              throws ConstantConversionException {
            try {
              return (T) parser.invoke(null, value);
            }
            catch (IllegalAccessException e) {
              throw new AssertionError(e);
            }
            catch (InvocationTargetException e) {
              throw new ConstantConversionException(member, key, value,
                  e.getTargetException());
            }
          }
        };
        put(wrapper, converter);
        put(primitive, converter);
      }
      catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
    }
  }

  /**
   * Converts a {@code String} to another type.
   */
  interface Converter<T> {

    /**
     * Converts {@code String} value.
     */
    T convert(Member member, Key<T> key, String value)
        throws ConstantConversionException;
  }

  Map<Class<?>, InternalFactory<?>> implicitBindings =
      new HashMap<Class<?>, InternalFactory<?>>();

  /**
   * Gets a factory for the specified type. Used when an explicit binding
   * was not made. Uses synchronization here so it's not necessary in the
   * factory itself. Returns {@code null} if the type isn't injectable.
   */
  <T> InternalFactory<? extends T> getImplicitBinding(Member member,
      final Class<T> type, Scope scope) {
    // Look for @DefaultImplementation.
    ImplementedBy implementedBy =
        type.getAnnotation(ImplementedBy.class);
    if (implementedBy != null) {
      Class<?> implementationType = implementedBy.value();

      // Make sure it's not the same type. TODO: Can we check for deeper loops?
      if (implementationType == type) {
        errorHandler.handle(StackTraceElements.forType(type),
            ErrorMessages.RECURSIVE_IMPLEMENTATION_TYPE, type);
        return invalidFactory();
      }

      // Make sure implementationType extends type.
      if (!type.isAssignableFrom(implementationType)) {
        errorHandler.handle(StackTraceElements.forType(type),
            ErrorMessages.NOT_A_SUBTYPE, implementationType, type);
        return invalidFactory();
      }

      return (InternalFactory<T>) getInternalFactory(
          member, Key.get(implementationType));      
    }

    // Look for @DefaultProvider.
    ProvidedBy providedBy = type.getAnnotation(ProvidedBy.class);
    if (providedBy != null) {
      final Class<? extends Provider<?>> providerType = providedBy.value();

      // Make sure it's not the same type. TODO: Can we check for deeper loops?
      if (providerType == type) {
        errorHandler.handle(StackTraceElements.forType(type),
            ErrorMessages.RECURSIVE_PROVIDER_TYPE, type);
        return invalidFactory();
      }

      // TODO: Make sure the provided type extends type. We at least check
      // the type at runtime below.

      InternalFactory<? extends Provider<?>> providerFactory
          = getInternalFactory(member, Key.get(providerType));
      Key<? extends Provider<?>> providerKey = Key.get(providerType);
      return (InternalFactory<T>) new BoundProviderFactory(
          providerKey, providerFactory, StackTraceElements.forType(type)) {
        public Object get(InternalContext context) {
          Object o = super.get(context);
          try {
            return type.cast(o);
          } catch (ClassCastException e) {
            errorHandler.handle(StackTraceElements.forType(type),
                ErrorMessages.SUBTYPE_NOT_PROVIDED, providerType, type);
            throw new AssertionError();
          }
        }
      };
    }

    // TODO: Method interceptors could actually enable us to implement
    // abstract types. Should we remove this restriction?
    if (Modifier.isAbstract(type.getModifiers())) {
      return null;
    }

    // Inject the class itself.
    synchronized (implicitBindings) {
      @SuppressWarnings("unchecked")
      InternalFactory<T> factory =
          (InternalFactory<T>) implicitBindings.get(type);
      if (factory != null) {
        return factory;
      }

      // Create the factory.
      ImplicitBinding<T> implicitBinding = new ImplicitBinding<T>(type);

      // Scope the factory if necessary.

      // If we don't have a scope from the configuration, look for one on
      // the type.
      if (scope == null) {
        scope = Scopes.getScopeForType(type, scopes, errorHandler);
      }

      InternalFactory<? extends T> scoped;
      if (scope != null) {
        scoped = Scopes.scope(Key.get(type), this, implicitBinding, scope);
      } else {
        scoped = implicitBinding;
      }

      implicitBindings.put(type, scoped);

      try {
        // Look up the constructor. We do this separately from constructions to
        // support circular dependencies.
        ConstructorInjector<T> constructor = getConstructor(type);
        implicitBinding.setConstructorInjector(constructor);
      }
      catch (RuntimeException e) {
        // Clean up state.
        implicitBindings.remove(type);
        throw e;
      }
      catch (Throwable t) {
        // Clean up state.
        implicitBindings.remove(type);
        throw new AssertionError(t);
      }
      
      return scoped;
    }
  }

  static class ImplicitBinding<T> implements InternalFactory<T> {

    final Class<T> implementation;
    ConstructorInjector<T> constructorInjector;

    ImplicitBinding(Class<T> implementation) {
      this.implementation = implementation;
    }

    void setConstructorInjector(
        ConstructorInjector<T> constructorInjector) {
      this.constructorInjector = constructorInjector;
    }

    public T get(InternalContext context) {
      return (T) constructorInjector.construct(context,
          context.getExpectedType());
    }
  }

  private static final InternalFactory<?> INVALID_FACTORY
      = new InternalFactory<Object>() {
    public Object get(InternalContext context) {
      throw new AssertionError();
    }
  };

  @SuppressWarnings("unchecked")
  static <T> InternalFactory<T> invalidFactory() {
    return (InternalFactory<T>) INVALID_FACTORY;
  }

  public String toString() {
    return new ToStringBuilder(Injector.class)
        .add("bindings", bindings)
        .toString();
  }
}
