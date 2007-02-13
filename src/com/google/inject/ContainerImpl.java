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

import static com.google.inject.ConstantConversionException.createMessage;
import com.google.inject.util.GuiceFastClass;
import com.google.inject.util.ReferenceCache;
import com.google.inject.util.Strings;
import com.google.inject.util.ToStringBuilder;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;

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

/**
 * Default {@link Container} implementation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @see ContainerBuilder
 */
class ContainerImpl implements Container {

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
  final Map<Key<?>, Binding<?>> bindings;
  final BindingsMultimap bindingsMultimap = new BindingsMultimap();
  final Map<String, Scope> scopes;

  ErrorHandler errorHandler = new InvalidErrorHandler();

  ContainerImpl(ConstructionProxyFactory constructionProxyFactory,
      Map<Key<?>, Binding<?>> bindings, Map<String, Scope> scopes) {
    this.constructionProxyFactory = constructionProxyFactory;
    this.bindings = bindings;
    this.scopes = scopes;
  }

  /**
   * Indexes bindings by type.
   */
  void index() {
    for (Binding<?> binding : bindings.values()) {
      index(binding);
    }
  }

  <T> void index(Binding<T> binding) {
    bindingsMultimap.put(binding.getKey().getType(), binding);
  }

  public <T> List<Binding<T>> findBindingsByType(TypeLiteral<T> type) {
    return bindingsMultimap.getAll(type);
  }

  <T> List<String> getNamesOfBindingsTo(TypeLiteral<T> type) {
    List<String> names = new ArrayList<String>();
    for (Binding<T> binding : findBindingsByType(type)) {
      names.add(binding.getKey().getName());
    }
    return names;
  }

  void withErrorHandler(ErrorHandler errorHandler, Runnable runnable) {
    ErrorHandler previous = this.errorHandler;
    this.errorHandler = errorHandler;
    try {
      runnable.run();
    }
    finally {
      this.errorHandler = previous;
    }
  }

  void setErrorHandler(ErrorHandler errorHandler) {
    this.errorHandler = errorHandler;
  }

  <T> InternalFactory<? extends T> getFactory(final Member member, Key<T> key) {
    // TODO: Clean up unchecked type warnings.

    // Do we have a factory for the specified type and name?
    Binding<T> binding = getBinding(key);
    if (binding != null) {
      return binding.getInternalFactory();
    }

    Class<? super T> rawType = key.getType().getRawType();

    // Handle cases where T is a Factory<?>.
    if (rawType.equals(Factory.class)) {
      Type factoryType = key.getType().getType();
      if (!(factoryType instanceof ParameterizedType)) {
        return null;
      }
      Type entryType
          = ((ParameterizedType) factoryType).getActualTypeArguments()[0];

      try {
        final Factory<?> factory
            = getFactory(Key.get(entryType, key.getName()));
        return new InternalFactory<T>() {
          @SuppressWarnings("unchecked")
          public T get(InternalContext context) {
            return (T) factory;
          }
        };
      }
      catch (ConfigurationException e) {
        ErrorMessages.handleMissingBinding(errorHandler, member, key,
            getNamesOfBindingsTo(key.getType()));
        return invalidFactory();
      }
    }

    // Auto[un]box primitives.
    Class<?> primitiveCounterpart
        = PRIMITIVE_COUNTERPARTS.get(rawType);
    if (primitiveCounterpart != null) {
      Binding<?> counterpartBinding
          = getBinding(Key.get(primitiveCounterpart, key.getName()));
      if (counterpartBinding != null) {
        return (InternalFactory<? extends T>)
            counterpartBinding.getInternalFactory();
      }
    }

    // Can we convert from a String constant?
    Binding<String> stringBinding
        = getBinding(Key.get(String.class, key.getName()));
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
          errorHandler.handle(e);
          return invalidFactory();
        }
      }

      // Do we need an enum?
      if (Enum.class.isAssignableFrom(rawType)) {
        T t;
        try {
          t = (T) Enum.valueOf((Class) rawType, value);
        }
        catch (IllegalArgumentException e) {
          errorHandler.handle(createMessage(value, key, member, e.toString()));
          return invalidFactory();
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
          errorHandler.handle(createMessage(value, key, member, e.toString()));
          return invalidFactory();
        }
      }
    }

    // Don't try to inject primitives, arrays, enums, interfaces or abstract
    // classes.
    int modifiers = rawType.getModifiers();
    if (rawType.isArray() || rawType.isEnum()
        || Modifier.isAbstract(modifiers) || rawType.isPrimitive()) {
      return null;
    }

    // Last resort: inject the type itself.
    if (member != null) {
      // If we're injecting into a member, include it in the error messages.
      final ErrorHandler previous = this.errorHandler;
      this.errorHandler = new AbstractErrorHandler() {
        public void handle(String message) {
          previous.handle("Error while injecting "
              + ErrorMessages.convert(member) + ": " + message);
        }
        public void handle(Throwable t) {
          previous.handle(t);
        }
      };
      try {
        return (InternalFactory<? extends T>) getImplicitBinding(rawType);
      }
      finally {
        this.errorHandler = previous;
      }
    }
    return (InternalFactory<? extends T>) getImplicitBinding(rawType);
  }

  boolean isConstantType(Class<?> type) {
    return PRIMITIVE_CONVERTERS.containsKey(type)
        || Enum.class.isAssignableFrom(type)
        || type == Class.class;
  }

  /**
   * Field and method injectors.
   */
  final Map<Class<?>, List<Injector>> injectors
      = new ReferenceCache<Class<?>, List<Injector>>() {
    protected List<Injector> create(Class<?> key) {
      if (key.isInterface()) {
        errorHandler.handle(ErrorMessages.CANNOT_INJECT_INTERFACE, key);
        return Collections.emptyList();
      }

      List<Injector> injectors = new ArrayList<Injector>();
      addInjectors(key, injectors);
      return injectors;
    }
  };

  /**
   * Recursively adds injectors for fields and methods from the given class to
   * the given list. Injects parent classes before sub classes.
   */
  void addInjectors(Class clazz, List<Injector> injectors) {
    if (clazz == Object.class) {
      return;
    }

    // Add injectors for superclass first.
    addInjectors(clazz.getSuperclass(), injectors);

    // TODO (crazybob): Filter out overridden members.
    addInjectorsForFields(clazz.getDeclaredFields(), false, injectors);
    addInjectorsForMethods(clazz.getDeclaredMethods(), false, injectors);
  }

  void addInjectorsForMethods(Method[] methods, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(methods), statics, injectors,
        new InjectorFactory<Method>() {
          public Injector create(ContainerImpl container, Method method,
              String name) throws MissingDependencyException {
            return new MethodInjector(container, method, name);
          }
        });
  }

  void addInjectorsForFields(Field[] fields, boolean statics,
      List<Injector> injectors) {
    addInjectorsForMembers(Arrays.asList(fields), statics, injectors,
        new InjectorFactory<Field>() {
          public Injector create(ContainerImpl container, Field field,
              String name) throws MissingDependencyException {
            return new FieldInjector(container, field, name);
          }
        });
  }

  <M extends Member & AnnotatedElement> void addInjectorsForMembers(
      List<M> members, boolean statics, List<Injector> injectors,
      InjectorFactory<M> injectorFactory) {
    for (M member : members) {
      if (isStatic(member) == statics) {
        Inject inject = member.getAnnotation(Inject.class);
        if (inject != null) {
          try {
            injectors.add(injectorFactory.create(this, member, inject.value()));
          }
          catch (MissingDependencyException e) {
            if (inject.required()) {
              // TODO: Report errors for more than one parameter per member.
              e.handle(errorHandler);
            }
          }
        }
      }
    }
  }

  Map<Key<?>, Binding<?>> internalBindings() {
    return bindings;
  }

  public Map<Key<?>, Binding<?>> getBindings() {
    return Collections.unmodifiableMap(bindings);
  }

  // TODO: try to get rid of the warning
  @SuppressWarnings("unchecked")
  public <T> Binding<T> getBinding(Key<T> key) {
    return (Binding<T>) bindings.get(key);
  }

  interface InjectorFactory<M extends Member & AnnotatedElement> {
    Injector create(ContainerImpl container, M member, String name)
        throws MissingDependencyException;
  }

  private boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private static class BindingsMultimap {
    private final Map<TypeLiteral<?>, List<? extends Binding<?>>> map
        = new HashMap<TypeLiteral<?>, List<? extends Binding<?>>>();

    public <T> void put(TypeLiteral<T> type, Binding<T> binding) {
      List<Binding<T>> bindingsForThisType = getFromMap(type);
      if (bindingsForThisType == null) {
        bindingsForThisType = new ArrayList<Binding<T>>();
        // We only put matching entries into the map
        map.put(type, bindingsForThisType);
      }
      bindingsForThisType.add(binding);
    }

    public <T> List<Binding<T>> getAll(TypeLiteral<T> type) {
      List<Binding<T>> list = getFromMap(type);
      return list == null ? Collections.<Binding<T>>emptyList() : list;
    }

    // safe because we only put matching entries into the map
    @SuppressWarnings("unchecked")
    private <T> List<Binding<T>> getFromMap(TypeLiteral<T> type) {
      return (List<Binding<T>>) map.get(type);
    }
  }

  class FieldInjector implements Injector {

    final Field field;
    final InternalFactory<?> factory;
    final ExternalContext<?> externalContext;

    public FieldInjector(ContainerImpl container, Field field, String name)
        throws MissingDependencyException {
      this.field = field;

      // Ewwwww...
      field.setAccessible(true);

      Key<?> key = Key.get(field.getGenericType(), name);
      factory = container.getFactory(field, key);
      if (factory == null) {
        throw new MissingDependencyException(key, field);
      }

      this.externalContext = ExternalContext.newInstance(field, key, container);
    }

    public void inject(InternalContext context, Object o) {
      ExternalContext<?> previous = context.getExternalContext();
      context.setExternalContext(externalContext);
      try {
        field.set(o, factory.get(context));
      }
      catch (IllegalAccessException e) {
        throw new AssertionError(e);
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
  ParameterInjector<?>[] getParametersInjectors(M member,
      Annotation[][] annotations, Type[] parameterTypes, String defaultName)
      throws MissingDependencyException {
    boolean defaultNameOverridden = !defaultName.equals(Key.DEFAULT_NAME);

    // We only carry over the name from the member level annotation to the
    // parameters if there's only one parameter.
    if (parameterTypes.length != 1 && defaultNameOverridden) {
      errorHandler.handle(
          ErrorMessages.NAME_ON_MEMBER_WITH_MULTIPLE_PARAMS, member);
    }

    ParameterInjector<?>[] parameterInjectors
        = new ParameterInjector<?>[parameterTypes.length];
    Iterator<Annotation[]> annotationsIterator
        = Arrays.asList(annotations).iterator();
    int index = 0;
    for (Type parameterType : parameterTypes) {
      Inject annotation = findInject(annotationsIterator.next());

      String name;
      if (defaultNameOverridden) {
        name = defaultName;
        if (annotation != null) {
          errorHandler.handle(
              ErrorMessages.NAME_ON_MEMBER_AND_PARAMETER, member);
        }
      }
      else {
        name = annotation == null ? defaultName : annotation.value();
      }

      Key<?> key = Key.get(parameterType, name);
      parameterInjectors[index] = createParameterInjector(key, member, index);
      index++;
    }

    return parameterInjectors;
  }

  <T> ParameterInjector<T> createParameterInjector(Key<T> key, Member member,
      int index) throws MissingDependencyException {
    InternalFactory<? extends T> factory = getFactory(member, key);
    if (factory == null) {
      throw new MissingDependencyException(key, member);
    }

    ExternalContext<T> externalContext
        = ExternalContext.newInstance(member, index, key, this);
    return new ParameterInjector<T>(externalContext, factory);
  }

  /**
   * Finds the {@link Inject} annotation in an array of annotations.
   */
  Inject findInject(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      if (annotation.annotationType() == Inject.class) {
        return Inject.class.cast(annotation);
      }
    }
    return null;
  }

  static class MethodInjector implements Injector {

    final FastMethod fastMethod;
    final ParameterInjector<?>[] parameterInjectors;

    public MethodInjector(ContainerImpl container, Method method, String name)
        throws MissingDependencyException {
      FastClass fastClass = GuiceFastClass.create(method.getDeclaringClass());
      this.fastMethod = fastClass.getMethod(method);
      Type[] parameterTypes = method.getGenericParameterTypes();
      parameterInjectors = parameterTypes.length > 0
          ? container.getParametersInjectors(
              method, method.getParameterAnnotations(), parameterTypes, name)
          : null;
    }

    public void inject(InternalContext context, Object o) {
      try {
        fastMethod.invoke(o, getParameters(context, parameterInjectors));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  final Map<Class<?>, ConstructorInjector> constructors
      = new ReferenceCache<Class<?>, ConstructorInjector>() {
    @SuppressWarnings("unchecked")
    protected ConstructorInjector<?> create(Class<?> implementation) {
      if (implementation.isInterface()) {
        errorHandler.handle(
            ErrorMessages.CANNOT_INJECT_INTERFACE, implementation);
        return ConstructorInjector.invalidConstructor();
      }

      return new ConstructorInjector(ContainerImpl.this, implementation);
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

  static class ParameterInjector<T> {

    final ExternalContext<T> externalContext;
    final InternalFactory<? extends T> factory;

    public ParameterInjector(ExternalContext<T> externalContext,
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
      ParameterInjector[] parameterInjectors) {
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
    List<Injector> injectorsForClass = injectors.get(o.getClass());
    for (Injector injector : injectorsForClass) {
      injector.inject(context, o);
    }
  }

  public void injectMembers(final Object o) {
    callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        injectMembers(o, context);
        return null;
      }
    });
  }

  public <T> T getInstance(TypeLiteral<T> type, String name) {
    return getFactory(Key.get(type, name)).get();
  }

  public <T> T getInstance(Class<T> type, String name) {
    return getFactory(Key.get(type, name)).get();
  }

  public <T> Factory<T> getFactory(Class<T> type, String name) {
    return getFactory(Key.get(type, name));
  }

  public <T> Factory<T> getFactory(TypeLiteral<T> type, String name) {
    return getFactory(Key.get(type, name));
  }

  public <T> T getInstance(TypeLiteral<T> type) {
    return getFactory(Key.get(type)).get();
  }

  public <T> T getInstance(Class<T> type) {
    return getFactory(Key.get(type)).get();
  }

  public <T> T getInstance(Key<T> key) {
    return getFactory(key).get();
  }

  public <T> Factory<T> getFactory(Class<T> type) {
    return getFactory(Key.get(type));
  }

  public <T> Factory<T> getFactory(TypeLiteral<T> type) {
    return getFactory(Key.get(type));
  }

  public <T> Factory<T> getFactory(final Key<T> key) {
    final InternalFactory<? extends T> factory = getFactory(null, key);

    if (factory == null) {
      throw new ConfigurationException(
          "Missing binding to " + ErrorMessages.convert(key) + ".");
    }

    return new Factory<T>() {
      public T get() {
        return callInContext(new ContextualCallable<T>() {
          public T call(InternalContext context) {
            ExternalContext<?> previous = context.getExternalContext();
            context.setExternalContext(
                ExternalContext.newInstance(null, key, ContainerImpl.this));
            try {
              return factory.get(context);
            }
            finally {
              context.setExternalContext(previous);
            }
          }
        });
      }
    };
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
  interface Injector {
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
          getNamesOfBindingsTo(key.getType()));
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
  <T> InternalFactory<? extends T> getImplicitBinding(Class<T> type) {
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
      Scope scope = Scopes.getScopeForType(type, scopes, errorHandler);
      InternalFactory<? extends T> scoped;
      if (scope != null) {
        scoped = Scopes.scope(Key.get(type), this, implicitBinding, scope);
      } else {
        scoped = implicitBinding;
      }
      implicitBindings.put(type, scoped);

      // Look up the constructor. We do this separately to support circular
      // dependencies.
      ConstructorInjector<T> constructor = getConstructor(type);
      implicitBinding.setConstructorInjector(constructor);

      return scoped;
    }
  }

  static class ImplicitBinding<T> implements InternalFactory<T> {

    final Class<T> type;
    ConstructorInjector<T> constructorInjector;

    ImplicitBinding(Class<T> type) {
      this.type = type;
    }

    void setConstructorInjector(
        ConstructorInjector<T> constructorInjector) {
      this.constructorInjector = constructorInjector;
    }

    public T get(InternalContext context) {
      return constructorInjector.construct(context, type);
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
    return new ToStringBuilder(Container.class)
        .add("bindings", bindings)
        .toString();
  }
}
