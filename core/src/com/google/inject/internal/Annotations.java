/*
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

package com.google.inject.internal;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Joiner;
import com.google.common.base.Joiner.MapJoiner;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.inject.BindingAnnotation;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.ScopeAnnotation;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.util.Classes;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Annotation utilities.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Annotations {

  private static final AnnotationProvider[] ANNOTATION_PROVIDERS = new AnnotationProvider[]{
          new JavaxAnnotations(),
          new JakartaAnnotations()
  };

  /** Returns {@code true} if the given annotation type has no attributes. */
  public static boolean isMarker(Class<? extends Annotation> annotationType) {
    return annotationType.getDeclaredMethods().length == 0;
  }

  public static boolean isAllDefaultMethods(Class<? extends Annotation> annotationType) {
    boolean hasMethods = false;
    for (Method m : annotationType.getDeclaredMethods()) {
      hasMethods = true;
      if (m.getDefaultValue() == null) {
        return false;
      }
    }
    return hasMethods;
  }

  private static final LoadingCache<Class<? extends Annotation>, Annotation> cache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(
              new CacheLoader<Class<? extends Annotation>, Annotation>() {
                @Override
                public Annotation load(Class<? extends Annotation> input) {
                  return generateAnnotationImpl(input);
                }
              });

  /**
   * Generates an Annotation for the annotation class. Requires that the annotation is all
   * optionals.
   */
  @SuppressWarnings("unchecked") // Safe because generateAnnotationImpl returns T for Class<T>
  public static <T extends Annotation> T generateAnnotation(Class<T> annotationType) {
    Preconditions.checkState(
        isAllDefaultMethods(annotationType), "%s is not all default methods", annotationType);
    return (T) cache.getUnchecked(annotationType);
  }

  private static <T extends Annotation> T generateAnnotationImpl(final Class<T> annotationType) {
    final Map<String, Object> members = resolveMembers(annotationType);
    return annotationType.cast(
        Proxy.newProxyInstance(
            annotationType.getClassLoader(),
            new Class<?>[] {annotationType},
            new InvocationHandler() {
              @Override
              public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
                String name = method.getName();
                if (name.equals("annotationType")) {
                  return annotationType;
                } else if (name.equals("toString")) {
                  return annotationToString(annotationType, members);
                } else if (name.equals("hashCode")) {
                  return annotationHashCode(annotationType, members);
                } else if (name.equals("equals")) {
                  return annotationEquals(annotationType, members, args[0]);
                } else {
                  return members.get(name);
                }
              }
            }));
  }

  private static ImmutableMap<String, Object> resolveMembers(
      Class<? extends Annotation> annotationType) {
    ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();
    for (Method method : annotationType.getDeclaredMethods()) {
      result.put(method.getName(), method.getDefaultValue());
    }
    return result.buildOrThrow();
  }

  /** Implements {@link Annotation#equals}. */
  private static boolean annotationEquals(
      Class<? extends Annotation> type, Map<String, Object> members, Object other)
      throws Exception {
    if (!type.isInstance(other)) {
      return false;
    }
    for (Method method : type.getDeclaredMethods()) {
      String name = method.getName();
      if (!Arrays.deepEquals(
          new Object[] {method.invoke(other)}, new Object[] {members.get(name)})) {
        return false;
      }
    }
    return true;
  }

  /** Implements {@link Annotation#hashCode}. */
  private static int annotationHashCode(
      Class<? extends Annotation> type, Map<String, Object> members) throws Exception {
    int result = 0;
    for (Method method : type.getDeclaredMethods()) {
      String name = method.getName();
      Object value = members.get(name);
      result += (127 * name.hashCode()) ^ (Arrays.deepHashCode(new Object[] {value}) - 31);
    }
    return result;
  }

  private static final MapJoiner JOINER = Joiner.on(", ").withKeyValueSeparator("=");

  /** Implements {@link Annotation#toString}. */
  private static String annotationToString(
      Class<? extends Annotation> type, Map<String, Object> members) throws Exception {
    StringBuilder sb = new StringBuilder().append('@').append(type.getName()).append('(');
    JOINER.appendTo(
        sb,
        Maps.transformValues(
            members,
            arg -> {
              String s = Arrays.deepToString(new Object[] {arg});
              return s.substring(1, s.length() - 1); // cut off brackets
            }));
    return sb.append(')').toString();
  }

  /** Returns true if the given annotation is retained at runtime. */
  public static boolean isRetainedAtRuntime(Class<? extends Annotation> annotationType) {
    Retention retention = annotationType.getAnnotation(Retention.class);
    return retention != null && retention.value() == RetentionPolicy.RUNTIME;
  }

  /** Returns the scope annotation on {@code type}, or null if none is specified. */
  public static Class<? extends Annotation> findScopeAnnotation(
      Errors errors, Class<?> implementation) {
    return findScopeAnnotation(errors, implementation.getAnnotations());
  }

  /** Returns the scoping annotation, or null if there isn't one. */
  public static Class<? extends Annotation> findScopeAnnotation(
      Errors errors, Annotation[] annotations) {
    Class<? extends Annotation> found = null;

    for (Annotation annotation : annotations) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (isScopeAnnotation(annotationType)) {
        if (found != null) {
          errors.duplicateScopeAnnotations(found, annotationType);
        } else {
          found = annotationType;
        }
      }
    }

    return found;
  }

  static boolean containsComponentAnnotation(Annotation[] annotations) {
    for (Annotation annotation : annotations) {
      // TODO(user): Should we scope this down to dagger.Component?
      if (annotation.annotationType().getSimpleName().equals("Component")) {
        return true;
      }
    }

    return false;
  }

  private static class AnnotationToStringConfig {
    final boolean quote;
    final boolean includeMemberName;

    AnnotationToStringConfig(boolean quote, boolean includeMemberName) {
      this.quote = quote;
      this.includeMemberName = includeMemberName;
    }
  }

  private static final AnnotationToStringConfig ANNOTATION_TO_STRING_CONFIG =
      determineAnnotationToStringConfig();

  /**
   * Returns {@code value}, quoted if annotation implementations quote their member values. In Java
   * 9, annotations quote their string members.
   */
  public static String memberValueString(String value) {
    return ANNOTATION_TO_STRING_CONFIG.quote ? "\"" + value + "\"" : value;
  }

  /**
   * Returns string representation of the annotation memeber.
   *
   * <p>The value of the member is prefixed with `memberName=` unless the runtime omits the member
   * name. The value of the member is quoted if annotation implementations quote their member values
   * and the value type is String.
   *
   * <p>In Java 9, annotations quote their string members and in Java 15, the member name is
   * omitted.
   */
  public static String memberValueString(String memberName, Object value) {
    StringBuilder sb = new StringBuilder();
    boolean quote = ANNOTATION_TO_STRING_CONFIG.quote;
    boolean includeMemberName = ANNOTATION_TO_STRING_CONFIG.includeMemberName;
    if (includeMemberName) {
      sb.append(memberName).append('=');
    }
    if (quote && (value instanceof String)) {
      sb.append('"').append(value).append('"');
    } else {
      sb.append(value);
    }
    return sb.toString();
  }

  @Retention(RUNTIME)
  private @interface TestAnnotation {
    String value();
  }

  @TestAnnotation("determineAnnotationToStringConfig")
  private static AnnotationToStringConfig determineAnnotationToStringConfig() {
    try {
      String annotation =
          Annotations.class
              .getDeclaredMethod("determineAnnotationToStringConfig")
              .getAnnotation(TestAnnotation.class)
              .toString();
      boolean quote = annotation.contains("\"determineAnnotationToStringConfig\"");
      boolean includeMemberName = annotation.contains("value=");
      return new AnnotationToStringConfig(quote, includeMemberName);
    } catch (NoSuchMethodException e) {
      throw new AssertionError(e);
    }
  }

  /** Checks for the presence of annotations. Caches results because Android doesn't. */
  static class AnnotationChecker {
    private final Collection<Class<? extends Annotation>> annotationTypes;

    /** Returns true if the given class has one of the desired annotations. */
    private CacheLoader<Class<? extends Annotation>, Boolean> hasAnnotations =
        new CacheLoader<Class<? extends Annotation>, Boolean>() {
          @Override
          public Boolean load(Class<? extends Annotation> annotationType) {
            for (Annotation annotation : annotationType.getAnnotations()) {
              if (annotationTypes.contains(annotation.annotationType())) {
                return true;
              }
            }
            return false;
          }
        };

    final LoadingCache<Class<? extends Annotation>, Boolean> cache =
        CacheBuilder.newBuilder().weakKeys().build(hasAnnotations);

    /** Constructs a new checker that looks for annotations of the given types. */
    AnnotationChecker(Collection<Class<? extends Annotation>> annotationTypes) {
      this.annotationTypes = annotationTypes;
    }

    /** Returns true if the given type has one of the desired annotations. */
    boolean hasAnnotations(Class<? extends Annotation> annotated) {
      return cache.getUnchecked(annotated);
    }
  }

  private static final AnnotationChecker scopeChecker =
      new AnnotationChecker(Streams.concat(Stream.of(ScopeAnnotation.class),
                      Arrays.stream(ANNOTATION_PROVIDERS).map(AnnotationProvider::getScopeAnnotationType))
              .collect(Collectors.toList()));

  public static boolean isScopeAnnotation(Class<? extends Annotation> annotationType) {
    return scopeChecker.hasAnnotations(annotationType);
  }

  /**
   * Adds an error if there is a misplaced annotations on {@code type}. Scoping annotations are not
   * allowed on abstract classes or interfaces.
   */
  public static void checkForMisplacedScopeAnnotations(
      Class<?> type, Object source, Errors errors) {
    if (Classes.isConcrete(type)) {
      return;
    }

    Class<? extends Annotation> scopeAnnotation = findScopeAnnotation(errors, type);
    if (scopeAnnotation != null
        // We let Dagger Components through to aid migrations.
        && !containsComponentAnnotation(type.getAnnotations())) {
      errors.withSource(type).scopeAnnotationOnAbstractType(scopeAnnotation, type, source);
    }
  }

  // NOTE: getKey/findBindingAnnotation are used by Gin which is abandoned.  So changing this API
  // will prevent Gin users from upgrading Guice version.

  /** Gets a key for the given type, member and annotations. */
  public static Key<?> getKey(
      TypeLiteral<?> type, Member member, Annotation[] annotations, Errors errors)
      throws ErrorsException {
    int numErrorsBefore = errors.size();
    Annotation found = findBindingAnnotation(errors, member, annotations);
    errors.throwIfNewErrors(numErrorsBefore);
    return found == null ? Key.get(type) : Key.get(type, found);
  }

  /** Returns the binding annotation on {@code member}, or null if there isn't one. */
  public static Annotation findBindingAnnotation(
      Errors errors, Member member, Annotation[] annotations) {
    Annotation found = null;

    for (Annotation annotation : annotations) {
      Class<? extends Annotation> annotationType = annotation.annotationType();
      if (isBindingAnnotation(annotationType)) {
        if (found != null) {
          errors.duplicateBindingAnnotations(member, found.annotationType(), annotationType);
        } else {
          found = annotation;
        }
      }
    }

    return found;
  }

  private static final AnnotationChecker bindingAnnotationChecker =
      new AnnotationChecker(Streams.concat(Stream.of(BindingAnnotation.class),
              Arrays.stream(ANNOTATION_PROVIDERS).map(AnnotationProvider::getQualifierAnnotationType))
          .collect(Collectors.toList()));


  /** Returns true if annotations of the specified type are binding annotations. */
  public static boolean isBindingAnnotation(Class<? extends Annotation> annotationType) {
    return bindingAnnotationChecker.hasAnnotations(annotationType);
  }

  /**
   * If the annotation is a supported Named annotation, canonicalize to
   * com.google.guice.name.Named. Returns the given annotation otherwise.
   */
  public static Annotation canonicalizeIfNamed(Annotation annotation) {
    for (AnnotationProvider annotationProvider : ANNOTATION_PROVIDERS) {
      if (annotationProvider.acceptAnnotation(annotation)) {
        return annotationProvider.canonicalizeNamed(annotation);
      }
    }
    return annotation;
  }

  /**
   * If the annotation is a supported Named annotation, canonicalize to
   * com.google.guice.name.Named. Returns the given annotation class otherwise.
   */
  public static Class<? extends Annotation> canonicalizeIfNamed(
      Class<? extends Annotation> annotationType) {

    for (AnnotationProvider annotationProvider : ANNOTATION_PROVIDERS) {
      if (annotationProvider.acceptAnnotation(annotationType)) {
        return Named.class;
      }
    }

    return annotationType;
  }

  /**
   * Returns the name the binding should use. This is based on the annotation. If the annotation has
   * an instance and is not a marker annotation, we ask the annotation for its toString. If it was a
   * marker annotation or just an annotation type, we use the annotation's name. Otherwise, the name
   * is the empty string.
   */
  public static String nameOf(Key<?> key) {
    Annotation annotation = key.getAnnotation();
    Class<? extends Annotation> annotationType = key.getAnnotationType();
    if (annotation != null && !isMarker(annotationType)) {
      return key.getAnnotation().toString();
    } else if (key.getAnnotationType() != null) {
      return '@' + key.getAnnotationType().getName();
    } else {
      return "";
    }
  }

  public static Annotation getAtInject(AnnotatedElement member) {

    for (AnnotationProvider annotationProvider : ANNOTATION_PROVIDERS) {
      Annotation a = annotationProvider.getInjectAnnotation(member);
      if (a != null) {
        return a;
      }
    }

    return member.getAnnotation(Inject.class);
  }

  public static boolean hasAtInject(AnnotatedElement member) {

    for (AnnotationProvider annotationProvider : ANNOTATION_PROVIDERS) {
      if (annotationProvider.hasInjectAnnotation(member)) {
        return true;
      }
    }

    return member.isAnnotationPresent(Inject.class);
  }

  public static boolean isSingletonAnnotation(Class<? extends Annotation> annotationType) {

    for (AnnotationProvider annotationProvider : ANNOTATION_PROVIDERS) {
      if (annotationProvider.getSingletonAnnotationType() == annotationType) {
        return true;
      }
    }

    return annotationType == Singleton.class;
  }

  private interface AnnotationProvider {

    // general annotation usage
    boolean acceptAnnotation(Annotation annotation);

    boolean acceptAnnotation(Class<? extends Annotation> annotationType);

    // inject
    Annotation getInjectAnnotation(AnnotatedElement member);

    boolean hasInjectAnnotation(AnnotatedElement member);

    // named
    Annotation canonicalizeNamed(Annotation annotation);

    // scope
    Class<? extends Annotation> getScopeAnnotationType();

    Class<? extends Annotation> getQualifierAnnotationType();

    Class<? extends Annotation> getSingletonAnnotationType();
  }

  private static class JavaxAnnotations implements AnnotationProvider {

    @Override
    public boolean acceptAnnotation(Annotation annotation) {
      return annotation instanceof javax.inject.Inject
          || annotation instanceof javax.inject.Named
          || annotation instanceof javax.inject.Qualifier
          || annotation instanceof javax.inject.Scope
          || annotation instanceof javax.inject.Singleton;
    }

    @Override
    public boolean acceptAnnotation(Class<? extends Annotation> annotationType) {
      return (annotationType == javax.inject.Inject.class
          || annotationType == javax.inject.Named.class
          || annotationType == javax.inject.Qualifier.class
          || annotationType == javax.inject.Scope.class
          || annotationType == javax.inject.Singleton.class);
    }

    @Override
    public Annotation getInjectAnnotation(AnnotatedElement member) {
      if (member != null) {
        return member.getAnnotation(javax.inject.Inject.class);
      }
      return null;
    }

    @Override
    public boolean hasInjectAnnotation(AnnotatedElement member) {
      if (member != null) {
        return member.isAnnotationPresent(javax.inject.Inject.class);
      }
      return false;
    }

    @Override
    public Annotation canonicalizeNamed(Annotation annotation) {
      return Names.named(((javax.inject.Named) annotation).value());
    }

    @Override
    public Class<? extends Annotation> getScopeAnnotationType() {
      return javax.inject.Scope.class;
    }

    @Override
    public Class<? extends Annotation> getQualifierAnnotationType() {
      return javax.inject.Qualifier.class;
    }

    @Override
    public Class<? extends Annotation> getSingletonAnnotationType() {
      return javax.inject.Singleton.class;
    }
  }

  private static class JakartaAnnotations implements AnnotationProvider {

    @Override
    public boolean acceptAnnotation(Annotation annotation) {
      return annotation instanceof jakarta.inject.Inject
          || annotation instanceof jakarta.inject.Named
          || annotation instanceof jakarta.inject.Qualifier
          || annotation instanceof jakarta.inject.Scope
          || annotation instanceof jakarta.inject.Singleton;
    }

    @Override
    public boolean acceptAnnotation(Class<? extends Annotation> annotationType) {
      return (annotationType == jakarta.inject.Inject.class
          || annotationType == jakarta.inject.Named.class
          || annotationType == jakarta.inject.Qualifier.class
          || annotationType == jakarta.inject.Scope.class
          || annotationType == jakarta.inject.Singleton.class);
    }

    @Override
    public Annotation getInjectAnnotation(AnnotatedElement member) {
      if (member != null) {
        return member.getAnnotation(jakarta.inject.Inject.class);
      }
      return null;
    }

    @Override
    public boolean hasInjectAnnotation(AnnotatedElement member) {
      if (member != null) {
        return member.isAnnotationPresent(jakarta.inject.Inject.class);
      }
      return false;
    }

    @Override
    public Annotation canonicalizeNamed(Annotation annotation) {
      return Names.named(((jakarta.inject.Named) annotation).value());
    }

    @Override
    public Class<? extends Annotation> getScopeAnnotationType() {
      return jakarta.inject.Scope.class;
    }

    @Override
    public Class<? extends Annotation> getQualifierAnnotationType() {
      return jakarta.inject.Qualifier.class;
    }

    @Override
    public Class<? extends Annotation> getSingletonAnnotationType() {
      return jakarta.inject.Singleton.class;
    }
  }
}
