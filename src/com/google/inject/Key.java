/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

import static com.google.inject.util.Objects.nonNull;
import com.google.inject.util.StackTraceElements;
import com.google.inject.util.ToStringBuilder;
import com.google.inject.util.Annotations;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.lang.reflect.Type;

/**
 * Binding key consisting of an injection type and an optional annotation.
 * Matches the type and annotation at a point of injection.
 *
 * <p>For example, {@code Key.get(Service.class, Transactional.class)} will
 * match:
 *
 * <pre>
 *   {@literal @}Inject
 *   public void setService({@literal @}Transactional Service service) {
 *     ...
 *   }
 * </pre>
 *
 * <p>{@code Key} supports generic types via subclassing just like {@link
 * TypeLiteral}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class Key<T> {

  final AnnotationStrategy annotationStrategy;

  final TypeLiteral<T> typeLiteral;
  final int hashCode;

  /**
   * Constructs a new key. Derives the type from this class's type parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute it
   * at runtime despite erasure.
   *
   * <p>Example usage for a binding of type {@code Foo} annotated with
   * {@code @Bar}:
   *
   * <p>{@code new Key<Foo>(Bar.class) {}}.
   */
  @SuppressWarnings("unchecked")
  protected Key(Class<? extends Annotation> annotationType) {
    this.annotationStrategy = strategyFor(annotationType);
    this.typeLiteral
        = (TypeLiteral<T>) TypeLiteral.fromSuperclassTypeParameter(getClass());
    this.hashCode = computeHashCode();
  }

  /**
   * Constructs a new key. Derives the type from this class's type parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute it
   * at runtime despite erasure.
   *
   * <p>Example usage for a binding of type {@code Foo} annotated with
   * {@code @Bar}:
   *
   * <p>{@code new Key<Foo>(new Bar()) {}}.
   */
  @SuppressWarnings("unchecked")
  protected Key(Annotation annotation) {
    // no usages, not test-covered
    this.annotationStrategy = strategyFor(annotation);
    this.typeLiteral
        = (TypeLiteral<T>) TypeLiteral.fromSuperclassTypeParameter(getClass());
    this.hashCode = computeHashCode();
  }

  /**
   * Constructs a new key. Derives the type from this class's type parameter.
   *
   * <p>Clients create an empty anonymous subclass. Doing so embeds the type
   * parameter in the anonymous class's type hierarchy so we can reconstitute it
   * at runtime despite erasure.
   *
   * <p>Example usage for a binding of type {@code Foo}:
   *
   * <p>{@code new Key<Foo>() {}}.
   */
  @SuppressWarnings("unchecked")
  protected Key() {
    this.annotationStrategy = NULL_STRATEGY;
    this.typeLiteral
        = (TypeLiteral<T>) TypeLiteral.fromSuperclassTypeParameter(getClass());
    this.hashCode = computeHashCode();
  }

  /**
   * Unsafe. Constructs a key from a manually specified type.
   */
  @SuppressWarnings("unchecked")
  private Key(Type type, AnnotationStrategy annotationStrategy) {
    this.annotationStrategy = annotationStrategy;
    this.typeLiteral = (TypeLiteral<T>) TypeLiteral.get(type);
    this.hashCode = computeHashCode();
  }

  /** Constructs a key from a manually specified type. */
  private Key(TypeLiteral<T> typeLiteral,
      AnnotationStrategy annotationStrategy) {
    this.annotationStrategy = annotationStrategy;
    this.typeLiteral = typeLiteral;
    this.hashCode = computeHashCode();
  }

  private int computeHashCode() {
    return typeLiteral.hashCode() * 31 + annotationStrategy.hashCode();
  }

  /**
   * Gets the key type.
   */
  public TypeLiteral<T> getTypeLiteral() {
    return typeLiteral;
  }

  /**
   * Gets the annotation type.
   */
  public Class<? extends Annotation> getAnnotationType() {
    return annotationStrategy.getAnnotationType();
  }

  /**
   * Gets the annotation.
   */
  public Annotation getAnnotation() {
    return annotationStrategy.getAnnotation();
  }

  boolean hasAnnotationType() {
    return annotationStrategy.getAnnotationType() != null;
  }

  String getAnnotationName() {
    Annotation annotation = annotationStrategy.getAnnotation();
    if (annotation != null) {
      return annotation.toString();
    }

    // not test-covered
    return annotationStrategy.getAnnotationType().toString();
  }

  public int hashCode() {
    return this.hashCode;
  }

  Class<? super T> getRawType() {
    return typeLiteral.getRawType();
  }

  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof Key<?>)) {
      return false;
    }
    Key<?> other = (Key<?>) o;
    return annotationStrategy.equals(other.annotationStrategy)
        && typeLiteral.equals(other.typeLiteral);
  }

  public String toString() {
    return new ToStringBuilder(Key.class)
        .add("type", typeLiteral)
        .add("annotation", annotationStrategy)
        .toString();
  }

  /**
   * Gets a key for an injection type and an annotation strategy.
   */
  static <T> Key<T> get(Class<T> type,
      AnnotationStrategy annotationStrategy) {
    return new SimpleKey<T>(type, annotationStrategy);
  }

  /**
   * Gets a key for an injection type.
   */
  public static <T> Key<T> get(Class<T> type) {
    return new SimpleKey<T>(type, NULL_STRATEGY);
  }

  /**
   * Gets a key for an injection type and an annotation type.
   */
  public static <T> Key<T> get(Class<T> type,
      Class<? extends Annotation> annotationType) {
    return new SimpleKey<T>(type, strategyFor(annotationType));
  }

  /**
   * Gets a key for an injection type and an annotation.
   */
  public static <T> Key<T> get(Class<T> type, Annotation annotation) {
    return new SimpleKey<T>(type, strategyFor(annotation));
  }

  /**
   * Gets a key for an injection type.
   */
  public static Key<?> get(Type type) {
    return new SimpleKey<Object>(type, NULL_STRATEGY);
  }

  /**
   * Gets a key for an injection type and an annotation type.
   */
  public static Key<?> get(Type type,
      Class<? extends Annotation> annotationType) {
    return new SimpleKey<Object>(type, strategyFor(annotationType));
  }

  /**
   * Gets a key for an injection type and an annotation.
   */
  public static Key<?> get(Type type, Annotation annotation) {
    return new SimpleKey<Object>(type, strategyFor(annotation));
  }

  /**
   * Gets a key for an injection type.
   */
  public static <T> Key<T> get(TypeLiteral<T> typeLiteral) {
    return new SimpleKey<T>(typeLiteral, NULL_STRATEGY);
  }

  /**
   * Gets a key for an injection type and an annotation type.
   */
  public static <T> Key<T> get(TypeLiteral<T> typeLiteral,
      Class<? extends Annotation> annotationType) {
    return new SimpleKey<T>(typeLiteral, strategyFor(annotationType));
  }

  /**
   * Gets a key for an injection type and an annotation.
   */
  public static <T> Key<T> get(TypeLiteral<T> typeLiteral,
      Annotation annotation) {
    return new SimpleKey<T>(typeLiteral, strategyFor(annotation));
  }

  /**
   * Gets a key for the given type, member and annotations.
   */
  static Key<?> get(Type type, Member member, Annotation[] annotations,
      ErrorHandler errorHandler) {
    Annotation found = null;
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().getAnnotation(BindingAnnotation.class) != null) {
        if (found == null) {
          found = annotation;
        } else {
          errorHandler.handle(StackTraceElements.forMember(member),
              ErrorMessages.DUPLICATE_ANNOTATIONS, found, annotation);
        }
      }
    }
    Key<?> key = found == null ? Key.get(type) : Key.get(type, found);
    return key;
  }

  /**
   * Returns a new key of the specified type with the same annotation as this
   * key.
   */
  <T> Key<T> ofType(Class<T> type) {
    return new SimpleKey<T>(type, annotationStrategy);
  }

  /**
   * Returns a new key of the specified type with the same annotation as this
   * key.
   */
  Key<?> ofType(Type type) {
    return new SimpleKey<Object>(type, annotationStrategy);
  }

  /**
   * Returns true if this key has annotation attributes.
   * @return
   */
  boolean hasAttributes() {
    return annotationStrategy.hasAttributes();
  }

  /**
   * Returns this key without annotation attributes, i.e. with only the
   * annotation type.
   */
  Key<T> withoutAttributes() {
    return new SimpleKey<T>(typeLiteral, annotationStrategy.withoutAttributes());
  }

  private static class SimpleKey<T> extends Key<T> {

    private SimpleKey(Type type, AnnotationStrategy annotationStrategy) {
      super(type, annotationStrategy);
    }

    private SimpleKey(TypeLiteral<T> typeLiteral,
        AnnotationStrategy annotationStrategy) {
      super(typeLiteral, annotationStrategy);
    }
  }

  interface AnnotationStrategy {

    Annotation getAnnotation();
    Class<? extends Annotation> getAnnotationType();
    boolean hasAttributes();
    AnnotationStrategy withoutAttributes();
  }

  static final AnnotationStrategy NULL_STRATEGY = new AnnotationStrategy() {

    public boolean hasAttributes() {
      return false;
    }

    public AnnotationStrategy withoutAttributes() {
      throw new UnsupportedOperationException("Key already has no attributes.");
    }

    public Annotation getAnnotation() {
      return null;
    }

    public Class<? extends Annotation> getAnnotationType() {
      return null;
    }

    public boolean equals(Object o) {
      return o == NULL_STRATEGY;
    }

    public int hashCode() {
      return 0;
    }

    public String toString() {
      return "[none]";
    }
  };

  /**
   * Returns {@code true} if the given annotation type has no attributes.
   */
  static boolean isMarker(Class<? extends Annotation> annotationType) {
    return annotationType.getDeclaredMethods().length == 0;
  }

  /**
   * Gets the strategy for an annotation.
   */
  static AnnotationStrategy strategyFor(Annotation annotation) {
    nonNull(annotation, "annotation");
    Class<? extends Annotation> annotationType = annotation.annotationType();
    ensureRetainedAtRuntime(annotationType);
    ensureIsBindingAnnotation(annotationType);
    return new AnnotationInstanceStrategy(annotation);
  }

  /**
   * Gets the strategy for an annotation type.
   */
  static AnnotationStrategy strategyFor(
      Class<? extends Annotation> annotationType) {
    nonNull(annotationType, "annotation type");
    ensureRetainedAtRuntime(annotationType);
    ensureIsBindingAnnotation(annotationType);
    return new AnnotationTypeStrategy(annotationType, null);
  }

  private static void ensureRetainedAtRuntime(
      Class<? extends Annotation> annotationType) {
    if (!Annotations.isRetainedAtRuntime(annotationType)) {
      throw new IllegalArgumentException(annotationType.getName()
          + " is not retained at runtime."
          + " Please annotate it with @Retention(RUNTIME).");
    }
  }

  private static void ensureIsBindingAnnotation(
      Class<? extends Annotation> annotationType) {
    if (!isBindingAnnotation(annotationType)) {
      throw new IllegalArgumentException(annotationType.getName()
          + " is not a binding annotation."
          + " Please annotate it with @BindingAnnotation.");
    }
  }

  // this class not test-covered
  static class AnnotationInstanceStrategy implements AnnotationStrategy {

    final Annotation annotation;

    AnnotationInstanceStrategy(Annotation annotation) {
      this.annotation = nonNull(annotation, "annotation");
    }

    public boolean hasAttributes() {
      return true;
    }

    public AnnotationStrategy withoutAttributes() {
      return new AnnotationTypeStrategy(getAnnotationType(), annotation);
    }

    public Annotation getAnnotation() {
      return annotation;
    }

    public Class<? extends Annotation> getAnnotationType() {
      return annotation.annotationType();
    }

    public boolean equals(Object o) {
      if (!(o instanceof AnnotationInstanceStrategy)) {
        return false;
      }

      AnnotationInstanceStrategy other = (AnnotationInstanceStrategy) o;
      return annotation.equals(other.annotation);
    }

    public int hashCode() {
      return annotation.hashCode();
    }

    public String toString() {
      return annotation.toString();
    }
  }

  static class AnnotationTypeStrategy implements AnnotationStrategy {

    final Class<? extends Annotation> annotationType;

    // Keep the instance around if we have it so the client can request it.
    final Annotation annotation;

    AnnotationTypeStrategy(Class<? extends Annotation> annotationType,
        Annotation annotation) {
      this.annotationType = nonNull(annotationType, "annotation type");
      this.annotation = annotation;
    }

    public boolean hasAttributes() {
      return false;
    }

    public AnnotationStrategy withoutAttributes() {
      throw new UnsupportedOperationException("Key already has no attributes.");
    }

    public Annotation getAnnotation() {
      return annotation;
    }

    public Class<? extends Annotation> getAnnotationType() {
      return annotationType;
    }

    public boolean equals(Object o) {
      if (!(o instanceof AnnotationTypeStrategy)) {
        return false;
      }

      AnnotationTypeStrategy other = (AnnotationTypeStrategy) o;
      return annotationType.equals(other.annotationType);
    }

    public int hashCode() {
      return annotationType.hashCode();
    }

    public String toString() {
      return "@" + annotationType.getName();
    }
  }

  static boolean isBindingAnnotation(Annotation annotation) {
    return isBindingAnnotation(annotation.annotationType());
  }

  static boolean isBindingAnnotation(
      Class<? extends Annotation> annotationType) {
    return annotationType.isAnnotationPresent(BindingAnnotation.class);
  }
}
