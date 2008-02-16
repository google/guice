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

package com.google.inject.visitable;

import com.google.inject.*;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import static com.google.inject.internal.Objects.nonNull;

import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a value.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BindCommand<T> implements Command {
  private Key<T> key;
  private Target<T> target;
  private BindScoping bindScoping;

  BindCommand(Key<T> key) {
    this.key = key;
  }

  public <V> V acceptVisitor(Visitor<V> visitor) {
    return visitor.visitBinding(this);
  }

  public Key<T> getKey() {
    return key;
  }

  public Target<T> getTarget() {
    return target;
  }

  public BindScoping getScoping() {
    return bindScoping;
  }

  @Override public String toString() {
    return "bind " + key
        + (target == null ? "" : (" to " + target))
        + (bindScoping == null ? "" : (" in " + bindScoping));
  }

  private static abstract class AbstractTarget<T> implements Target<T> {
    public void execute(ConstantBindingBuilder builder) {
      throw new UnsupportedOperationException();
    }
    public T get(T defaultValue) {
      return defaultValue;
    }
    public Key<? extends Provider<? extends T>> getProviderKey(Key<Provider<? extends T>> defaultValue) {
      return defaultValue;
    }
    public Provider<? extends T> getProvider(Provider<? extends T> defaultValue) {
      return defaultValue;
    }
    public Key<? extends T> getKey(Key<? extends T> defaultValue) {
      return defaultValue;
    }
  }

  private static abstract class AbstractScoping implements BindScoping {
    public boolean isEagerSingleton() {
      return false;
    }
    public Scope getScope(Scope defaultValue) {
      return defaultValue;
    }
    public Class<? extends Annotation> getScopeAnnotation(Class<? extends Annotation> defaultValue) {
      return defaultValue;
    }
  }

  BindingBuilder bindingBuilder() {
    return new BindingBuilder();
  }

  /**
   * Package-private write access to the internal state of this command.
   */
  class BindingBuilder implements AnnotatedBindingBuilder<T> {
    public LinkedBindingBuilder<T> annotatedWith(
        Class<? extends Annotation> annotationType) {
      assertNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotationType);
      return this;
    }

    public LinkedBindingBuilder<T> annotatedWith(Annotation annotation) {
      nonNull(annotation, "annotation");
      assertNotAnnotated();
      key = Key.get(key.getTypeLiteral(), annotation);
      return this;
    }

    public ScopedBindingBuilder to(final Class<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(
        final TypeLiteral<? extends T> implementation) {
      return to(Key.get(implementation));
    }

    public ScopedBindingBuilder to(final Key<? extends T> targetKey) {
      nonNull(targetKey, "targetKey");
      assertNoTarget();
      target = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.to(targetKey);
        }
        @Override public Key<? extends T> getKey(Key<? extends T> defaultValue) {
          return targetKey;
        }
        @Override public String toString() {
          return String.valueOf(targetKey);
        }
      };
      return this;
    }

    public void toInstance(final T instance) {
      nonNull(instance, "instance"); // might someday want to tolerate null here
      assertNoTarget();
      target = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          linkedBindingBuilder.toInstance(instance);
          return null;
        }
        @Override public T get(T defaultValue) {
          return instance;
        }
        @Override public String toString() {
          return "instance " + instance;
        }
      };
    }

    public ScopedBindingBuilder toProvider(final Provider<? extends T> provider) {
      nonNull(provider, "provider");
      assertNoTarget();
      target = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.toProvider(provider);
        }
        @Override public Provider<? extends T> getProvider(Provider<? extends T> defaultValue) {
          return provider;
        }
        @Override public String toString() {
          return "provider " + provider;
        }
      };
      return this;
    }

    public ScopedBindingBuilder toProvider(final Class<? extends Provider<? extends T>> providerType) {
      return toProvider(Key.get(providerType));
    }

    public ScopedBindingBuilder toProvider(final Key<? extends Provider<? extends T>> providerKey) {
      nonNull(providerKey, "providerKey");
      assertNoTarget();
      target = new AbstractTarget<T>() {
        public ScopedBindingBuilder execute(LinkedBindingBuilder<T> linkedBindingBuilder) {
          return linkedBindingBuilder.toProvider(providerKey);
        }
        @Override public Key<? extends Provider<? extends T>> getProviderKey(Key<Provider<? extends T>> defaultValue) {
          return providerKey;
        }
        @Override public String toString() {
          return "provider " + providerKey;
        }
      };
      return this;
    }

    public void in(final Class<? extends Annotation> scopeAnnotation) {
      nonNull(scopeAnnotation, "scopeAnnotation");
      assertNoScope();

      bindScoping = new AbstractScoping() {
        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.in(scopeAnnotation);
        }
        @Override public Class<? extends Annotation> getScopeAnnotation(Class<? extends Annotation> defaultValue) {
          return scopeAnnotation;
        }
        @Override public String toString() {
          return scopeAnnotation.getName();
        }
      };
    }

    public void in(final Scope scope) {
      nonNull(scope, "scope");
      assertNoScope();
      bindScoping = new AbstractScoping() {
        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.in(scope);
        }
        @Override public Scope getScope(Scope defaultValue) {
          return scope;
        }
        @Override public String toString() {
          return String.valueOf(scope);
        }
      };
    }

    public void asEagerSingleton() {
      assertNoScope();
      bindScoping = new AbstractScoping() {
        public void execute(ScopedBindingBuilder scopedBindingBuilder) {
          scopedBindingBuilder.asEagerSingleton();
        }
        @Override public boolean isEagerSingleton() {
          return true;
        }
        @Override public String toString() {
          return "eager singleton";
        }
      };
    }

    private void assertNoTarget() {
      if (target != null) {
        throw new IllegalStateException("Already targetted to " + target);
      }
    }

    private void assertNotAnnotated() {
      if (BindCommand.this.key == null) {
        throw new IllegalStateException();
      }
      if (BindCommand.this.key.getAnnotationType() != null) {
        throw new IllegalStateException("Already annotated with " + key.getAnnotationType());
      }
    }

    private void assertNoScope() {
      if (bindScoping != null) {
        throw new IllegalStateException("Already scoped by " + bindScoping);
      }
    }
  }
}
