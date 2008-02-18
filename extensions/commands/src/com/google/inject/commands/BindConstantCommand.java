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

package com.google.inject.commands;

import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.ScopedBindingBuilder;
import com.google.inject.internal.Objects;

import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a constant.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BindConstantCommand implements Command {
  private BindingAnnotation bindingAnnotation;
  private ConstantTarget<?> target;

  BindConstantCommand() {
    // hide public constructor
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitBindConstant(this);
  }

  public BindTarget<?> getTarget() {
    return target;
  }

  public <T> Key<T> getKey() {
    return bindingAnnotation.getKey();
  }

  /**
   * Target API for bindConstant().
   */
  private static abstract class ConstantTarget<T> implements BindTarget<T> {

    /**
     * Returns the type of constant, such as {@code int.class} or
     * {@code Enum.class}.
     */
    abstract Class getType();

    public boolean hasInstance() {
      return true;
    }
    public ScopedBindingBuilder execute(LinkedBindingBuilder linkedBindingBuilder) {
      throw new UnsupportedOperationException();
    }
    public Provider<? extends T> getProvider(Provider<? extends T> defaultValue) {
      return defaultValue;
    }
    public Key<? extends Provider<? extends T>> getProviderKey(
        Key<Provider<? extends T>> defaultValue) {
      return defaultValue;
    }
    public Key<? extends T> getKey(Key<? extends T> defaultValue) {
      return defaultValue;
    }
  }

  /**
   * Internal annotation API.
   */
  private abstract class BindingAnnotation {
    abstract ConstantBindingBuilder execute(AnnotatedConstantBindingBuilder builder);
    abstract <T> Key<T> getKey();
  }

  BindingBuilder bindingBuilder() {
    return new BindingBuilder();
  }

  /**
   * Package-private write access to the internal state of this command.
   */
  class BindingBuilder
      implements AnnotatedConstantBindingBuilder, ConstantBindingBuilder {

    private void assertNoBindingAnnotation() {
      if (bindingAnnotation != null) {
        throw new IllegalStateException("Already annotated with " + bindingAnnotation);
      }
    }

    private void assertNoTarget() {
      if (target != null) {
        throw new IllegalStateException("Already targetted to " + target);
      }
    }

    public ConstantBindingBuilder annotatedWith(final Class<? extends Annotation> annotationType) {
      assertNoBindingAnnotation();

      bindingAnnotation = new BindingAnnotation() {
        public ConstantBindingBuilder execute(AnnotatedConstantBindingBuilder builder) {
          return builder.annotatedWith(annotationType);
        }
        @SuppressWarnings({"unchecked"})
        public <T> Key<T> getKey() {
          return Key.get((Class<T>) target.getType(), annotationType);
        }
      };
      return this;
    }

    public ConstantBindingBuilder annotatedWith(final Annotation annotation) {
      assertNoBindingAnnotation();

      bindingAnnotation = new BindingAnnotation() {
        public ConstantBindingBuilder execute(AnnotatedConstantBindingBuilder builder) {
          return builder.annotatedWith(annotation);
        }
        @SuppressWarnings({"unchecked"})
        public <T> Key<T> getKey() {
          return Key.get((Class<T>) target.getType(), annotation);
        }
      };
      return this;
    }

    public void to(final String value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return String.class;
        }
        @Override public String toString() {
          return value;
        }
      };
    }

    public void to(final int value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Integer.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final long value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Long.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final boolean value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Boolean.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final double value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Double.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final float value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Float.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final short value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Short.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final char value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Character.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public void to(final Class<?> value) {
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return Class.class;
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }

    public <E extends Enum<E>> void to(final E value) {
      Objects.nonNull(value, "value");
      assertNoTarget();

      BindConstantCommand.this.target = new ConstantTarget() {
        public void execute(ConstantBindingBuilder builder) {
          builder.to(value);
        }
        public Object get(Object defaultValue) {
          return value;
        }
        public Class getType() {
          return value.getDeclaringClass();
        }
        @Override public String toString() {
          return String.valueOf(value);
        }
      };
    }
  }
}
