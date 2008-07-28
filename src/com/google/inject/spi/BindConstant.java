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
import com.google.inject.Binding.TargetVisitor;
import com.google.inject.Key;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.binder.ConstantBindingBuilder;
import java.lang.annotation.Annotation;

/**
 * Immutable snapshot of a request to bind a constant.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class BindConstant implements Element {

  // TODO(jessewilson): Combine with Binding

  private static final ConstantTarget<Object> UNTARGETTED
      = new ConstantTarget<Object>(null, Void.class) {
    public <V> V acceptTargetVisitor(TargetVisitor<? super Object, V> visitor) {
      return visitor.visitUntargetted();
    }
  };

  private final Object source;
  private BindingAnnotation bindingAnnotation;
  private ConstantTarget<?> target = UNTARGETTED;

  BindConstant(Object source) {
    this.source = checkNotNull(source, "source");
  }

  public Object getSource() {
    return source;
  }

  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visitBindConstant(this);
  }

  public <T> Key<T> getKey() {
    return bindingAnnotation.getKey();
  }

  public <V> V acceptTargetVisitor(TargetVisitor<?, V> visitor) {
    TargetVisitor v = (TargetVisitor) visitor;
    return (V) target.acceptTargetVisitor(v);
  }

  /**
   * Target API for bindConstant().
   */
  private static class ConstantTarget<T> {
    private final T value;
    private final Class<?> type;

    protected ConstantTarget(T value, Class<?> type) {
      this.value = value;
      this.type = type;
    }

    /**
     * Returns the type of constant, such as {@code Integer.class} or
     * {@code Enum.class}.
     */
    public Class getType() {
      return type;
    }
    public <V> V acceptTargetVisitor(TargetVisitor<? super T, V> visitor) {
      return visitor.visitToInstance(value);
    }
    @Override public String toString() {
      return String.valueOf(value);
    }
  }

  /**
   * Internal annotation API.
   */
  private abstract class BindingAnnotation {
    abstract ConstantBindingBuilder execute(AnnotatedConstantBindingBuilder builder);
    abstract <T> Key<T> getKey();
  }

  BindingBuilder bindingBuilder(Binder binder) {
    return new BindingBuilder(binder);
  }

  /**
   * Package-private write access to the internal state of this command.
   */
  class BindingBuilder
      implements AnnotatedConstantBindingBuilder, ConstantBindingBuilder {
    private final Binder binder;

    BindingBuilder(Binder binder) {
      this.binder = binder.skipSources(BindingBuilder.class);
    }

    public ConstantBindingBuilder annotatedWith(final Class<? extends Annotation> annotationType) {
      checkNotNull(annotationType, "annotationType");
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
      checkNotNull(annotation, "annotation");
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
      checkNotNull(value, "value");
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<String>(value, String.class);
    }

    public void to(final int value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Integer>(value, Integer.class);
    }

    public void to(final long value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Long>(value, Long.class);
    }

    public void to(final boolean value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Boolean>(value, Boolean.class);
    }

    public void to(final double value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Double>(value, Double.class);
    }

    public void to(final float value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Float>(value, Float.class);
    }

    public void to(final short value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Short>(value, Short.class);
    }

    public void to(final char value) {
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Character>(value, Character.class);
    }

    public void to(final Class<?> value) {
      checkNotNull(value, "value");
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Class<?>>(value, Class.class);
    }

    public <E extends Enum<E>> void to(final E value) {
      checkNotNull(value, "value");
      assertNoTarget();
      BindConstant.this.target = new ConstantTarget<Enum>(value, value.getDeclaringClass());
    }

    static final String CONSTANT_VALUE_ALREADY_SET = "Constant value is set more"
        + " than once.";
    static final String ANNOTATION_ALREADY_SPECIFIED = "More than one annotation"
        + " is specified for this binding.";

    private void assertNoBindingAnnotation() {
      if (bindingAnnotation != null) {
        binder.addError(ANNOTATION_ALREADY_SPECIFIED);
      }
    }

    private void assertNoTarget() {
      if (target != UNTARGETTED) {
        binder.addError(CONSTANT_VALUE_ALREADY_SET);
      }
    }

    @Override public String toString() {
      return bindingAnnotation == null
          ? "AnnotatedConstantBindingBuilder"
          : "ConstantBindingBuilder";
    }
  }
}
