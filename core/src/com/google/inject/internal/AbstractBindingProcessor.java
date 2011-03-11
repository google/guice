/**
 * Copyright (C) 2011 Google Inc.
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

import java.util.Set;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.ImmutableSet;
import com.google.inject.spi.DefaultBindingTargetVisitor;

/**
 * Guarantees that processing of Binding elements happens in a sane way.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
abstract class AbstractBindingProcessor extends AbstractProcessor {

  // It's unfortunate that we have to maintain a blacklist of specific
  // classes, but we can't easily block the whole package because of
  // all our unit tests.
  private static final Set<Class<?>> FORBIDDEN_TYPES = ImmutableSet.<Class<?>>of(
      AbstractModule.class,
      Binder.class,
      Binding.class,
      Injector.class,
      Key.class,
      MembersInjector.class,
      Module.class,
      Provider.class,
      Scope.class,
      TypeLiteral.class);
  // TODO(jessewilson): fix BuiltInModule, then add Stage
  
  protected final ProcessedBindingData bindingData;
  
  AbstractBindingProcessor(Errors errors, ProcessedBindingData bindingData) {
    super(errors);
    this.bindingData = bindingData;
  }

  protected <T> UntargettedBindingImpl<T> invalidBinding(
      InjectorImpl injector, Key<T> key, Object source) {
    return new UntargettedBindingImpl<T>(injector, key, source);
  }
  
  protected void putBinding(BindingImpl<?> binding) {
    Key<?> key = binding.getKey();

    Class<?> rawType = key.getTypeLiteral().getRawType();
    if (FORBIDDEN_TYPES.contains(rawType)) {
      errors.cannotBindToGuiceType(rawType.getSimpleName());
      return;
    }

    BindingImpl<?> original = injector.getExistingBinding(key);
    if (original != null) {
      // If it failed because of an explicit duplicate binding...
      if (injector.state.getExplicitBinding(key) != null) {
        try {
          if(!isOkayDuplicate(original, binding, injector.state)) {
            errors.bindingAlreadySet(key, original.getSource());
            return;
          }
        } catch(Throwable t) {
          errors.errorCheckingDuplicateBinding(key, original.getSource(), t);
          return;
        }
      } else {
        // Otherwise, it failed because of a duplicate JIT binding
        // in the parent
        errors.jitBindingAlreadySet(key);
        return;
      }
    }

    // prevent the parent from creating a JIT binding for this key
    injector.state.parent().blacklist(key, binding.getSource());
    injector.state.putBinding(key, binding);
  }

  /**
   * We tolerate duplicate bindings if one exposes the other or if the two bindings
   * are considered duplicates (see {@link Bindings#areDuplicates(BindingImpl, BindingImpl)}.
   *
   * @param original the binding in the parent injector (candidate for an exposing binding)
   * @param binding the binding to check (candidate for the exposed binding)
   */
  private boolean isOkayDuplicate(BindingImpl<?> original, BindingImpl<?> binding, State state) {
    if (original instanceof ExposedBindingImpl) {
      ExposedBindingImpl exposed = (ExposedBindingImpl) original;
      InjectorImpl exposedFrom = (InjectorImpl) exposed.getPrivateElements().getInjector();
      return (exposedFrom == binding.getInjector());
    } else {
      original = (BindingImpl<?>)state.getExplicitBindingsThisLevel().get(binding.getKey());
      // If no original at this level, the original was on a parent, and we don't
      // allow deduplication between parents & children.
      if(original == null) {
        return false;
      } else {
        return original.equals(binding);
      }
    }
  }
  
  private <T> void validateKey(Object source, Key<T> key) {
    Annotations.checkForMisplacedScopeAnnotations(
        key.getTypeLiteral().getRawType(), source, errors);
  }
  
  /** 
   * Processor for visiting bindings.  Each overriden method that wants to
   * actually process the binding should call prepareBinding first.
   */
  abstract class Processor<T, V> extends DefaultBindingTargetVisitor<T, V> {
    final Object source;
    final Key<T> key;
    final Class<? super T> rawType;
    Scoping scoping;
    
    Processor(BindingImpl<T> binding) {
      source = binding.getSource();
      key = binding.getKey();
      rawType = key.getTypeLiteral().getRawType();
      scoping = binding.getScoping();
    }
    
    protected void prepareBinding() {      
      validateKey(source, key);
      scoping = Scoping.makeInjectable(scoping, injector, errors);
    }

    protected void scheduleInitialization(final BindingImpl<?> binding) {
      bindingData.addUninitializedBinding(new Runnable() {
        public void run() {
          try {
            binding.getInjector().initializeBinding(binding, errors.withSource(source));
          } catch (ErrorsException e) {
            errors.merge(e.getErrors());
          }
        }
      });
    }
  }
}
