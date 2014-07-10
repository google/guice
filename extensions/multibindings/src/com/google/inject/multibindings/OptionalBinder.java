/**
 * Copyright (C) 2014 Google Inc.
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.internal.RehashableKeys.Keys.needsRehashing;
import static com.google.inject.internal.RehashableKeys.Keys.rehash;
import static com.google.inject.multibindings.Multibinder.checkConfiguration;
import static com.google.inject.util.Types.newParameterizedType;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.internal.Errors;
import com.google.inject.multibindings.Element.Type;
import com.google.inject.multibindings.MapBinder.RealMapBinder;
import com.google.inject.spi.BindingTargetVisitor;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.HasDependencies;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderLookup;
import com.google.inject.spi.ProviderWithDependencies;
import com.google.inject.spi.ProviderWithExtensionVisitor;
import com.google.inject.spi.Toolable;
import com.google.inject.util.Types;

import java.util.Map;
import java.util.Set;


/**
 * An API to bind optional values, optionally with a default value.
 * OptionalBinder fulfills two roles: <ol>
 * <li>It allows a framework to define an injection point that may or
 *     may not be bound by users.
 * <li>It allows a framework to supply a default value that can be changed
 *     by users.
 * </ol>
 * 
 * <p>When an OptionalBinder is added, it will always supply the bindings:
 * {@code Optional<T>} and {@code Optional<Provider<T>>}.  If
 * {@link #setBinding} or {@link #setDefault} are called, it will also
 * bind {@code T}.
 * 
 * <p>{@code setDefault} is intended for use by frameworks that need a default
 * value.  User code can call {@code setBinding} to override the default.
 * <b>Warning: Even if setBinding is called, the default binding
 * will still exist in the object graph.  If it is a singleton, it will be
 * instantiated in {@code Stage.PRODUCTION}.</b>
 * 
 * <p>If setDefault or setBinding are linked to Providers, the Provider may return
 * {@code null}.  If it does, the Optional bindings will be absent.  Binding
 * setBinding to a Provider that returns null will not cause OptionalBinder
 * to fall back to the setDefault binding.
 * 
 * <p>If neither setDefault nor setBinding are called, the optionals will be
 * absent.  Otherwise, the optionals will return present if they are bound
 * to a non-null value.
 *
 * <p>Values are resolved at injection time. If a value is bound to a
 * provider, that provider's get method will be called each time the optional
 * is injected (unless the binding is also scoped, or an optional of provider is
 * injected).
 * 
 * <p>Annotations are used to create different optionals of the same key/value
 * type. Each distinct annotation gets its own independent binding.
 *  
 * <pre><code>
 * public class FrameworkModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Renamer.class);
 *   }
 * }</code></pre>
 *
 * <p>With this module, an {@link Optional}{@code <Renamer>} can now be
 * injected.  With no other bindings, the optional will be absent.  However,
 * once a user adds a binding:
 * 
 * <pre><code>
 * public class UserRenamerModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Renamer.class)
 *         .setBinding().to(ReplacingRenamer.class);
 *   }
 * }</code></pre>
 * .. then the {@code Optional<Renamer>} will be present and supply the
 * ReplacingRenamer.
 * 
 * <p>Default values can be supplied using:
 * <pre><code>
 * public class FrameworkModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, LookupUrl.class))
 *         .setDefault().to(DEFAULT_LOOKUP_URL);
 *   }
 * }</code></pre>
 * With the above module, code can inject an {@code @LookupUrl String} and it
 * will supply the DEFAULT_LOOKUP_URL.  A user can change this value by binding
 * <pre><code>
 * public class UserLookupModule extends AbstractModule {
 *   protected void configure() {
 *     OptionalBinder.newOptionalBinder(binder(), Key.get(String.class, LookupUrl.class))
 *         .setBinding().to(CUSTOM_LOOKUP_URL);
 *   }
 * }</code></pre>
 * ... which will override the default value.
 *
 * @author sameb@google.com (Sam Berlin)
 */
public abstract class OptionalBinder<T> {
  private OptionalBinder() {}

  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, Class<T> type) {
    return newOptionalBinder(binder, Key.get(type));
  }
  
  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, TypeLiteral<T> type) {
    return newOptionalBinder(binder, Key.get(type));
  }
  
  public static <T> OptionalBinder<T> newOptionalBinder(Binder binder, Key<T> type) {
    binder = binder.skipSources(OptionalBinder.class, RealOptionalBinder.class);
    RealOptionalBinder<T> optionalBinder = new RealOptionalBinder<T>(binder, type);
    binder.install(optionalBinder);
    return optionalBinder;
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<T>> optionalOf(
      TypeLiteral<T> type) {
    return (TypeLiteral<Optional<T>>) TypeLiteral.get(
        Types.newParameterizedType(Optional.class,  type.getType()));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<javax.inject.Provider<T>>> optionalOfJavaxProvider(
      TypeLiteral<T> type) {
    return (TypeLiteral<Optional<javax.inject.Provider<T>>>) TypeLiteral.get(
        Types.newParameterizedType(Optional.class,
            newParameterizedType(javax.inject.Provider.class, type.getType())));
  }

  @SuppressWarnings("unchecked")
  static <T> TypeLiteral<Optional<Provider<T>>> optionalOfProvider(TypeLiteral<T> type) {
    return (TypeLiteral<Optional<Provider<T>>>) TypeLiteral.get(Types.newParameterizedType(
        Optional.class, newParameterizedType(Provider.class, type.getType())));
  }

  /**
   * Returns a binding builder used to set the default value that will be injected.
   * The binding set by this method will be ignored if {@link #setBinding} is called.
   * 
   * <p>It is an error to call this method without also calling one of the {@code to}
   * methods on the returned binding builder. 
   */
  public abstract LinkedBindingBuilder<T> setDefault();


  /**
   * Returns a binding builder used to set the actual value that will be injected.
   * This overrides any binding set by {@link #setDefault}.
   * 
   * <p>It is an error to call this method without also calling one of the {@code to}
   * methods on the returned binding builder. 
   */
  public abstract LinkedBindingBuilder<T> setBinding();
  
  enum Source { DEFAULT, ACTUAL }

  /**
   * The actual OptionalBinder plays several roles.  It implements Module to hide that
   * fact from the public API, and installs the various bindings that are exposed to the user.
   */
  static final class RealOptionalBinder<T> extends OptionalBinder<T> implements Module {
    private final Key<T> typeKey;
    private final Key<Optional<T>> optionalKey;
    private final Key<Optional<javax.inject.Provider<T>>> optionalJavaxProviderKey;
    private final Key<Optional<Provider<T>>> optionalProviderKey;
    private final Key<Map<Source, Provider<T>>> mapKey;
    private final RealMapBinder<Source, T> mapBinder;
    private final Set<Dependency<?>> dependencies;
    private final Provider<Optional<Provider<T>>> optionalProviderT;
    

    /** the target injector's binder. non-null until initialization, null afterwards */
    private Binder binder;
    /** the default binding, for the SPI. */
    private Binding<?> defaultBinding;
    /** the actual binding, for the SPI */
    private Binding<?> actualBinding;

    private RealOptionalBinder(Binder binder, Key<T> typeKey) {
      this.binder = binder;
      this.typeKey = checkNotNull(typeKey);
      TypeLiteral<T> literal = typeKey.getTypeLiteral();
      this.optionalKey = typeKey.ofType(optionalOf(literal));
      this.optionalJavaxProviderKey = typeKey.ofType(optionalOfJavaxProvider(literal));
      this.optionalProviderKey = typeKey.ofType(optionalOfProvider(literal));
      this.mapKey =
          typeKey.ofType(MapBinder.mapOfProviderOf(TypeLiteral.get(Source.class), literal));
      this.dependencies = ImmutableSet.<Dependency<?>>of(Dependency.get(mapKey));
      this.optionalProviderT = binder.getProvider(optionalProviderKey);
      if (typeKey.getAnnotation() != null) {
        this.mapBinder = (RealMapBinder<Source, T>) MapBinder.newMapBinder(binder,
            TypeLiteral.get(Source.class), typeKey.getTypeLiteral(), typeKey.getAnnotation());
      } else if (typeKey.getAnnotationType() != null) {
        this.mapBinder = (RealMapBinder<Source, T>) MapBinder.newMapBinder(binder,
            TypeLiteral.get(Source.class), typeKey.getTypeLiteral(), typeKey.getAnnotationType());
      } else {
        this.mapBinder = (RealMapBinder<Source, T>) MapBinder.newMapBinder(binder,
            TypeLiteral.get(Source.class), typeKey.getTypeLiteral());
      }
      mapBinder.updateDuplicateKeyMessage(Source.DEFAULT, "OptionalBinder for "
          + Errors.convert(typeKey)
          + " called with different setDefault values, from bindings:\n");
      mapBinder.updateDuplicateKeyMessage(Source.ACTUAL, "OptionalBinder for "
          + Errors.convert(typeKey)
          + " called with different setBinding values, from bindings:\n");
    }
    
    /**
     * Adds a binding for T. Multiple calls to this are safe, and will be collapsed as duplicate
     * bindings.
     */
    private void addDirectTypeBinding(Binder binder) {
      binder.bind(typeKey).toProvider(new RealOptionalBinderProviderWithDependencies<T>(typeKey) {
        public T get() {
          Optional<Provider<T>> optional = optionalProviderT.get();
          if (optional.isPresent()) {
            return optional.get().get();
          }
          // Let Guice handle blowing up if the injection point doesn't have @Nullable
          // (If it does have @Nullable, that's fine.  This would only happen if
          //  setBinding/setDefault themselves were bound to 'null').
          return null; 
        }

        public Set<Dependency<?>> getDependencies() {
          return dependencies;
        }
      });
    }

    @Override public LinkedBindingBuilder<T> setDefault() {
      checkConfiguration(!isInitialized(), "already initialized");
      
      addDirectTypeBinding(binder);

      RealElement.BindingBuilder<T> valueBinding = RealElement.addBinding(binder,
          Element.Type.OPTIONALBINDER, typeKey.getTypeLiteral(), RealElement.nameOf(typeKey));
      Key<T> valueKey = Key.get(typeKey.getTypeLiteral(), valueBinding.getAnnotation());
      mapBinder.addBinding(Source.DEFAULT).toProvider(
          new ValueProvider<T>(valueKey, binder.getProvider(valueKey)));
      return valueBinding;
    }

    @Override public LinkedBindingBuilder<T> setBinding() {
      checkConfiguration(!isInitialized(), "already initialized");
      
      addDirectTypeBinding(binder);

      RealElement.BindingBuilder<T> valueBinding = RealElement.addBinding(binder,
          Element.Type.OPTIONALBINDER, typeKey.getTypeLiteral(), RealElement.nameOf(typeKey));
      Key<T> valueKey = Key.get(typeKey.getTypeLiteral(), valueBinding.getAnnotation());
      mapBinder.addBinding(Source.ACTUAL).toProvider(
          new ValueProvider<T>(valueKey, binder.getProvider(valueKey)));
      return valueBinding;
    }
    
    /**
     * Traverses through the dependencies of the providers in order to get to the user's binding.
     */
    private Binding<?> getBindingFromMapProvider(Injector injector, Provider<T> mapProvider) {
      HasDependencies deps = (HasDependencies) mapProvider;
      Key<?> depKey = Iterables.getOnlyElement(deps.getDependencies()).getKey();
      // The dep flow is (and will stay this way, until we change the internals) --
      //    Key[type=Provider<java.lang.String>, annotation=@Element(type=MAPBINDER)]
      // -> Key[type=String, annotation=@Element(type=MAPBINDER)]
      // -> Key[type=Provider<String>, annotation=@Element(type=OPTIONALBINDER)]
      // -> Key[type=String, annotation=@Element(type=OPTIONALBINDER)]
      // The last one points to the user's binding.
      for (int i = 0; i < 3; i++) {
        deps = (HasDependencies) injector.getBinding(depKey);
        depKey = Iterables.getOnlyElement(deps.getDependencies()).getKey();
      }
      return injector.getBinding(depKey);
    }

    public void configure(Binder binder) {
      checkConfiguration(!isInitialized(), "OptionalBinder was already initialized");

      final Provider<Map<Source, Provider<T>>> mapProvider = binder.getProvider(mapKey);
      binder.bind(optionalProviderKey).toProvider(
          new RealOptionalBinderProviderWithDependencies<Optional<Provider<T>>>(typeKey) {
        private Optional<Provider<T>> optional;

        @Toolable @Inject void initialize(Injector injector) {
          RealOptionalBinder.this.binder = null;
          Map<Source, Provider<T>> map = mapProvider.get();
          // Map might be null if duplicates prevented MapBinder from initializing
          if (map != null) {
            if (map.containsKey(Source.ACTUAL)) {
              // TODO(sameb): Consider exposing an option that will allow
              // ACTUAL to fallback to DEFAULT if ACTUAL's provider returns null.
              // Right now, an ACTUAL binding can convert from present -> absent
              // if it's bound to a provider that returns null.
              optional = Optional.fromNullable(map.get(Source.ACTUAL)); 
            } else if (map.containsKey(Source.DEFAULT)) {
              optional = Optional.fromNullable(map.get(Source.DEFAULT));
            } else {
              optional = Optional.absent();
            }
            
            // Also set up the bindings for the SPI.
            if (map.containsKey(Source.ACTUAL)) {
              actualBinding = getBindingFromMapProvider(injector, map.get(Source.ACTUAL));
            }
            if (map.containsKey(Source.DEFAULT)) {
              defaultBinding = getBindingFromMapProvider(injector, map.get(Source.DEFAULT));
            }
          }
        }
        
        public Optional<Provider<T>> get() {
          return optional;
        }

        public Set<Dependency<?>> getDependencies() {
          return dependencies;
        }
      });
      
      // Optional is immutable, so it's safe to expose Optional<Provider<T>> as
      // Optional<javax.inject.Provider<T>> (since Guice provider implements javax Provider).
      @SuppressWarnings({"unchecked", "cast"})
      Key massagedOptionalProviderKey = (Key) optionalProviderKey;
      binder.bind(optionalJavaxProviderKey).to(massagedOptionalProviderKey);

      binder.bind(optionalKey).toProvider(new RealOptionalKeyProvider());
    }

    private class RealOptionalKeyProvider
        extends RealOptionalBinderProviderWithDependencies<Optional<T>>
        implements ProviderWithExtensionVisitor<Optional<T>>,
            OptionalBinderBinding<Optional<T>>,
            Provider<Optional<T>> {
      RealOptionalKeyProvider() {
        super(mapKey);
      }
      
      public Optional<T> get() {
        Optional<Provider<T>> optional = optionalProviderT.get();
        if (optional.isPresent()) {
          return Optional.fromNullable(optional.get().get());
        } else {
          return Optional.absent();
        }
      }

      public Set<Dependency<?>> getDependencies() {
        return dependencies;
      }

      @SuppressWarnings("unchecked")
      public <B, R> R acceptExtensionVisitor(BindingTargetVisitor<B, R> visitor,
          ProviderInstanceBinding<? extends B> binding) {
        if (visitor instanceof MultibindingsTargetVisitor) {
          return ((MultibindingsTargetVisitor<Optional<T>, R>) visitor).visit(this);
        } else {
          return visitor.visit(binding);
        }
      }

      public Key<Optional<T>> getKey() {
        return optionalKey;
      }
      
      public Binding<?> getActualBinding() {
        if (isInitialized()) {
          return actualBinding;
        } else {
          throw new UnsupportedOperationException(
              "getActualBinding() not supported from Elements.getElements, requires an Injector.");
        }
      }
      
      public Binding<?> getDefaultBinding() {
        if (isInitialized()) {
          return defaultBinding;
        } else {
          throw new UnsupportedOperationException(
              "getDefaultBinding() not supported from Elements.getElements, requires an Injector.");
        }
      }

      public boolean containsElement(com.google.inject.spi.Element element) {
        if (mapBinder.containsElement(element)) {
          return true;
        } else {
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
              || matchesTypeKey(element, elementKey)
              || matchesUserBinding(elementKey);
        }
      }
    }
    
    /** Returns true if the key & element indicate they were bound by this OptionalBinder. */
    private boolean matchesTypeKey(com.google.inject.spi.Element element, Key<?> elementKey) {
      // Just doing .equals(typeKey) isn't enough, because the user can bind that themselves.
      return elementKey.equals(typeKey)
          && element instanceof ProviderInstanceBinding
          && (((ProviderInstanceBinding) element)
              .getUserSuppliedProvider() instanceof RealOptionalBinderProviderWithDependencies);
    }

    /** Returns true if the key indicates this is a user bound value for the optional binder. */
    private boolean matchesUserBinding(Key<?> elementKey) {
      return elementKey.getAnnotation() instanceof Element
          && ((Element) elementKey.getAnnotation()).setName().equals(RealElement.nameOf(typeKey))
          && ((Element) elementKey.getAnnotation()).type() == Type.OPTIONALBINDER
          && elementKey.getTypeLiteral().equals(typeKey.getTypeLiteral());
    }

    private boolean isInitialized() {
      return binder == null;
    }

    @Override public boolean equals(Object o) {
      return o instanceof RealOptionalBinder
          && ((RealOptionalBinder<?>) o).mapKey.equals(mapKey);
    }

    @Override public int hashCode() {
      return mapKey.hashCode();
    }

    /** A Provider that bases equality & hashcodes off another key. */
    private static final class ValueProvider<T> implements ProviderWithDependencies<T> {
      private final Provider<T> provider;
      private volatile Key<T> key;

      private ValueProvider(Key<T> key, Provider<T> provider) {
        this.key = key;
        this.provider = provider;
      }
      
      public T get() {
        return provider.get();
      }
      
      public Set<Dependency<?>> getDependencies() {
        return ((HasDependencies) provider).getDependencies();
      }

      private Key<T> getCurrentKey() {
        // Every time, check if the key needs rehashing.
        // If so, update the field as an optimization for next time.
        Key<T> currentKey = key;
        if (needsRehashing(currentKey)) {
          currentKey = rehash(currentKey);
          key = currentKey;
        }
        return currentKey;
      }

      /**
       * Equality is based on the key (which includes the target information, because of how
       * RealElement works). This lets duplicate bindings collapse.
       */
      @Override public boolean equals(Object obj) {
        return obj instanceof ValueProvider
            && getCurrentKey().equals(((ValueProvider) obj).getCurrentKey());
      }

      /** We only use the hashcode of the typeliteral, which can't change. */
      @Override public int hashCode() {
        return key.getTypeLiteral().hashCode();
      }

      @Override public String toString() {
        return provider.toString();
      }
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

      @Override
      public boolean equals(Object obj) {
        return this.getClass() == obj.getClass()
            && equality.equals(((RealOptionalBinderProviderWithDependencies<?>) obj).equality);
      }

      @Override
      public int hashCode() {
        return equality.hashCode();
      }
    }
  }
}
