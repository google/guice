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

package com.google.inject.spi;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Converts elements into a Module.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ModuleWriter {

  /**
   * Returns a module that executes the specified elements
   * using this executing visitor.
   */
  public final Module create(final Iterable<? extends Element> elements) {
    return new Module() {
      public void configure(Binder binder) {
        apply(binder, elements);
      }
    };
  }

  /**
   * Replays {@code elements} against {@code binder}.
   */
  public final void apply(final Binder binder, Iterable<? extends Element> elements) {
    checkNotNull(binder, "binder");
    checkNotNull(elements, "elements");

    Element.Visitor<Void> visitor = new Element.Visitor<Void>() {

      public Void visitMessage(Message message) {
        writeMessage(binder, message);
        return null;
      }

      public Void visitBindInterceptor(BindInterceptor command) {
        writeBindInterceptor(binder, command);
        return null;
      }

      public Void visitBindScope(BindScope command) {
        writeBindScope(binder, command);
        return null;
      }

      public Void visitRequestInjection(RequestInjection command) {
        writeRequestInjection(binder, command);
        return null;
      }

      public Void visitRequestStaticInjection(RequestStaticInjection command) {
        writeRequestStaticInjection(binder, command);
        return null;
      }

      public Void visitBindConstant(BindConstant command) {
        writeBindConstant(binder, command);
        return null;
      }

      public Void visitConvertToTypes(ConvertToTypes command) {
        writeConvertToTypes(binder, command);
        return null;
      }

      public <T> Void visitBinding(Binding<T> command) {
        writeBind(binder, command);
        return null;
      }

      public <T> Void visitGetProvider(GetProvider<T> command) {
        writeGetProvider(binder, command);
        return null;
      }
    };

    for (Element element : elements) {
      element.acceptVisitor(visitor);
    }
  }

  public void writeMessage(final Binder binder, final Message message) {
    binder.addError(message);
  }

  public void writeBindInterceptor(final Binder binder, final BindInterceptor command) {
    List<MethodInterceptor> interceptors = command.getInterceptors();
    binder.withSource(command.getSource()).bindInterceptor(
        command.getClassMatcher(), command.getMethodMatcher(),
        interceptors.toArray(new MethodInterceptor[interceptors.size()]));
  }

  public void writeBindScope(final Binder binder, final BindScope command) {
    binder.withSource(command.getSource()).bindScope(
        command.getAnnotationType(), command.getScope());
  }

  public void writeRequestInjection(final Binder binder,
      final RequestInjection command) {
    List<Object> objects = command.getInstances();
    binder.withSource(command.getSource())
        .requestInjection(objects.toArray());
  }

  public void writeRequestStaticInjection(final Binder binder,
      final RequestStaticInjection command) {
    List<Class> types = command.getTypes();
    binder.withSource(command.getSource())
        .requestStaticInjection(types.toArray(new Class[types.size()]));
  }

  public void writeBindConstant(final Binder binder, final BindConstant command) {
    AnnotatedConstantBindingBuilder constantBindingBuilder
        = binder.withSource(command.getSource()).bindConstant();

    Key<Object> key = command.getKey();
    ConstantBindingBuilder builder = key.getAnnotation() != null
        ? constantBindingBuilder.annotatedWith(key.getAnnotation())
        : constantBindingBuilder.annotatedWith(key.getAnnotationType());

    apply(command, builder);
  }

  public void writeConvertToTypes(final Binder binder, final ConvertToTypes command) {
    binder.withSource(command.getSource())
        .convertToTypes(command.getTypeMatcher(), command.getTypeConverter());
  }

  public <T> void writeBind(final Binder binder, final Binding<T> binding) {
    LinkedBindingBuilder<T> lbb = binder.withSource(binding.getSource()).bind(binding.getKey());

    ScopedBindingBuilder sbb = applyTarget(binding, lbb);
    applyScoping(binding, sbb);
  }

  /**
   * Execute this target against the linked binding builder.
   */
  public <T> ScopedBindingBuilder applyTarget(Binding<T> binding,
      final LinkedBindingBuilder<T> linkedBindingBuilder) {
    return binding.acceptTargetVisitor(new com.google.inject.Binding.TargetVisitor<T, ScopedBindingBuilder>() {
      public ScopedBindingBuilder visitToInstance(T instance) {
        linkedBindingBuilder.toInstance(instance);
        return null;
      }

      public ScopedBindingBuilder visitToProvider(Provider<? extends T> provider) {
        return linkedBindingBuilder.toProvider(provider);
      }

      public ScopedBindingBuilder visitToProviderKey(Key<? extends Provider<? extends T>> providerKey) {
        return linkedBindingBuilder.toProvider(providerKey);
      }

      public ScopedBindingBuilder visitToKey(Key<? extends T> key) {
        return linkedBindingBuilder.to(key);
      }

      public ScopedBindingBuilder visitUntargetted() {
        return linkedBindingBuilder;
      }

      public ScopedBindingBuilder visitConstant(T value) {
        throw new IllegalArgumentException("Non-module element");
      }

      public ScopedBindingBuilder visitConvertedConstant(T value) {
        throw new IllegalArgumentException("Non-module element");
      }

      public ScopedBindingBuilder visitConstructor(Constructor<? extends T> constructor) {
        throw new IllegalArgumentException("Non-module element");
      }

      public ScopedBindingBuilder visitProviderBinding(Key<?> provided) {
        throw new IllegalArgumentException("Non-module element");
      }
    });
  }

  /**
   * Execute this target against the constant binding builder.
   */
  public <T> void apply(BindConstant bindConstant, ConstantBindingBuilder builder) {
    T t = bindConstant.acceptTargetVisitor(Elements.<T>getInstanceVisitor());
    Class<?> targetType = t.getClass();
    if (targetType == String.class) {
      builder.to((String) t);
    } else if (targetType == Integer.class) {
      builder.to((Integer) t);
    } else if (targetType == Long.class) {
      builder.to((Long) t);
    } else if (targetType == Boolean.class) {
      builder.to((Boolean) t);
    } else if (targetType == Double.class) {
      builder.to((Double) t);
    } else if (targetType == Float.class) {
      builder.to((Float) t);
    } else if (targetType == Short.class) {
      builder.to((Short) t);
    } else if (targetType == Character.class) {
      builder.to((Character) t);
    } else if (targetType == Class.class) {
      builder.to((Class) t);
    } else if (Enum.class.isAssignableFrom(targetType)) {
      builder.to((Enum) t);
    } else {
      throw new IllegalArgumentException("Non-constant target " + targetType);
    }
  }

  public void applyScoping(Binding<?> binding, final ScopedBindingBuilder scopedBindingBuilder) {
    binding.acceptScopingVisitor(new com.google.inject.Binding.ScopingVisitor<Void>() {
      public Void visitEagerSingleton() {
        scopedBindingBuilder.asEagerSingleton();
        return null;
      }

      public Void visitScope(Scope scope) {
        scopedBindingBuilder.in(scope);
        return null;
      }

      public Void visitScopeAnnotation(Class<? extends Annotation> scopeAnnotation) {
        scopedBindingBuilder.in(scopeAnnotation);
        return null;
      }

      public Void visitNoScoping() {
        // do nothing
        return null;
      }
    });
  }

  public <T> void writeGetProvider(final Binder binder, final GetProvider<T> command) {
    Provider<T> provider = binder.withSource(command.getSource()).getProvider(command.getKey());
    command.initDelegate(provider);
  }
}
