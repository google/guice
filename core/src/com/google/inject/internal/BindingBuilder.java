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

import com.google.common.collect.ImmutableSet;
import com.google.inject.Binder;
import com.google.inject.ConfigurationException;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.spi.Element;
import com.google.inject.spi.InjectionPoint;
import com.google.inject.spi.Message;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;

/**
 * Bind a non-constant key.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class BindingBuilder<T> extends AbstractBindingBuilder<T>
    implements AnnotatedBindingBuilder<T> {

  public BindingBuilder(Binder binder, List<Element> elements, Object source, Key<T> key) {
    super(binder, elements, source, key);
  }

  @Override
  public BindingBuilder<T> annotatedWith(Class<? extends Annotation> annotationType) {
    annotatedWithInternal(annotationType);
    return this;
  }

  @Override
  public BindingBuilder<T> annotatedWith(Annotation annotation) {
    annotatedWithInternal(annotation);
    return this;
  }

  @Override
  public BindingBuilder<T> to(Class<? extends T> implementation) {
    return to(Key.get(implementation));
  }

  @Override
  public BindingBuilder<T> to(TypeLiteral<? extends T> implementation) {
    return to(Key.get(implementation));
  }

  @Override
  public BindingBuilder<T> to(Key<? extends T> linkedKey) {
    checkNotNull(linkedKey, "linkedKey");
    checkNotTargetted();
    BindingImpl<T> base = getBinding();
    setBinding(
        new LinkedBindingImpl<T>(base.getSource(), base.getKey(), base.getScoping(), linkedKey));
    return this;
  }

  @Override
  public void toInstance(T instance) {
    checkNotTargetted();

    // lookup the injection points, adding any errors to the binder's errors list
    Set<InjectionPoint> injectionPoints;
    if (instance != null) {
      try {
        injectionPoints = InjectionPoint.forInstanceMethodsAndFields(instance.getClass());
      } catch (ConfigurationException e) {
        copyErrorsToBinder(e);
        injectionPoints = e.getPartialValue();
      }
    } else {
      binder.addError(BINDING_TO_NULL);
      injectionPoints = ImmutableSet.of();
    }

    BindingImpl<T> base = getBinding();
    setBinding(
        new InstanceBindingImpl<T>(
            base.getSource(), base.getKey(), Scoping.EAGER_SINGLETON, injectionPoints, instance));
  }

  @Override
  public BindingBuilder<T> toProvider(Provider<? extends T> provider) {
    return toProvider((javax.inject.Provider<T>) provider);
  }

  @Override
  public BindingBuilder<T> toProvider(javax.inject.Provider<? extends T> provider) {
    checkNotNull(provider, "provider");
    checkNotTargetted();

    // lookup the injection points, adding any errors to the binder's errors list
    Set<InjectionPoint> injectionPoints;
    try {
      injectionPoints = InjectionPoint.forInstanceMethodsAndFields(provider.getClass());
    } catch (ConfigurationException e) {
      copyErrorsToBinder(e);
      injectionPoints = e.getPartialValue();
    }

    BindingImpl<T> base = getBinding();
    setBinding(
        new ProviderInstanceBindingImpl<T>(
            base.getSource(), base.getKey(), base.getScoping(), injectionPoints, provider));
    return this;
  }

  @Override
  public BindingBuilder<T> toProvider(
      Class<? extends javax.inject.Provider<? extends T>> providerType) {
    return toProvider(Key.get(providerType));
  }

  @Override
  public BindingBuilder<T> toProvider(
      TypeLiteral<? extends javax.inject.Provider<? extends T>> providerType) {
    return toProvider(Key.get(providerType));
  }

  @Override
  public BindingBuilder<T> toProvider(
      Key<? extends javax.inject.Provider<? extends T>> providerKey) {
    checkNotNull(providerKey, "providerKey");
    checkNotTargetted();

    BindingImpl<T> base = getBinding();
    setBinding(
        new LinkedProviderBindingImpl<T>(
            base.getSource(), base.getKey(), base.getScoping(), providerKey));
    return this;
  }

  @Override
  public <S extends T> ScopedBindingBuilder toConstructor(Constructor<S> constructor) {
    return toConstructor(constructor, TypeLiteral.get(constructor.getDeclaringClass()));
  }

  @Override
  public <S extends T> ScopedBindingBuilder toConstructor(
      Constructor<S> constructor, TypeLiteral<? extends S> type) {
    checkNotNull(constructor, "constructor");
    checkNotNull(type, "type");
    checkNotTargetted();

    BindingImpl<T> base = getBinding();

    Set<InjectionPoint> injectionPoints;
    try {
      injectionPoints = InjectionPoint.forInstanceMethodsAndFields(type);
    } catch (ConfigurationException e) {
      copyErrorsToBinder(e);
      injectionPoints = e.getPartialValue();
    }

    try {
      InjectionPoint constructorPoint = InjectionPoint.forConstructor(constructor, type);
      setBinding(
          new ConstructorBindingImpl<T>(
              base.getKey(),
              base.getSource(),
              base.getScoping(),
              constructorPoint,
              injectionPoints));
    } catch (ConfigurationException e) {
      copyErrorsToBinder(e);
    }

    return this;
  }

  @Override
  public String toString() {
    return "BindingBuilder<" + getBinding().getKey().getTypeLiteral() + ">";
  }

  private void copyErrorsToBinder(ConfigurationException e) {
    for (Message message : e.getErrorMessages()) {
      binder.addError(message);
    }
  }
}
