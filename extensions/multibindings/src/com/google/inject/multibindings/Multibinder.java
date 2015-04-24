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

package com.google.inject.multibindings;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.primitives.Ints.MAX_POWER_OF_TWO;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.inject.multibindings.Element.Type.MULTIBINDER;
import static com.google.inject.name.Names.named;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Errors;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.Message;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Types;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An API to bind multiple values separately, only to later inject them as a
 * complete collection. Multibinder is intended for use in your application's
 * module:
 * <pre><code>
 * public class SnacksModule extends AbstractModule {
 *   protected void configure() {
 *     Multibinder&lt;Snack&gt; multibinder
 *         = Multibinder.newSetBinder(binder(), Snack.class);
 *     multibinder.addBinding().toInstance(new Twix());
 *     multibinder.addBinding().toProvider(SnickersProvider.class);
 *     multibinder.addBinding().to(Skittles.class);
 *   }
 * }</code></pre>
 *
 * <p>With this binding, a {@link Set}{@code <Snack>} can now be injected:
 * <pre><code>
 * class SnackMachine {
 *   {@literal @}Inject
 *   public SnackMachine(Set&lt;Snack&gt; snacks) { ... }
 * }</code></pre>
 *
 * If desired, {@link Collection}{@code <Provider<Snack>>} can also be injected.
 *
 * <p>Contributing multibindings from different modules is supported. For
 * example, it is okay for both {@code CandyModule} and {@code ChipsModule}
 * to create their own {@code Multibinder<Snack>}, and to each contribute
 * bindings to the set of snacks. When that set is injected, it will contain
 * elements from both modules.
 *
 * <p>The set's iteration order is consistent with the binding order. This is
 * convenient when multiple elements are contributed by the same module because
 * that module can order its bindings appropriately. Avoid relying on the
 * iteration order of elements contributed by different modules, since there is
 * no equivalent mechanism to order modules.
 *
 * <p>The set is unmodifiable.  Elements can only be added to the set by
 * configuring the multibinder.  Elements can never be removed from the set.
 *
 * <p>Elements are resolved at set injection time. If an element is bound to a
 * provider, that provider's get method will be called each time the set is
 * injected (unless the binding is also scoped).
 *
 * <p>Annotations are be used to create different sets of the same element
 * type. Each distinct annotation gets its own independent collection of
 * elements.
 *
 * <p><strong>Elements must be distinct.</strong> If multiple bound elements
 * have the same value, set injection will fail.
 *
 * <p><strong>Elements must be non-null.</strong> If any set element is null,
 * set injection will fail.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class Multibinder<T> {
  private Multibinder() {}

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with no binding annotation.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, TypeLiteral<T> type) {
    return newRealSetBinder(binder, Key.get(type));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with no binding annotation.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, Class<T> type) {
    return newRealSetBinder(binder, Key.get(type));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotation}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, TypeLiteral<T> type, Annotation annotation) {
    return newRealSetBinder(binder, Key.get(type, annotation));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotation}.
   */
  public static <T> Multibinder<T> newSetBinder(
      Binder binder, Class<T> type, Annotation annotation) {
    return newRealSetBinder(binder, Key.get(type, annotation));
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotationType}.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, TypeLiteral<T> type,
      Class<? extends Annotation> annotationType) {
    return newRealSetBinder(binder, Key.get(type, annotationType));
  }

  /**
   * Returns a new multibinder that collects instances of the key's type in a {@link Set} that is
   * itself bound with the annotation (if any) of the key.
   *
   * @since 4.0
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, Key<T> key) {
    return newRealSetBinder(binder, key);
  }

  /**
   * Implementation of newSetBinder.
   */
  static <T> RealMultibinder<T> newRealSetBinder(Binder binder, Key<T> key) {
    binder = binder.skipSources(RealMultibinder.class, Multibinder.class);
    RealMultibinder<T> result = new RealMultibinder<T>(binder, key.getTypeLiteral(),
        key.ofType(setOf(key.getTypeLiteral())));
    binder.install(result);
    return result;
  }

  /**
   * Returns a new multibinder that collects instances of {@code type} in a {@link Set} that is
   * itself bound with {@code annotationType}.
   */
  public static <T> Multibinder<T> newSetBinder(Binder binder, Class<T> type,
      Class<? extends Annotation> annotationType) {
    return newSetBinder(binder, Key.get(type, annotationType));
  }

  @SuppressWarnings("unchecked") // wrapping a T in a Set safely returns a Set<T>
  static <T> TypeLiteral<Set<T>> setOf(TypeLiteral<T> elementType) {
    Type type = Types.setOf(elementType.getType());
    return (TypeLiteral<Set<T>>) TypeLiteral.get(type);
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Collection<Provider<T>>> collectionOfProvidersOf(
      TypeLiteral<T> elementType) {
    Type providerType = Types.providerOf(elementType.getType());
    Type type = Types.newParameterizedType(Collection.class, providerType);
    return (TypeLiteral<Collection<Provider<T>>>) TypeLiteral.get(type);
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Collection<javax.inject.Provider<T>>> collectionOfJavaxProvidersOf(
      TypeLiteral<T> elementType) {
    Type providerType =
        Types.newParameterizedType(javax.inject.Provider.class, elementType.getType());
    Type type = Types.newParameterizedType(Collection.class, providerType);
    return (TypeLiteral<Collection<javax.inject.Provider<T>>>) TypeLiteral.get(type);
  }

  /**
   * Configures the bound set to silently discard duplicate elements. When multiple equal values are
   * bound, the one that gets included is arbitrary. When multiple modules contribute elements to
   * the set, this configuration option impacts all of them.
   *
   * @return this multibinder
   * @since 3.0
   */
  public abstract Multibinder<T> permitDuplicates();

  /**
   * Returns a binding builder used to add a new element in the set. Each
   * bound element must have a distinct value. Bound providers will be
   * evaluated each time the set is injected.
   *
   * <p>It is an error to call this method without also calling one of the
   * {@code to} methods on the returned binding builder.
   *
   * <p>Scoping elements independently is supported. Use the {@code in} method
   * to specify a binding scope.
   */
  public abstract LinkedBindingBuilder<T> addBinding();

  /**
   * The actual multibinder plays several roles:
   *
   * <p>As a Multibinder, it acts as a factory for LinkedBindingBuilders for
   * each of the set's elements. Each binding is given an annotation that
   * identifies it as a part of this set.
   *
   * <p>As a Module, it installs the binding to the set itself. As a module,
   * this implements equals() and hashcode() in order to trick Guice into
   * executing its configure() method only once. That makes it so that
   * multiple multibinders can be created for the same target collection, but
   * only one is bound. Since the list of bindings is retrieved from the
   * injector itself (and not the multibinder), each multibinder has access to
   * all contributions from all multibinders.
   *
   * <p>As a Provider, this constructs the set instances.
   *
   * <p>We use a subclass to hide 'implements Module, Provider' from the public
   * API.
   */
  static final class RealMultibinder<T> extends Multibinder<T>
      implements Module, ProviderWithExtensionVisitor<Set<T>>, HasDependencies,
          MultibinderBinding<Set<T>> {

    private final TypeLiteral<T> elementType;
    private final String setName;
    private final Key<Set<T>> setKey;
    private final Key<Collection<Provider<T>>> collectionOfProvidersKey;
    private final Key<Collection<javax.inject.Provider<T>>> collectionOfJavaxProvidersKey;
    private final Key<Boolean> permitDuplicatesKey;

    /* the target injector's binder. non-null until initialization, null afterwards */
    private Binder binder;

    /* a binding for each element in the set. null until initialization, non-null afterwards */
    private ImmutableList<Binding<T>> bindings;
    private Set<Dependency<?>> dependencies;

    /** whether duplicates are allowed. Possibly configured by a different instance */
    private boolean permitDuplicates;

    private RealMultibinder(Binder binder, TypeLiteral<T> elementType, Key<Set<T>> setKey) {
      this.binder = checkNotNull(binder, "binder");
      this.elementType = checkNotNull(elementType, "elementType");
      this.setKey = checkNotNull(setKey, "setKey");
      this.collectionOfProvidersKey = setKey.ofType(collectionOfProvidersOf(elementType));
      this.collectionOfJavaxProvidersKey = setKey.ofType(collectionOfJavaxProvidersOf(elementType));
      this.setName = RealElement.nameOf(setKey);
      this.permitDuplicatesKey = Key.get(Boolean.class, named(toString() + " permits duplicates"));
    }

    public void configure(Binder binder) {
      checkConfiguration(!isInitialized(), "Multibinder was already initialized");

      binder.bind(setKey).toProvider(this);
      binder.bind(collectionOfProvidersKey).toProvider(
          new RealMultibinderCollectionOfProvidersProvider());

      // The collection this exposes is internally an ImmutableList, so it's OK to massage
      // the guice Provider to javax Provider in the value (since the guice Provider implements
      // javax Provider).
      @SuppressWarnings("unchecked")
      Key key = (Key) collectionOfProvidersKey;
      binder.bind(collectionOfJavaxProvidersKey).to(key);
    }

    @Override public Multibinder<T> permitDuplicates() {
      binder.install(new PermitDuplicatesModule(permitDuplicatesKey));
      return this;
    }
    
    Key<T> getKeyForNewItem() {
      checkConfiguration(!isInitialized(), "Multibinder was already initialized");
      return Key.get(elementType, new RealElement(setName, MULTIBINDER, ""));
    }

    @Override public LinkedBindingBuilder<T> addBinding() {
      return binder.bind(getKeyForNewItem());
    }

    /**
     * Invoked by Guice at Injector-creation time to prepare providers for each
     * element in this set. At this time the set's size is known, but its
     * contents are only evaluated when get() is invoked.
     */
    @Toolable @Inject void initialize(Injector injector) {
      List<Binding<T>> bindings = Lists.newArrayList();
      Set<Indexer.IndexedBinding> index = Sets.newHashSet();
      Indexer indexer = new Indexer(injector);
      List<Dependency<?>> dependencies = Lists.newArrayList();
      for (Binding<?> entry : injector.findBindingsByType(elementType)) {
        if (keyMatches(entry.getKey())) {
          @SuppressWarnings("unchecked") // protected by findBindingsByType()
          Binding<T> binding = (Binding<T>) entry;
          if (index.add(binding.acceptTargetVisitor(indexer))) {
            bindings.add(binding);
            dependencies.add(Dependency.get(binding.getKey()));
          }
        }
      }

      this.bindings = ImmutableList.copyOf(bindings);
      this.dependencies = ImmutableSet.copyOf(dependencies);
      this.permitDuplicates = permitsDuplicates(injector);
      this.binder = null;
    }

    // This is forked from com.google.common.collect.Maps.capacity 
    private static int mapCapacity(int numBindings) {
      if (numBindings < 3) {
        return numBindings + 1;
      } else  if (numBindings < MAX_POWER_OF_TWO) {
        return (int) (numBindings / 0.75F + 1.0F);
      }
      return Integer.MAX_VALUE;
    }

    boolean permitsDuplicates(Injector injector) {
      return injector.getBindings().containsKey(permitDuplicatesKey);
    }

    private boolean keyMatches(Key<?> key) {
      return key.getTypeLiteral().equals(elementType)
          && key.getAnnotation() instanceof Element
          && ((Element) key.getAnnotation()).setName().equals(setName)
          && ((Element) key.getAnnotation()).type() == MULTIBINDER;
    }

    private boolean isInitialized() {
      return binder == null;
    }

    public Set<T> get() {
      checkConfiguration(isInitialized(), "Multibinder is not initialized");

      Map<T, Binding<T>> result = new LinkedHashMap<T, Binding<T>>(mapCapacity(bindings.size()));
      for (Binding<T> binding : bindings) {
        final T newValue = binding.getProvider().get();
        checkConfiguration(newValue != null,
            "Set injection failed due to null element bound at: %s",
            binding.getSource());
        Binding<T> duplicateBinding = result.put(newValue, binding);
        if (!permitDuplicates && duplicateBinding != null) {
          throw newDuplicateValuesException(result, binding, newValue, duplicateBinding);
        }
      }
      return ImmutableSet.copyOf(result.keySet());
    }

    @SuppressWarnings("unchecked")
    public <B, V> V acceptExtensionVisitor(
        BindingTargetVisitor<B, V> visitor,
        ProviderInstanceBinding<? extends B> binding) {
      if (visitor instanceof MultibindingsTargetVisitor) {
        return ((MultibindingsTargetVisitor<Set<T>, V>) visitor).visit(this);
      } else {
        return visitor.visit(binding);
      }
    }

    String getSetName() {
      return setName;
    }

    public TypeLiteral<?> getElementTypeLiteral() {
      return elementType;
    }

    public Key<Set<T>> getSetKey() {
      return setKey;
    }

    @SuppressWarnings("unchecked")
    public List<Binding<?>> getElements() {
      if (isInitialized()) {
        return (List<Binding<?>>) (List<?>) bindings; // safe because bindings is immutable.
      } else {
        throw new UnsupportedOperationException("getElements() not supported for module bindings");
      }
    }

    public boolean permitsDuplicates() {
      if (isInitialized()) {
        return permitDuplicates;
      } else {
        throw new UnsupportedOperationException(
            "permitsDuplicates() not supported for module bindings");
      }
    }

    public boolean containsElement(com.google.inject.spi.Element element) {
      if (element instanceof Binding) {
        Binding<?> binding = (Binding<?>) element;
        return keyMatches(binding.getKey())
            || binding.getKey().equals(permitDuplicatesKey)
            || binding.getKey().equals(setKey)
            || binding.getKey().equals(collectionOfProvidersKey)
            || binding.getKey().equals(collectionOfJavaxProvidersKey);
      } else {
        return false;
      }
    }

    public Set<Dependency<?>> getDependencies() {
      if (!isInitialized()) {
        return ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));
      } else {
        return dependencies;
      }
    }

    @Override public boolean equals(Object o) {
      return o instanceof RealMultibinder
          && ((RealMultibinder<?>) o).setKey.equals(setKey);
    }

    @Override public int hashCode() {
      return setKey.hashCode();
    }

    @Override public String toString() {
      return (setName.isEmpty() ? "" : setName + " ") + "Multibinder<" + elementType + ">";
    }

    final class RealMultibinderCollectionOfProvidersProvider
        implements ProviderWithDependencies<Collection<Provider<T>>> {
      @Override public Collection<Provider<T>> get() {
        checkConfiguration(isInitialized(), "Multibinder is not initialized");
        int size = bindings.size();
        @SuppressWarnings("unchecked")  // safe because we only put Provider<T> into it.
        Provider<T>[] providers = new Provider[size];
        for (int i = 0; i < size; i++) {
          providers[i] = bindings.get(i).getProvider();
        }
        return ImmutableList.copyOf(providers);
      }

      @Override public Set<Dependency<?>> getDependencies() {
        if (!isInitialized()) {
          return ImmutableSet.<Dependency<?>>of(Dependency.get(Key.get(Injector.class)));
        }
        ImmutableSet.Builder<Dependency<?>> setBuilder = ImmutableSet.builder();
        for (Dependency<?> dependency : dependencies) {
          Key key = dependency.getKey();
          setBuilder.add(
              Dependency.get(key.ofType(Types.providerOf(key.getTypeLiteral().getType()))));
        }
        return setBuilder.build();
      }

      Key getCollectionKey() {
        return RealMultibinder.this.collectionOfProvidersKey;
      }

      @Override public boolean equals(Object o) {
        return o instanceof Multibinder.RealMultibinder.RealMultibinderCollectionOfProvidersProvider
            && ((Multibinder.RealMultibinder.RealMultibinderCollectionOfProvidersProvider) o)
                .getCollectionKey().equals(getCollectionKey());
      }

      @Override public int hashCode() {
        return getCollectionKey().hashCode();
      }
    }
  }

  /**
   * We install the permit duplicates configuration as its own binding, all by itself. This way,
   * if only one of a multibinder's users remember to call permitDuplicates(), they're still
   * permitted.
   */
  private static class PermitDuplicatesModule extends AbstractModule {
    private final Key<Boolean> key;

    PermitDuplicatesModule(Key<Boolean> key) {
      this.key = key;
    }

    @Override protected void configure() {
      bind(key).toInstance(true);
    }

    @Override public boolean equals(Object o) {
      return o instanceof PermitDuplicatesModule
          && ((PermitDuplicatesModule) o).key.equals(key);
    }

    @Override public int hashCode() {
      return getClass().hashCode() ^ key.hashCode();
    }
  }

  static void checkConfiguration(boolean condition, String format, Object... args) {
    if (condition) {
      return;
    }

    throw new ConfigurationException(ImmutableSet.of(new Message(Errors.format(format, args))));
  }

  private static <T> ConfigurationException newDuplicateValuesException(
      Map<T, Binding<T>> existingBindings,
      Binding<T> binding,
      final T newValue,
      Binding<T> duplicateBinding) {
    T oldValue = getOnlyElement(filter(existingBindings.keySet(), equalTo(newValue)));
    String oldString = oldValue.toString();
    String newString = newValue.toString();
    if (Objects.equal(oldString, newString)) {
      // When the value strings match, just show the source of the bindings
      return new ConfigurationException(ImmutableSet.of(new Message(Errors.format(
          "Set injection failed due to duplicated element \"%s\""
              + "\n    Bound at %s\n    Bound at %s",
          newValue,
          duplicateBinding.getSource(),
          binding.getSource()))));
    } else {
      // When the value strings don't match, include them both as they may be useful for debugging
      return new ConfigurationException(ImmutableSet.of(new Message(Errors.format(
          "Set injection failed due to multiple elements comparing equal:"
              + "\n    \"%s\"\n        bound at %s"
              + "\n    \"%s\"\n        bound at %s",
          oldValue,
          duplicateBinding.getSource(),
          newValue,
          binding.getSource()))));
    }
  }

  static <T> T checkNotNull(T reference, String name) {
    if (reference != null) {
      return reference;
    }

    NullPointerException npe = new NullPointerException(name);
    throw new ConfigurationException(ImmutableSet.of(
        new Message(npe.toString(), npe)));
  }
}
