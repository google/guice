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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.binder.PrivateBinder;
import com.google.inject.binder.ScopedBindingBuilder;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Creates a Module from a collection of component elements.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public class ModuleWriter {

  private final Map<PrivateEnvironment, PrivateBinder> environmentToBinder = Maps.newHashMap();

  /**
   * Returns a module that executes the specified elements using this executing visitor.
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

    ElementVisitor<Void> visitor = new ElementVisitor<Void>() {

      public Void visitMessage(Message message) {
        writeMessage(binder, message);
        return null;
      }

      public Void visitInterceptorBinding(InterceptorBinding element) {
        writeBindInterceptor(binder, element);
        return null;
      }

      public Void visitScopeBinding(ScopeBinding element) {
        writeBindScope(binder, element);
        return null;
      }

      public Void visitInjectionRequest(InjectionRequest element) {
        writeRequestInjection(binder, element);
        return null;
      }

      public Void visitStaticInjectionRequest(StaticInjectionRequest element) {
        writeRequestStaticInjection(binder, element);
        return null;
      }

      public Void visitTypeConverterBinding(TypeConverterBinding element) {
        writeConvertToTypes(binder, element);
        return null;
      }

      public <T> Void visitBinding(Binding<T> element) {
        writeBind(binder, element);
        return null;
      }

      public <T> Void visitProviderLookup(ProviderLookup<T> element) {
        writeGetProvider(binder, element);
        return null;
      }

      public Void visitPrivateEnvironment(PrivateEnvironment privateEnvironment) {
        writePrivateElements(binder, privateEnvironment);
        return null;
      }
    };

    for (Element element : elements) {
      element.acceptVisitor(visitor);
    }
  }

  protected void writeMessage(Binder binder, Message element) {
    binder.addError(element);
  }

  protected void writeBindInterceptor(Binder binder, InterceptorBinding element) {
    List<MethodInterceptor> interceptors = element.getInterceptors();
    binder.withSource(element.getSource()).bindInterceptor(
        element.getClassMatcher(), element.getMethodMatcher(),
        interceptors.toArray(new MethodInterceptor[interceptors.size()]));
  }

  protected void writeBindScope(Binder binder, ScopeBinding element) {
    binder.withSource(element.getSource()).bindScope(
        element.getAnnotationType(), element.getScope());
  }

  protected void writeRequestInjection(Binder binder, InjectionRequest command) {
    binder.withSource(command.getSource()).requestInjection(command.getInstance());
  }

  protected void writeRequestStaticInjection(Binder binder, StaticInjectionRequest element) {
    Class<?> type = element.getType();
    binder.withSource(element.getSource()).requestStaticInjection(type);
  }

  protected void writeConvertToTypes(Binder binder, TypeConverterBinding element) {
    binder.withSource(element.getSource())
        .convertToTypes(element.getTypeMatcher(), element.getTypeConverter());
  }

  protected <T> void writeBind(Binder binder, Binding<T> element) {
    ScopedBindingBuilder sbb
        = bindKeyToTarget(element, binder.withSource(element.getSource()), element.getKey());
    applyScoping(element, sbb);
  }

  /**
   * Writes the elements of the private environment to a new private binder and {@link
   * #setPrivateBinder associates} the two.
   */
  protected void writePrivateElements(Binder binder, PrivateEnvironment element) {
    PrivateBinder privateBinder = binder.withSource(element.getSource()).newPrivateBinder();
    setPrivateBinder(element, privateBinder);
    apply(privateBinder, element.getElements());
  }

  /**
   * Execute this target against the linked binding builder.
   */
  protected <T> ScopedBindingBuilder bindKeyToTarget(
      final Binding<T> binding, final Binder binder, final Key<T> key) {
    return binding.acceptTargetVisitor(new BindingTargetVisitor<T, ScopedBindingBuilder>() {
      public ScopedBindingBuilder visitInstance(InstanceBinding<T> binding) {
        binder.bind(key).toInstance(binding.getInstance());
        return null;
      }

      public ScopedBindingBuilder visitProviderInstance(ProviderInstanceBinding<T> binding) {
        return binder.bind(key).toProvider(binding.getProviderInstance());
      }

      public ScopedBindingBuilder visitProviderKey(ProviderKeyBinding<T> binding) {
        return binder.bind(key).toProvider(binding.getProviderKey());
      }

      public ScopedBindingBuilder visitLinkedKey(LinkedKeyBinding<T> binding) {
        return binder.bind(key).to(binding.getLinkedKey());
      }

      public ScopedBindingBuilder visitUntargetted(UntargettedBinding<T> binding) {
        return binder.bind(key);
      }

      public ScopedBindingBuilder visitExposed(ExposedBinding<T> binding) {
        PrivateBinder privateBinder = getPrivateBinder(binding.getPrivateEnvironment());
        privateBinder.withSource(binding.getSource()).expose(key);
        return null;
      }

      public ScopedBindingBuilder visitConvertedConstant(ConvertedConstantBinding<T> binding) {
        throw new IllegalArgumentException("Non-module element");
      }

      public ScopedBindingBuilder visitConstructor(ConstructorBinding<T> binding) {
        throw new IllegalArgumentException("Non-module element");
      }

      public ScopedBindingBuilder visitProviderBinding(ProviderBinding<?> binding) {
        throw new IllegalArgumentException("Non-module element");
      }
    });
  }

  /** Associates {@code binder} with {@code privateEnvironment}. */
  protected void setPrivateBinder(PrivateEnvironment privateEnvironment, PrivateBinder binder) {
    checkArgument(!environmentToBinder.containsKey(privateEnvironment),
        "A private binder already exists for %s", privateEnvironment);
    environmentToBinder.put(privateEnvironment, binder);
  }

  /**
   * Returns the {@code binder} accociated with {@code privateEnvironment}. This can be used to
   * expose bindings to the enclosing environment.
   */
  protected PrivateBinder getPrivateBinder(PrivateEnvironment privateEnvironment) {
    PrivateBinder privateBinder = environmentToBinder.get(privateEnvironment);
    checkArgument(privateBinder != null, "No private binder for %s", privateEnvironment);
    return privateBinder;
  }

  protected void applyScoping(Binding<?> binding, final ScopedBindingBuilder scopedBindingBuilder) {
    binding.acceptScopingVisitor(new BindingScopingVisitor<Void>() {
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

  protected <T> void writeGetProvider(Binder binder, ProviderLookup<T> element) {
    Provider<T> provider = binder.withSource(element.getSource()).getProvider(element.getKey());
    element.initDelegate(provider);
  }
}
