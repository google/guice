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

package com.google.inject.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Scope;
import com.google.inject.spi.Element;
import com.google.inject.spi.InstanceBinding;
import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Base class used to create a new binding with the Guice EDSL described in {@link Binder}.
 * Constructing an AbstractBindingBuilder will create a new binding and add it into the {@link
 * Binder}.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class AbstractBindingBuilder<T> {

  public static final String IMPLEMENTATION_ALREADY_SET = "Implementation is set more than once.";
  public static final String SINGLE_INSTANCE_AND_SCOPE =
      "Setting the scope is not permitted when binding to a single instance.";
  public static final String SCOPE_ALREADY_SET = "Scope is set more than once.";
  public static final String BINDING_TO_NULL =
      "Binding to null instances is not allowed. "
          + "Use toProvider(Providers.of(null)) if this is your intended behaviour.";
  public static final String CONSTANT_VALUE_ALREADY_SET = "Constant value is set more than once.";
  public static final String ANNOTATION_ALREADY_SPECIFIED =
      "More than one annotation is specified for this binding.";

  protected static final Key<?> NULL_KEY = Key.get(Void.class);

  /** The binder that the new binding will be added to. */
  protected final Binder binder;
  /**
   * The list of elements stored inside the {@link #binder}. The new binding is added to this list.
   */
  protected List<Element> elements;
  /**
   * The index of the new binding in {@link #elements}. This is used by subclasses of
   * AbstractBindingBuilder to repeatedly replace the binding object in {@link #elements} as more
   * Guice EDSL methods are called to update the binding.
   */
  protected int position;
  /** The new binding being added to the {@link #binder}'s {@link #elements} list */
  private BindingImpl<T> binding;

  public AbstractBindingBuilder(Binder binder, List<Element> elements, Object source, Key<T> key) {
    this.binder = binder;
    this.elements = elements;
    this.position = elements.size();
    this.binding = new UntargettedBindingImpl<>(source, key, Scoping.UNSCOPED);
    elements.add(position, this.binding);
  }

  protected BindingImpl<T> getBinding() {
    return binding;
  }

  protected BindingImpl<T> setBinding(BindingImpl<T> binding) {
    this.binding = binding;
    elements.set(position, binding);
    return binding;
  }

  /** Sets the binding to a copy with the specified annotation on the bound key */
  protected BindingImpl<T> annotatedWithInternal(Class<? extends Annotation> annotationType) {
    checkNotNull(annotationType, "annotationType");
    checkNotAnnotated();
    return setBinding(binding.withKey(this.binding.getKey().withAnnotation(annotationType)));
  }

  /** Sets the binding to a copy with the specified annotation on the bound key */
  protected BindingImpl<T> annotatedWithInternal(Annotation annotation) {
    checkNotNull(annotation, "annotation");
    checkNotAnnotated();
    return setBinding(binding.withKey(this.binding.getKey().withAnnotation(annotation)));
  }

  public void in(final Class<? extends Annotation> scopeAnnotation) {
    checkNotNull(scopeAnnotation, "scopeAnnotation");
    checkNotScoped();
    setBinding(getBinding().withScoping(Scoping.forAnnotation(scopeAnnotation)));
  }

  public void in(final Scope scope) {
    checkNotNull(scope, "scope");
    checkNotScoped();
    setBinding(getBinding().withScoping(Scoping.forInstance(scope)));
  }

  public void asEagerSingleton() {
    checkNotScoped();
    setBinding(getBinding().withScoping(Scoping.EAGER_SINGLETON));
  }

  protected boolean keyTypeIsSet() {
    return !Void.class.equals(binding.getKey().getTypeLiteral().getType());
  }

  protected void checkNotTargetted() {
    if (!(binding instanceof UntargettedBindingImpl)) {
      binder.addError(IMPLEMENTATION_ALREADY_SET);
    }
  }

  protected void checkNotAnnotated() {
    if (binding.getKey().getAnnotationType() != null) {
      binder.addError(ANNOTATION_ALREADY_SPECIFIED);
    }
  }

  protected void checkNotScoped() {
    // Scoping isn't allowed when we have only one instance.
    if (binding instanceof InstanceBinding) {
      binder.addError(SINGLE_INSTANCE_AND_SCOPE);
      return;
    }

    if (binding.getScoping().isExplicitlyScoped()) {
      binder.addError(SCOPE_ALREADY_SET);
    }
  }
}
