/**
 * Copyright (C) 2016 Google Inc.
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.inject.internal.Errors.checkConfiguration;
import static com.google.inject.util.Types.newParameterizedType;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MultibindingsTargetVisitor;
import com.google.inject.multibindings.OptionalBinderBinding;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.Element;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Types;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Set;
import javax.inject.Qualifier;

/**
 * The actual OptionalBinder plays several roles.  It implements Module to hide that
 * fact from the public API, and installs the various bindings that are exposed to the user.
 */
public final class RealOptionalBinder<T> implements Module {
  public static <T> RealOptionalBinder<T> newRealOptionalBinder(Binder binder, Key<T> type) {
    binder = binder.skipSources(RealOptionalBinder.class);
    RealOptionalBinder<T> optionalBinder = new RealOptionalBinder<T>(binder, type);
    binder.install(optionalBinder);
    return optionalBinder;
  }

  /* Reflectively capture java 8's Optional types so we can bind them if we're running in java8. */
  private static final Class<?> JAVA_OPTIONAL_CLASS;
  private static final Method JAVA_EMPTY_METHOD;
  private static final Method JAVA_OF_NULLABLE_METHOD;
  static {
    Class<?> optional = null;
    Method empty = null;
    Method ofNullable = null;
    boolean useJavaOptional = false;
    try {
      optional = Class.forName("java.util.Optional");
      empty = optional.getDeclaredMethod("empty");
      ofNullable = optional.getDeclaredMethod("ofNullable", Object.class);
      useJavaOptional = true;
    } catch (ClassNotFoundException ignored) {
    } catch (NoSuchMethodException ignored) {
    } catch (SecurityException ignored) {
    }
    JAVA_OPTIONAL_CLASS = useJavaOptional ? optional : null;
    JAVA_EMPTY_METHOD = useJavaOptional ? empty : null;
    JAVA_OF_NULLABLE_METHOD = useJavaOptional ? ofNullable : null;
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<T>> optionalOf(
      TypeLiteral<T> type) {
    return (TypeLiteral<Optional<T>>) TypeLiteral.get(
        Types.newParameterizedType(Optional.class,  type.getType()));
  }

  static <T> TypeLiteral<?> javaOptionalOf(
      TypeLiteral<T> type) {
    checkState(JAVA_OPTIONAL_CLASS != null, "java.util.Optional not found");
    return TypeLiteral.get(Types.newParameterizedType(JAVA_OPTIONAL_CLASS, type.getType()));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<javax.inject.Provider<T>>> optionalOfJavaxProvider(
      TypeLiteral<T> type) {
    return (TypeLiteral<Optional<javax.inject.Provider<T>>>) TypeLiteral.get(
        Types.newParameterizedType(Optional.class,
            newParameterizedType(javax.inject.Provider.class, type.getType())));
  }

  static <T> TypeLiteral<?> javaOptionalOfJavaxProvider(
      TypeLiteral<T> type) {
    checkState(JAVA_OPTIONAL_CLASS != null, "java.util.Optional not found");
    return TypeLiteral.get(Types.newParameterizedType(JAVA_OPTIONAL_CLASS,
        newParameterizedType(javax.inject.Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<Provider<T>>> optionalOfProvider(TypeLiteral<T> type) {
    return (TypeLiteral<Optional<Provider<T>>>) TypeLiteral.get(Types.newParameterizedType(
        Optional.class, newParameterizedType(Provider.class, type.getType())));
  }

  static <T> TypeLiteral<?> javaOptionalOfProvider(TypeLiteral<T> type) {
    checkState(JAVA_OPTIONAL_CLASS != null, "java.util.Optional not found");
    return TypeLiteral.get(Types.newParameterizedType(JAVA_OPTIONAL_CLASS,
        newParameterizedType(Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> Key<Provider<T>> providerOf(Key<T> key) {
    Type providerT = Types.providerOf(key.getTypeLiteral().getType());
    return (Key<Provider<T>>) key.ofType(providerT);
  }

  enum Source { DEFAULT, ACTUAL }
  
  @Retention(RUNTIME)
  @Qualifier
  @interface Default {
    String value();
  }

  @Retention(RUNTIME)
  @Qualifier
  @interface Actual {
    String value();
  }

  private final Key<T> typeKey;
  private final Key<Optional<T>> optionalKey;
  private final Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey;
  private final Key<Optional<Provider<T>>> optionalProviderKey;
  private final Provider<Optional<Provider<T>>> optionalProviderT;
  private final Key<T> defaultKey;
  private final Key<T> actualKey;

  private final Key javaOptionalKey;
  private final Key javaOptionalJavaxProviderKey;
  private final Key javaOptionalProviderKey;

  /** the target injector's binder. non-null until initialization, null afterwards */
  private Binder binder;
  /** the default binding, for the SPI. */
  private Binding<T> defaultBinding;
  /** the actual binding, for the SPI */
  private Binding<T> actualBinding;
  
  /** the dependencies -- initialized with defaults & overridden when tooled. */
  private Set<Dependency<?>> dependencies;
  /** the dependencies -- initialized with defaults & overridden when tooled. */
  private Set<Dependency<?>> providerDependencies;

  private RealOptionalBinder(Binder binder, Key<T> typeKey) {
    this.binder = binder;
    this.typeKey = checkNotNull(typeKey);
    TypeLiteral<T> literal = typeKey.getTypeLiteral();
    this.optionalKey = typeKey.ofType(optionalOf(literal));
    this.optionalJavaxProviderKey = typeKey.ofType(optionalOfJavaxProvider(literal));
    this.optionalProviderKey = typeKey.ofType(optionalOfProvider(literal));
    this.optionalProviderT = binder.getProvider(optionalProviderKey);
    String name = Annotations.nameOf(typeKey);
    this.defaultKey = Key.get(typeKey.getTypeLiteral(), new DefaultImpl(name));
    this.actualKey = Key.get(typeKey.getTypeLiteral(), new ActualImpl(name));
    // Until the injector initializes us, we don't know what our dependencies are,
    // so initialize to the whole Injector (like Multibinder, and MapBinder indirectly).
    this.dependencies = ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));
    this.providerDependencies =
        ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));

    if (JAVA_OPTIONAL_CLASS != null) {
      this.javaOptionalKey = typeKey.ofType(javaOptionalOf(literal));
      this.javaOptionalJavaxProviderKey = typeKey.ofType(javaOptionalOfJavaxProvider(literal));
      this.javaOptionalProviderKey = typeKey.ofType(javaOptionalOfProvider(literal));
    } else {
      this.javaOptionalKey = null;
      this.javaOptionalJavaxProviderKey = null;
      this.javaOptionalProviderKey = null;
    }
  }

  /**
   * Adds a binding for T. Multiple calls to this are safe, and will be collapsed as duplicate
   * bindings.
   */
  private void addDirectTypeBinding(Binder binder) {
    binder.bind(typeKey).toProvider(new RealDirectTypeProvider());
  }

  /**
   * Returns the key to use for the default binding.
   * 
   * <p>As a side effect this installs support for the 'direct type', so a binding for {@code T}
   * will be made available.
   */
  Key<T> getKeyForDefaultBinding() {
    checkConfiguration(!isInitialized(), "already initialized");      
    addDirectTypeBinding(binder);
    return defaultKey;
  }

  public LinkedBindingBuilder<T> setDefault() {
    return binder.bind(getKeyForDefaultBinding());
  }

  /**
   * Returns the key to use for the actual binding, overrides the default if set.
   * 
   * <p>As a side effect this installs support for the 'direct type', so a binding for {@code T}
   * will be made available.
   */
  Key<T> getKeyForActualBinding() {
    checkConfiguration(!isInitialized(), "already initialized");      
    addDirectTypeBinding(binder);
    return actualKey;
  }

  public LinkedBindingBuilder<T> setBinding() {
    return binder.bind(getKeyForActualBinding());
  }

  @Override public void configure(Binder binder) {
    checkConfiguration(!isInitialized(), "OptionalBinder was already initialized");

    binder.bind(optionalProviderKey).toProvider(new RealOptionalProviderProvider());

    // Optional is immutable, so it's safe to expose Optional<Provider<T>> as
    // Optional<javax.inject.Provider<T>> (since Guice provider implements javax Provider).
    @SuppressWarnings({"unchecked", "cast"})
    Key massagedOptionalProviderKey = (Key) optionalProviderKey;
    binder.bind(optionalJavaxProviderKey).to(massagedOptionalProviderKey);

    binder.bind(optionalKey).toProvider(new RealOptionalKeyProvider());

    // Bind the java-8 types if we know them.
    bindJava8Optional(binder);
  }

  @SuppressWarnings("unchecked")
  private void bindJava8Optional(Binder binder) {
    if (JAVA_OPTIONAL_CLASS != null) {
      binder.bind(javaOptionalKey).toProvider(new JavaOptionalProvider());
      binder.bind(javaOptionalProviderKey).toProvider(new JavaOptionalProviderProvider());
      // for the javax version we reuse the guice version since they're type-compatible.
      binder.bind(javaOptionalJavaxProviderKey).to(javaOptionalProviderKey);
    }
  }

  @SuppressWarnings("rawtypes")
  final class JavaOptionalProvider extends RealOptionalBinderProviderWithDependencies 
      implements ProviderWithExtensionVisitor, OptionalBinderBinding {
    private JavaOptionalProvider() {
      super(typeKey);
    }

    @Override public Object get() {
      Optional<Provider<T>> optional = optionalProviderT.get();
      try {
        if (optional.isPresent()) {
          return JAVA_OF_NULLABLE_METHOD.invoke(JAVA_OPTIONAL_CLASS, optional.get().get());
        } else {
          return JAVA_EMPTY_METHOD.invoke(JAVA_OPTIONAL_CLASS);
        }
      } catch (IllegalAccessException e) {
        throw new SecurityException(e);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(e);
      } catch (InvocationTargetException e) {
        throw Throwables.propagate(e.getCause());
      }
    }

    @Override public Set<Dependency<?>> getDependencies() {
      return dependencies;
    }

    @SuppressWarnings("unchecked")
    @Override public Object acceptExtensionVisitor(BindingTargetVisitor visitor,
        ProviderInstanceBinding binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    @Override public boolean containsElement(Element element) {
      return RealOptionalBinder.this.containsElement(element);
    }

    @Override public Binding getActualBinding() {
      return RealOptionalBinder.this.getActualBinding();
    }

    @Override public Binding getDefaultBinding() {
      return RealOptionalBinder.this.getDefaultBinding();
    }

    @Override public Key getKey() {
      return javaOptionalKey;
    }
  }

  @SuppressWarnings("rawtypes")
  final class JavaOptionalProviderProvider extends RealOptionalBinderProviderWithDependencies {
    private JavaOptionalProviderProvider() {
      super(typeKey);
    }

    @Override public Object get() {
      Optional<Provider<T>> optional = optionalProviderT.get();
      try {
        if (optional.isPresent()) {
          return JAVA_OF_NULLABLE_METHOD.invoke(JAVA_OPTIONAL_CLASS, optional.get());
        } else {
          return JAVA_EMPTY_METHOD.invoke(JAVA_OPTIONAL_CLASS);
        }
      } catch (IllegalAccessException e) {
        throw new SecurityException(e);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(e);
      } catch (InvocationTargetException e) {
        throw Throwables.propagate(e.getCause());
      }
    }

    @Override public Set<Dependency<?>> getDependencies() {
      return providerDependencies;
    }
  }

  final class RealDirectTypeProvider extends RealOptionalBinderProviderWithDependencies<T> {
    private RealDirectTypeProvider() {
      super(typeKey);
    }

    @Override public T get() {
      Optional<Provider<T>> optional = optionalProviderT.get();
      if (optional.isPresent()) {
        return optional.get().get();
      }
      // Let Guice handle blowing up if the injection point doesn't have @Nullable
      // (If it does have @Nullable, that's fine.  This would only happen if
      //  setBinding/setDefault themselves were bound to 'null').
      return null;
    }

    @Override public Set<Dependency<?>> getDependencies() {
      return dependencies;
    }
  }

  final class RealOptionalProviderProvider
      extends RealOptionalBinderProviderWithDependencies<Optional<Provider<T>>> {
    private Optional<Provider<T>> optional;

    private RealOptionalProviderProvider() {
      super(typeKey);
    }

    @Toolable @Inject void initialize(Injector injector) {
      RealOptionalBinder.this.binder = null;
      actualBinding = injector.getExistingBinding(actualKey);
      defaultBinding = injector.getExistingBinding(defaultKey);
      Binding<T> userBinding = injector.getExistingBinding(typeKey);
      Binding<T> binding = null;
      if (actualBinding != null) {
        // TODO(sameb): Consider exposing an option that will allow
        // ACTUAL to fallback to DEFAULT if ACTUAL's provider returns null.
        // Right now, an ACTUAL binding can convert from present -> absent
        // if it's bound to a provider that returns null.
        binding = actualBinding;
      } else if (defaultBinding != null) {
        binding = defaultBinding;
      } else if (userBinding != null) {
        // If neither the actual or default is set, then we fallback
        // to the value bound to the type itself and consider that the
        // "actual binding" for the SPI.
        binding = userBinding;
        actualBinding = userBinding;
      }
        
      if (binding != null) {
        optional = Optional.of(binding.getProvider());
        RealOptionalBinder.this.dependencies =
            ImmutableSet.<Dependency<?>>of(Dependency.get(binding.getKey()));
        RealOptionalBinder.this.providerDependencies =
            ImmutableSet.<Dependency<?>>of(Dependency.get(providerOf(binding.getKey())));
      } else {
        optional = Optional.absent();
        RealOptionalBinder.this.dependencies = ImmutableSet.of();
        RealOptionalBinder.this.providerDependencies = ImmutableSet.of();
      }
    }
      
    @Override public Optional<Provider<T>> get() {
      return optional;
    }

    @Override public Set<Dependency<?>> getDependencies() {
      return providerDependencies;
    }
  }
  
  final class RealOptionalKeyProvider
      extends RealOptionalBinderProviderWithDependencies<Optional<T>>
      implements ProviderWithExtensionVisitor<Optional<T>>,
          OptionalBinderBinding<Optional<T>>,
          Provider<Optional<T>> {
    private RealOptionalKeyProvider() {
      super(typeKey);
    }
    
    @Override public Optional<T> get() {
      Optional<Provider<T>> optional = optionalProviderT.get();
      if (optional.isPresent()) {
        return Optional.fromNullable(optional.get().get());
      } else {
        return Optional.absent();
      }
    }

    @Override public Set<Dependency<?>> getDependencies() {
      return dependencies;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <B, R> R acceptExtensionVisitor(BindingTargetVisitor<B, R> visitor,
        ProviderInstanceBinding<? extends B> binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor<Optional<T>, R>) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    @Override public Key<Optional<T>> getKey() {
      return optionalKey;
    }

    @Override public Binding<?> getActualBinding() {
      return RealOptionalBinder.this.getActualBinding();
    }

    @Override public Binding<?> getDefaultBinding() {
      return RealOptionalBinder.this.getDefaultBinding();
    }

    @Override public boolean containsElement(Element element) {
      return RealOptionalBinder.this.containsElement(element);
    }
  }

  private Binding<?> getActualBinding() {
    if (isInitialized()) {
      return actualBinding;
    } else {
      throw new UnsupportedOperationException(
          "getActualBinding() not supported from Elements.getElements, requires an Injector.");
    }
  }

  private Binding<?> getDefaultBinding() {
    if (isInitialized()) {
      return defaultBinding;
    } else {
      throw new UnsupportedOperationException(
          "getDefaultBinding() not supported from Elements.getElements, requires an Injector.");
    }
  }

  private boolean containsElement(Element element) {
    Key<?> elementKey;
    if (element instanceof Binding) {
      elementKey = ((Binding<?>) element).getKey();
    } else if (element instanceof ProviderLookup) {
      elementKey = ((ProviderLookup<?>) element).getKey();
    } else {
      return false; // cannot match;
    }

    return elementKey.equals(optionalKey)
        || elementKey.equals(optionalProviderKey)
        || elementKey.equals(optionalJavaxProviderKey)
        || elementKey.equals(defaultKey)
        || elementKey.equals(actualKey)
        || matchesJ8Keys(elementKey)
        || matchesTypeKey(element, elementKey);
  }

  private boolean matchesJ8Keys(Key<?> elementKey) {
    if (JAVA_OPTIONAL_CLASS != null) {
      return elementKey.equals(javaOptionalKey)
          || elementKey.equals(javaOptionalProviderKey)
          || elementKey.equals(javaOptionalJavaxProviderKey);
    }
    return false;
  }
  
  /** Returns true if the key & element indicate they were bound by this OptionalBinder. */
  private boolean matchesTypeKey(Element element, Key<?> elementKey) {
    // Just doing .equals(typeKey) isn't enough, because the user can bind that themselves.
    return elementKey.equals(typeKey)
        && element instanceof ProviderInstanceBinding
        && (((ProviderInstanceBinding) element)
            .getUserSuppliedProvider() instanceof RealOptionalBinderProviderWithDependencies);
  }

  private boolean isInitialized() {
    return binder == null;
  }

  @Override public boolean equals(Object o) {
    return o instanceof RealOptionalBinder
        && ((RealOptionalBinder<?>) o).typeKey.equals(typeKey);
  }

  @Override public int hashCode() {
    return typeKey.hashCode();
  }

  /**
   * A base class for ProviderWithDependencies that need equality based on a specific object.
   */
  private abstract static class RealOptionalBinderProviderWithDependencies<T> implements
      ProviderWithDependencies<T> {
    private final Object equality;

    public RealOptionalBinderProviderWithDependencies(Object equality) {
      this.equality = equality;
    }

    @Override public boolean equals(Object obj) {
      return obj != null && this.getClass() == obj.getClass()
          && equality.equals(((RealOptionalBinderProviderWithDependencies<?>) obj).equality);
    }

    @Override public int hashCode() {
      return equality.hashCode();
    }
  }
  
  static class DefaultImpl extends BaseAnnotation implements Default {
    public DefaultImpl(String value) {
      super(Default.class, value);
    }
  }
  
  static class ActualImpl extends BaseAnnotation implements Actual {
    public ActualImpl(String value) {
      super(Actual.class, value);
    }
  }
  
  abstract static class BaseAnnotation implements Serializable, Annotation {

    private final String value;
    private final Class<? extends Annotation> clazz;

    BaseAnnotation(Class<? extends Annotation> clazz, String value) {
      this.clazz = checkNotNull(clazz, "clazz");
      this.value = checkNotNull(value, "value");
    }

    public String value() {
      return this.value;
    }

    @Override public int hashCode() {
      // This is specified in java.lang.Annotation.
      return (127 * "value".hashCode()) ^ value.hashCode();
    }

    @Override public boolean equals(Object o) {
      // We check against each annotation type instead of BaseAnnotation
      // so that we can compare against generated annotation implementations. 
      if (o instanceof Actual && clazz == Actual.class) {
        Actual other = (Actual) o;
        return value.equals(other.value());
      } else if (o instanceof Default && clazz == Default.class) {
        Default other = (Default) o;
        return value.equals(other.value());
      }
      return false;
    }

    @Override public String toString() {
      return "@" + clazz.getName() + (value.isEmpty() ? "" : "(value=" + value + ")");
    }

    @Override public Class<? extends Annotation> annotationType() {
      return clazz;
    }

    private static final long serialVersionUID = 0;
  }
}
