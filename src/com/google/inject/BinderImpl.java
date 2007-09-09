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

import static com.google.inject.Nullability.NULLABLE;
import com.google.inject.InjectorImpl.SingleMemberInjector;
import static com.google.inject.Scopes.SINGLETON;
import com.google.inject.internal.Annotations;
import static com.google.inject.internal.Objects.nonNull;
import com.google.inject.internal.StackTraceElements;
import com.google.inject.internal.Stopwatch;
import com.google.inject.internal.Objects;
import com.google.inject.internal.Strings;
import com.google.inject.matcher.Matcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.Message;
import com.google.inject.spi.SourceProviders;
import com.google.inject.spi.TypeConverter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Builds a dependency injection {@link Injector}. Binds {@link Key}s to
 * implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class BinderImpl implements Binder {

  static {
    SourceProviders.skip(BinderImpl.class);
  }

  private static final Logger logger
      = Logger.getLogger(BinderImpl.class.getName());

  final LinkedList<BindingBuilderImpl<?>> bindingBuilders
      = new LinkedList<BindingBuilderImpl<?>>();
  final List<ConstantBindingBuilderImpl> constantBindingBuilders
      = new ArrayList<ConstantBindingBuilderImpl>();
  final Map<Class<? extends Annotation>, Scope> scopes =
      new HashMap<Class<? extends Annotation>, Scope>();

  final List<StaticInjection> staticInjections
      = new ArrayList<StaticInjection>();

  final Set<Module> modulesInstalled = new HashSet<Module>();

  InjectorImpl injector;

  final Stage stage;

  final Collection<Message> errorMessages = new ArrayList<Message>();

  final ProxyFactoryBuilder proxyFactoryBuilder;

  /**
   * Constructs a new builder.
   *
   * @param stage we're running in. If the stage is {@link Stage#PRODUCTION},
   *  we will eagerly load singletons.
   */
  public BinderImpl(Stage stage) {
    bindScope(Singleton.class, SINGLETON);

    bind(Logger.class, SourceProviders.UNKNOWN_SOURCE)
        .toProvider(new LoggerProvider());
    bind(Stage.class, SourceProviders.UNKNOWN_SOURCE).toInstance(stage);

    this.proxyFactoryBuilder = new ProxyFactoryBuilder();

    this.stage = stage;

    // Configure type converters.
    convertToPrimitiveType(int.class, Integer.class);
    convertToPrimitiveType(long.class, Long.class);
    convertToPrimitiveType(boolean.class, Boolean.class);
    convertToPrimitiveType(byte.class, Byte.class);
    convertToPrimitiveType(short.class, Short.class);
    convertToPrimitiveType(float.class, Float.class);
    convertToPrimitiveType(double.class, Double.class);

    TypeConverter characterConverter = new TypeConverter() {
      public Object convert(String value, TypeLiteral<?> toType) {
        value = value.trim();
        if (value.length() != 1) {
          throw new RuntimeException("Length != 1.");
        }
        return value.charAt(0);
      }

      @Override
      public String toString() {
        return "TypeConverter<Character>";
      }
    };

    convertToClass(char.class, characterConverter);
    convertToClass(Character.class, characterConverter);

    convertToClasses(Matchers.subclassesOf(Enum.class), new TypeConverter() {
      @SuppressWarnings("unchecked")
      public Object convert(String value, TypeLiteral<?> toType) {
        return Enum.valueOf((Class) toType.getRawType(), value);
      }

      @Override
      public String toString() {
        return "TypeConverter<E extends Enum<E>>";
      }
    });

    internalConvertToTypes(
      new AbstractMatcher<TypeLiteral<?>>() {
        public boolean matches(TypeLiteral<?> typeLiteral) {
          return typeLiteral.getRawType() == Class.class;
        }

        @Override
        public String toString() {
          return "Class<?>";
        }
      },
      new TypeConverter() {
        @SuppressWarnings("unchecked")
        public Object convert(String value, TypeLiteral<?> toType) {
          try {
            return Class.forName(value);
          }
          catch (ClassNotFoundException e) {
            throw new RuntimeException(e.getMessage());
          }
        }

        @Override
        public String toString() {
          return "TypeConverter<Class<?>>";
        }
      }
    );
  }

  /**
   * Constructs a new builder for a development environment (see
   * {@link Stage#DEVELOPMENT}).
   */
  public BinderImpl() {
    this(Stage.DEVELOPMENT);
  }

  final List<MatcherAndConverter<?>> converters
      = new ArrayList<MatcherAndConverter<?>>();

  <T> void convertToType(TypeLiteral<T> type,
      TypeConverter converter) {
    convertToClasses(Matchers.identicalTo(type), converter);
  }

  <T> void convertToClass(Class<T> type,
      TypeConverter converter) {
    convertToClasses(Matchers.identicalTo(type), converter);
  }

  void convertToClasses(final Matcher<? super Class<?>> typeMatcher,
      TypeConverter converter) {
    internalConvertToTypes(new AbstractMatcher<TypeLiteral<?>>() {
      public boolean matches(TypeLiteral<?> typeLiteral) {
        Type type = typeLiteral.getType();
        if (!(type instanceof Class)) {
          return false;
        }
        Class<?> clazz = (Class<?>) type;
        return typeMatcher.matches(clazz);
      }

      public String toString() {
        return typeMatcher.toString();
      }
    }, converter);
  }

  public void internalConvertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter converter) {
    converters.add(MatcherAndConverter.newInstance(typeMatcher, converter,
        SourceProviders.UNKNOWN_SOURCE));
  }

  public void convertToTypes(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter converter) {
    converters.add(MatcherAndConverter.newInstance(typeMatcher, converter));
  }

  <T> void convertToPrimitiveType(Class<T> primitiveType,
      final Class<T> wrapperType) {
    try {
      final Method parser = wrapperType.getMethod(
        "parse" + Strings.capitalize(primitiveType.getName()), String.class);

      TypeConverter typeConverter = new TypeConverter() {
        @SuppressWarnings("unchecked")
        public Object convert(String value, TypeLiteral<?> toType) {
          try {
            return (T) parser.invoke(null, value);
          }
          catch (IllegalAccessException e) {
            throw new AssertionError(e);
          }
          catch (InvocationTargetException e) {
            throw new RuntimeException(e.getTargetException().getMessage());
          }
        }

        @Override
        public String toString() {
          return "TypeConverter<" + wrapperType.getSimpleName() + ">";
        }
      };

      convertToClass(primitiveType, typeConverter);
      convertToClass(wrapperType, typeConverter);
    }
    catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  public Stage currentStage() {
    return stage;
  }

  final List<CreationListener> creationListeners
      = new ArrayList<CreationListener>();

  interface CreationListener {
    void notify(InjectorImpl injector);
  }

  final List<MembersInjector> membersInjectors
      = new ArrayList<MembersInjector>();

  static class MembersInjector {

    final Object o;

    MembersInjector(Object o) {
      this.o = o;
    }

    void checkDependencies(InjectorImpl injector) {
      injector.injectors.get(o.getClass());
    }

    void injectMembers(InjectorImpl injector) {
      injector.injectMembers(o);
    }    
  }

  public void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    proxyFactoryBuilder.intercept(classMatcher, methodMatcher, interceptors);
  }

  public void bindScope(Class<? extends Annotation> annotationType,
      Scope scope) {
    if (!Scopes.isScopeAnnotation(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_SCOPE_ANNOTATION);
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    if (!Annotations.isRetainedAtRuntime(annotationType)) {
      addError(StackTraceElements.forType(annotationType),
          ErrorMessages.MISSING_RUNTIME_RETENTION, source());
      // Go ahead and bind anyway so we don't get collateral errors.
    }

    Scope existing = scopes.get(nonNull(annotationType, "annotation type"));
    if (existing != null) {
      addError(source(), ErrorMessages.DUPLICATE_SCOPES, existing,
          annotationType, scope);
    }
    else {
      scopes.put(annotationType, nonNull(scope, "scope"));
    }
  }

  static boolean inGuiceNamespace(Class<?> clazz) {
    return clazz == Provider.class;
  }

  public <T> BindingBuilderImpl<T> bind(Key<T> key) {
    Object source = source();

    BindingBuilderImpl<T> builder =
        new BindingBuilderImpl<T>(this, key, source);

    Class<? super T> rawType = key.getTypeLiteral().getRawType();

    if (rawType == Provider.class) {
      addError(source, ErrorMessages.BINDING_TO_PROVIDER);
    } else if (Logger.class == rawType) {
      addError(source, ErrorMessages.LOGGER_ALREADY_BOUND);
    } else {
      bindingBuilders.add(builder);
    }

    return builder;
  }

  public <T> BindingBuilderImpl<T> bind(TypeLiteral<T> typeLiteral) {
    return bind(Key.get(typeLiteral));
  }

  public <T> BindingBuilderImpl<T> bind(Class<T> clazz) {
    return bind(Key.get(clazz));
  }

  /**
   * Internal use only.
   */
  <T> BindingBuilderImpl<T> bind(Class<T> clazz, Object source) {
    Key<T> key = Key.get(clazz);
    BindingBuilderImpl<T> builder =
        new BindingBuilderImpl<T>(this, key, source);
    // Put Guice's bindings in front of user's bindings.
    bindingBuilders.addFirst(builder);
    return builder;
  }

  public ConstantBindingBuilderImpl bindConstant() {
    ConstantBindingBuilderImpl constantBuilder
        = new ConstantBindingBuilderImpl(this, source());
    constantBindingBuilders.add(constantBuilder);
    return constantBuilder;
  }

  public void requestStaticInjection(Class<?>... types) {
    staticInjections.add(new StaticInjection(source(), types));
  }

  public void install(Module module) {
    if (modulesInstalled.add(module)) {
      module.configure(this);
    }
  }

  public void addError(String message, Object... arguments) {
    configurationErrorHandler.handle(source(), message, arguments);
  }

  public void addError(Throwable t) {
    Object source = source();
    String message = ErrorMessages.getRootMessage(t);
    String logMessage = String.format(
        ErrorMessages.EXCEPTION_REPORTED_BY_MODULE, message);
    logger.log(Level.INFO, logMessage, t);
    addError(source, ErrorMessages.EXCEPTION_REPORTED_BY_MODULE_SEE_LOG,
        message);
  }

  void addError(Object source, String message, Object... arguments) {
    configurationErrorHandler.handle(source, message, arguments);
  }

  void addError(Object source, String message) {
    configurationErrorHandler.handle(source, message);
  }

  /**
   * Adds an error message to be reported at creation time.
   */
  void add(Message errorMessage) {
    errorMessages.add(errorMessage);
  }

  final Stopwatch stopwatch = new Stopwatch();

  /**
   * Creates a {@link Injector} instance. Injects static members for classes
   * which were registered using {@link #requestStaticInjection(Class...)}.
   *
   * @throws CreationException if configuration errors are found. The
   *     expectation is that the application will log this exception and exit.
   * @throws IllegalStateException if called more than once
   */
  Injector createInjector() throws CreationException {
    stopwatch.resetAndLog(logger, "Configuration (running the modules)");

    Map<Key<?>, BindingImpl<?>> bindings
        = new HashMap<Key<?>, BindingImpl<?>>();
    injector = new InjectorImpl(
        proxyFactoryBuilder.create(), bindings, scopes, converters);

    // Create default bindings.
    // We use toProvider() instead of toInstance() to avoid infinite recursion
    // in toString().
    bind(Injector.class, SourceProviders.UNKNOWN_SOURCE)
        .toProvider(new InjectorProvider(injector));

    injector.setErrorHandler(configurationErrorHandler);

    createConstantBindings();

    // Commands to execute before returning the Injector instance.
    final List<ContextualCallable<Void>> preloaders
        = new ArrayList<ContextualCallable<Void>>();

    createBindings(preloaders);

    stopwatch.resetAndLog(logger, "Binding creation");

    injector.index();

    stopwatch.resetAndLog(logger, "Binding indexing");

    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(injector);
    }

    stopwatch.resetAndLog(logger, "Validation");

    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.createMemberInjectors(injector);
    }

    stopwatch.resetAndLog(logger, "Static validation");

    for (MembersInjector membersInjector : membersInjectors) {
      membersInjector.checkDependencies(injector);
    }

    stopwatch.resetAndLog(logger, "Instance member validation");

    // Blow up if we encountered errors.
    if (!errorMessages.isEmpty()) {
      throw new CreationException(errorMessages);
    }

    // Switch to runtime error handling.
    injector.setErrorHandler(new RuntimeErrorHandler());

    // If we're in the tool stage, stop here. Don't eagerly inject or load
    // anything.
    if (stage == Stage.TOOL) {
      // TODO: Wrap this and prevent usage of anything besides getBindings().
      return injector;
    }

    // Inject static members.
    for (StaticInjection staticInjection : staticInjections) {
      staticInjection.runMemberInjectors(injector);
    }

    stopwatch.resetAndLog(logger, "Static member injection");

    // Inject pre-existing instances.
    for (MembersInjector membersInjector : membersInjectors) {
      membersInjector.injectMembers(injector);
    }

    stopwatch.resetAndLog(logger, "Instance injection");

    // Run preloading commands.
    runPreloaders(injector, preloaders);

    stopwatch.resetAndLog(logger, "Preloading");

    return injector;
  }

  private void runPreloaders(InjectorImpl injector,
      final List<ContextualCallable<Void>> preloaders) {
    injector.callInContext(new ContextualCallable<Void>() {
      public Void call(InternalContext context) {
        for (ContextualCallable<Void> preloader : preloaders) {
          preloader.call(context);
        }
        return null;
      }
    });
  }

  private void createBindings(List<ContextualCallable<Void>> preloaders) {
    for (BindingBuilderImpl<?> builder : bindingBuilders) {
      createBinding(builder, preloaders);
    }
  }

  private <T> void createBinding(BindingBuilderImpl<T> builder,
      List<ContextualCallable<Void>> preloaders) {
    BindingImpl<T> binding = builder.build(injector);

    putBinding(binding);

    // Register to preload if necessary.
    boolean preload = stage == Stage.PRODUCTION;
    if (binding.getScope() == Scopes.SINGLETON) {
      if (preload || builder.shouldPreload()) {
        preloaders.add(
            new BindingPreloader(binding.key, binding.internalFactory));
      }
    }
    else {
      if (builder.shouldPreload()) {
        addError(builder.getSource(), ErrorMessages.PRELOAD_NOT_ALLOWED);
      }
    }
  }

  private void createConstantBindings() {
    for (ConstantBindingBuilderImpl builder : constantBindingBuilders) {
      createConstantBinding(builder);
    }
  }

  private void createConstantBinding(ConstantBindingBuilderImpl builder) {
    if (builder.hasValue()) {
      putBinding(builder.createBinding(injector));
    }
    else {
      addError(builder.getSource(), ErrorMessages.MISSING_CONSTANT_VALUE);
    }
  }

  private static Set<Class<?>> FORBIDDEN_TYPES = forbiddenTypes();

  private static Set<Class<?>> forbiddenTypes() {
    Set<Class<?>> set = new HashSet<Class<?>>();
    Collections.addAll(set,

        // It's unfortunate that we have to maintain a blacklist of specific
        // classes, but we can't easily block the whole package because of
        // all our unit tests.

        AbstractModule.class,
        Binder.class,
        Binding.class,
        Key.class,
        Module.class,
        Provider.class,
        Scope.class,
        TypeLiteral.class);
    return Collections.unmodifiableSet(set);
  }

  void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();
    Map<Key<?>, BindingImpl<?>> bindings = injector.internalBindings();
    Binding<?> original = bindings.get(key);

    Class<?> rawType = key.getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      addError(binding.getSource(), ErrorMessages.CANNOT_BIND_TO_GUICE_TYPE,
          rawType.getSimpleName());
      return;
    }

    if (bindings.containsKey(key)) {
      addError(binding.getSource(), ErrorMessages.BINDING_ALREADY_SET, key,
          original.getSource());
    }
    else {
      bindings.put(key, binding);
    }
  }

  /**
   * Gets the current source.
   */
  Object source() {
    return SourceProviders.defaultSource();    
  }

  ErrorHandler configurationErrorHandler = new AbstractErrorHandler() {

    public void handle(Object source, String message) {
      add(new Message(source, message));
    }
  };

  /**
   * Handles errors after the injector is created.
   */
  static class RuntimeErrorHandler extends AbstractErrorHandler {

    static ErrorHandler INSTANCE = new RuntimeErrorHandler();

    public void handle(Object source, String message) {
      throw new ConfigurationException("Error at " + source + " " + message);
    }
  }

  /**
   * A requested static injection.
   */
  class StaticInjection {

    final Object source;
    final Class<?>[] types;
    final List<SingleMemberInjector> memberInjectors
        = new ArrayList<SingleMemberInjector>();

    public StaticInjection(Object source, Class<?>[] types) {
      this.source = source;
      this.types = types;
    }

    void createMemberInjectors(final InjectorImpl injector) {
      injector.withDefaultSource(source,
          new Runnable() {
            public void run() {
              for (Class<?> clazz : types) {
                injector.addSingleInjectorsForFields(
                    clazz.getDeclaredFields(), true, memberInjectors);
                injector.addSingleInjectorsForMethods(
                    clazz.getDeclaredMethods(), true, memberInjectors);
              }
            }
          });
    }

    void runMemberInjectors(InjectorImpl injector) {
      injector.callInContext(new ContextualCallable<Void>() {
        public Void call(InternalContext context) {
          for (SingleMemberInjector injector : memberInjectors) {
            injector.inject(context, null);
          }
          return null;
        }
      });
    }
  }

  static class BindingPreloader implements ContextualCallable<Void> {

    private final Key<?> key;
    private final InternalFactory<?> factory;

    public BindingPreloader(Key<?> key, InternalFactory<?> factory) {
      this.key = key;
      this.factory = Objects.nonNull(factory, "factory");
    }

    public Void call(InternalContext context) {
      context.pushExternalContext(ExternalContext.newInstance(
          null, NULLABLE, key, context.getInjectorImpl()));
      try {
        factory.get(context);
        return null;
      }
      finally {
        context.popExternalContext();
      }
    }
  }

  public <T> Provider<T> getProvider(Key<T> key) {
    ProviderProxy<T> providerProxy = new ProviderProxy<T>(source(), key);
    creationListeners.add(providerProxy);
    return providerProxy;
  }

  public <T> Provider<T> getProvider(Class<T> type) {
    return getProvider(Key.get(type));
  }

  /**
   * A reference to a provider which can be filled in once the Injector has
   * been created.
   */
  static class ProviderProxy<T> implements Provider<T>, CreationListener {

    final Object source;
    final Key<T> key;

    // We don't have to synchronize access to this field because it doesn't
    // change after the Injector has been created.
    Provider<T> delegate = illegalProvider();

    public ProviderProxy(Object source, Key<T> key) {
      this.source = source;
      this.key = key;
    }

    public void notify(final InjectorImpl injector) {
      injector.withDefaultSource(source, new Runnable() {
        public void run() {
          try {
            delegate = injector.getProvider(key);
          }
          catch (ConfigurationException e) {
            ErrorMessages.handleMissingBinding(injector, source, key);
          }
        }
      });
    }

    public T get() {
      return delegate.get();
    }
  }

  static final Provider<Object> ILLEGAL_PROVIDER = new Provider<Object>() {
    public Object get() {
      throw new IllegalStateException("This provider cannot be used until the"
          + " Injector has been created.");
    }
  };

  @SuppressWarnings("unchecked")
  static <T> Provider<T> illegalProvider() {
    return (Provider<T>) ILLEGAL_PROVIDER;
  }

  static class LoggerProvider implements Provider<Logger> {

    @Inject Injector injector;
    public Logger get() {
      InternalContext context = ((InjectorImpl) injector).getContext();
      Member member = context.getExternalContext().getMember();
      return member == null
          ? Logger.getAnonymousLogger()
          : Logger.getLogger(member.getDeclaringClass().getName());
    }

    public String toString() {
      return "Provider<Logger>";
    }
  }

  static class InjectorProvider implements Provider<Injector> {

    final Injector injector;

    InjectorProvider(Injector injector) {
      this.injector = injector;
    }

    public Injector get() {
      return injector;
    }

    public String toString() {
      return "Provider<Injector>";
    }
  }
}
