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

import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.internal.Annotations;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.internal.ImmutableList;
import com.google.inject.internal.ImmutableSet;
import com.google.inject.internal.Lists;
import com.google.inject.internal.MoreTypes;
import static com.google.inject.internal.MoreTypes.getRawType;
import com.google.inject.internal.Nullability;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * A constructor, field or method that can receive injections. Typically this is a member with the
 * {@literal @}{@link Inject} annotation. For non-private, no argument constructors, the member may
 * omit the annotation.
 *
 * @author crazybob@google.com (Bob Lee)
 * @since 2.0
 */
public final class InjectionPoint {

  private final boolean optional;
  private final Member member;
  private final TypeLiteral<?> declaringType;
  private final ImmutableList<Dependency<?>> dependencies;

  InjectionPoint(TypeLiteral<?> declaringType, Method method, boolean optional) {
    this.member = method;
    this.declaringType = declaringType;
    this.optional = optional;
    this.dependencies = forMember(method, declaringType, method.getParameterAnnotations());
  }

  InjectionPoint(TypeLiteral<?> declaringType, Constructor<?> constructor) {
    this.member = constructor;
    this.declaringType = declaringType;
    this.optional = false;
    this.dependencies = forMember(
        constructor, declaringType, constructor.getParameterAnnotations());
  }

  InjectionPoint(TypeLiteral<?> declaringType, Field field, boolean optional) {
    this.member = field;
    this.declaringType = declaringType;
    this.optional = optional;

    Annotation[] annotations = field.getAnnotations();

    Errors errors = new Errors(field);
    Key<?> key = null;
    try {
      key = Annotations.getKey(declaringType.getFieldType(field), field, annotations, errors);
    } catch (ConfigurationException e) {
      errors.merge(e.getErrorMessages());
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
    errors.throwConfigurationExceptionIfErrorsExist();

    this.dependencies = ImmutableList.<Dependency<?>>of(
        newDependency(key, Nullability.allowsNull(annotations), -1));
  }

  private ImmutableList<Dependency<?>> forMember(Member member, TypeLiteral<?> type,
      Annotation[][] paramterAnnotations) {
    Errors errors = new Errors(member);
    Iterator<Annotation[]> annotationsIterator = Arrays.asList(paramterAnnotations).iterator();

    List<Dependency<?>> dependencies = Lists.newArrayList();
    int index = 0;

    for (TypeLiteral<?> parameterType : type.getParameterTypes(member)) {
      try {
        Annotation[] parameterAnnotations = annotationsIterator.next();
        Key<?> key = Annotations.getKey(parameterType, member, parameterAnnotations, errors);
        dependencies.add(newDependency(key, Nullability.allowsNull(parameterAnnotations), index));
        index++;
      } catch (ConfigurationException e) {
        errors.merge(e.getErrorMessages());
      } catch (ErrorsException e) {
        errors.merge(e.getErrors());
      }
    }

    errors.throwConfigurationExceptionIfErrorsExist();
    return ImmutableList.copyOf(dependencies);
  }

  // This metohd is necessary to create a Dependency<T> with proper generic type information
  private <T> Dependency<T> newDependency(Key<T> key, boolean allowsNull, int parameterIndex) {
    return new Dependency<T>(this, key, allowsNull, parameterIndex);
  }

  /**
   * Returns the injected constructor, field, or method.
   */
  public Member getMember() {
    // TODO: Don't expose the original member (which probably has setAccessible(true)).
    return member;
  }

  /**
   * Returns the dependencies for this injection point. If the injection point is for a method or
   * constructor, the dependencies will correspond to that member's parameters. Field injection
   * points always have a single dependency for the field itself.
   *
   * @return a possibly-empty list
   */
  public List<Dependency<?>> getDependencies() {
    return dependencies;
  }

  /**
   * Returns true if this injection point shall be skipped if the injector cannot resolve bindings
   * for all required dependencies. Both explicit bindings (as specified in a module), and implicit
   * bindings ({@literal @}{@link com.google.inject.ImplementedBy ImplementedBy}, default
   * constructors etc.) may be used to satisfy optional injection points.
   */
  public boolean isOptional() {
    return optional;
  }

  /**
   * Returns the generic type that defines this injection point. If the member exists on a
   * parameterized type, the result will include more type information than the member's {@link
   * Member#getDeclaringClass() raw declaring class}.
   */
  public TypeLiteral<?> getDeclaringType() {
    return declaringType;
  }

  @Override public boolean equals(Object o) {
    return o instanceof InjectionPoint
        && member.equals(((InjectionPoint) o).member)
        && declaringType.equals(((InjectionPoint) o).declaringType);
  }

  @Override public int hashCode() {
    return member.hashCode() ^ declaringType.hashCode();
  }

  @Override public String toString() {
    return MoreTypes.toString(member);
  }

  /**
   * Returns a new injection point for the specified constructor. If the declaring type of {@code
   * constructor} is parameterized (such as {@code List<T>}), prefer the overload that includes a
   * type literal.
   *
   * @param constructor any single constructor present on {@code type}.
   */
  public static <T> InjectionPoint forConstructor(Constructor<T> constructor) {
    return new InjectionPoint(TypeLiteral.get(constructor.getDeclaringClass()), constructor);
  }

  /**
   * Returns a new injection point for the specified constructor of {@code type}.
   *
   * @param constructor any single constructor present on {@code type}.
   * @param type the concrete type that defines {@code constructor}.
   */
  public static <T> InjectionPoint forConstructor(
      Constructor<T> constructor, TypeLiteral<? extends T> type) {
    if (type.getRawType() != constructor.getDeclaringClass()) {
      new Errors(type)
          .constructorNotDefinedByType(constructor, type)
          .throwConfigurationExceptionIfErrorsExist();
    }

    return new InjectionPoint(type, constructor);
  }

  /**
   * Returns a new injection point for the injectable constructor of {@code type}.
   *
   * @param type a concrete type with exactly one constructor annotated {@literal @}{@link Inject},
   *     or a no-arguments constructor that is not private.
   * @throws ConfigurationException if there is no injectable constructor, more than one injectable
   *     constructor, or if parameters of the injectable constructor are malformed, such as a
   *     parameter with multiple binding annotations.
   */
  public static InjectionPoint forConstructorOf(TypeLiteral<?> type) {
    Class<?> rawType = getRawType(type.getType());
    Errors errors = new Errors(rawType);

    Constructor<?> injectableConstructor = null;
    for (Constructor<?> constructor : rawType.getDeclaredConstructors()) {

      boolean optional;
      Inject guiceInject = constructor.getAnnotation(Inject.class);
      if (guiceInject == null) {
        javax.inject.Inject javaxInject = constructor.getAnnotation(javax.inject.Inject.class);
        if (javaxInject == null) {
          continue;
        }
        optional = false;
      } else {
        optional = guiceInject.optional();
      }

      if (optional) {
        errors.optionalConstructor(constructor);
      }

      if (injectableConstructor != null) {
        errors.tooManyConstructors(rawType);
      }

      injectableConstructor = constructor;
      checkForMisplacedBindingAnnotations(injectableConstructor, errors);
    }

    errors.throwConfigurationExceptionIfErrorsExist();

    if (injectableConstructor != null) {
      return new InjectionPoint(type, injectableConstructor);
    }

    // If no annotated constructor is found, look for a no-arg constructor instead.
    try {
      Constructor<?> noArgConstructor = rawType.getDeclaredConstructor();

      // Disallow private constructors on non-private classes (unless they have @Inject)
      if (Modifier.isPrivate(noArgConstructor.getModifiers())
          && !Modifier.isPrivate(rawType.getModifiers())) {
        errors.missingConstructor(rawType);
        throw new ConfigurationException(errors.getMessages());
      }

      checkForMisplacedBindingAnnotations(noArgConstructor, errors);
      return new InjectionPoint(type, noArgConstructor);
    } catch (NoSuchMethodException e) {
      errors.missingConstructor(rawType);
      throw new ConfigurationException(errors.getMessages());
    }
  }

  /**
   * Returns a new injection point for the injectable constructor of {@code type}.
   *
   * @param type a concrete type with exactly one constructor annotated {@literal @}{@link Inject},
   *     or a no-arguments constructor that is not private.
   * @throws ConfigurationException if there is no injectable constructor, more than one injectable
   *     constructor, or if parameters of the injectable constructor are malformed, such as a
   *     parameter with multiple binding annotations.
   */
  public static InjectionPoint forConstructorOf(Class<?> type) {
    return forConstructorOf(TypeLiteral.get(type));
  }

  /**
   * Returns all static method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forStaticMethodsAndFields(TypeLiteral type) {
    Errors errors = new Errors();

    Set<InjectionPoint> result = getInjectionPoints(type, true, errors);
    if (errors.hasErrors()) {
      throw new ConfigurationException(errors.getMessages()).withPartialValue(result);
    }
    return result;
  }

  /**
   * Returns all static method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forStaticMethodsAndFields(Class<?> type) {
    return forStaticMethodsAndFields(TypeLiteral.get(type));
  }

  /**
   * Returns all instance method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forInstanceMethodsAndFields(TypeLiteral<?> type) {
    Errors errors = new Errors();
    Set<InjectionPoint> result = getInjectionPoints(type, false, errors);
    if (errors.hasErrors()) {
      throw new ConfigurationException(errors.getMessages()).withPartialValue(result);
    }
    return result;
  }

  /**
   * Returns all instance method and field injection points on {@code type}.
   *
   * @return a possibly empty set of injection points. The set has a specified iteration order. All
   *      fields are returned and then all methods. Within the fields, supertype fields are returned
   *      before subtype fields. Similarly, supertype methods are returned before subtype methods.
   * @throws ConfigurationException if there is a malformed injection point on {@code type}, such as
   *      a field with multiple binding annotations. The exception's {@link
   *      ConfigurationException#getPartialValue() partial value} is a {@code Set<InjectionPoint>}
   *      of the valid injection points.
   */
  public static Set<InjectionPoint> forInstanceMethodsAndFields(Class<?> type) {
    return forInstanceMethodsAndFields(TypeLiteral.get(type));
  }

  private static boolean checkForMisplacedBindingAnnotations(Member member, Errors errors) {
    Annotation misplacedBindingAnnotation = Annotations.findBindingAnnotation(
        errors, member, ((AnnotatedElement) member).getAnnotations());
    if (misplacedBindingAnnotation == null) {
      return false;
    }

    // don't warn about misplaced binding annotations on methods when there's a field with the same
    // name. In Scala, fields always get accessor methods (that we need to ignore). See bug 242.
    if (member instanceof Method) {
      try {
        if (member.getDeclaringClass().getDeclaredField(member.getName()) != null) {
          return false;
        }
      } catch (NoSuchFieldException ignore) {
      }
    }

    errors.misplacedBindingAnnotation(member, misplacedBindingAnnotation);
    return true;
  }

  static abstract class InjectableMember {
    final TypeLiteral<?> declaringType;
    final boolean injectable;
    final boolean optional;
    final boolean jsr330;

    InjectableMember(TypeLiteral<?> declaringType, AnnotatedElement member) {
      this.declaringType = declaringType;

      javax.inject.Inject jsr330Inject = member.getAnnotation(javax.inject.Inject.class);
      if (jsr330Inject != null) {
        injectable = true;
        optional = false;
        jsr330 = true;
        return;
      }

      jsr330 = false;

      Inject inject = member.getAnnotation(Inject.class);
      if (inject != null) {
        injectable = true;
        optional = inject.optional();
        return;
      }

      injectable = false;
      optional = false;
    }

    abstract InjectionPoint toInjectionPoint(Errors errors);
  }

  static class InjectableField extends InjectableMember {
    final Field field;
    InjectableField(TypeLiteral<?> declaringType, Field field) {
      super(declaringType, field);
      this.field = field;
    }

    InjectionPoint toInjectionPoint(Errors errors) {
      return new InjectionPoint(declaringType, field, optional);
    }
  }

  static class InjectableMethod extends InjectableMember {
    final Method method;
    InjectableMethod(TypeLiteral<?> declaringType, Method method) {
      super(declaringType, method);
      this.method = method;
    }

    InjectionPoint toInjectionPoint(Errors errors) {
      checkForMisplacedBindingAnnotations(method, errors);
      return new InjectionPoint(declaringType, method, optional);
    }
  }

  private static Set<InjectionPoint> getInjectionPoints(final TypeLiteral<?> type,
      boolean statics, Errors errors) {
    LinkedHashMap<Member, InjectableMember> injectableMembers
        = new LinkedHashMap<Member, InjectableMember>();
    HashMap<Signature, List<Method>> bySignature = null;

    for (TypeLiteral<?> current : hierarchyFor(type)) {
      for (Field field : current.getRawType().getDeclaredFields()) {
        if (Modifier.isStatic(field.getModifiers()) == statics) {
          InjectableField injectableField = new InjectableField(current, field);
          if (injectableField.injectable) {
            if (injectableField.jsr330 && Modifier.isFinal(field.getModifiers())) {
              errors.cannotInjectFinalField(field);
              continue;
            }
            injectableMembers.put(field, injectableField);
          }
        }
      }

      for (Method method : current.getRawType().getDeclaredMethods()) {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        if (isStatic == statics) {
          InjectableMethod injectableMethod = new InjectableMethod(current, method);

          if (injectableMethod.injectable) {
            boolean result = true;
            if (injectableMethod.jsr330) {
              if (Modifier.isAbstract(method.getModifiers())) {
                errors.cannotInjectAbstractMethod(method);
                result = false;
              }
              if (method.getTypeParameters().length > 0) {
                errors.cannotInjectMethodWithTypeParameters(method);
                result = false;
              }
            }
            if (!result) {
              continue;
            }
            if (isStatic && statics) {
              injectableMembers.put(method, injectableMethod);
            }
          }

          if (!isStatic) {
            // Remove overridden method if present.
            List<Method> possibleOverrides = null;
            Signature signature = new Signature(method);
            if (bySignature == null) {
              // Lazily initialize the bySignature map.
              bySignature = new HashMap<Signature, List<Method>>();
            } else {
              possibleOverrides = bySignature.get(signature);
              if (possibleOverrides != null) {
                Method overridden = removeOverriddenMethod(method, possibleOverrides);
                if (overridden != null) {
                  injectableMembers.remove(overridden);
                }
              }
            }

            if (injectableMethod.injectable) {
              // Keep track of this method in case it gets overridden.
              if (possibleOverrides == null) {
                possibleOverrides = new ArrayList<Method>();
                bySignature.put(signature, possibleOverrides);
              }
              possibleOverrides.add(method);
              injectableMembers.put(method, injectableMethod);
            }
          }
        }
      }
    }

    if (injectableMembers.isEmpty()) {
      return Collections.emptySet();
    }

    ImmutableSet.Builder<InjectionPoint> builder = ImmutableSet.builder();
    for (InjectableMember injectableMember : injectableMembers.values()) {
      try {
        builder.add(injectableMember.toInjectionPoint(errors));
      } catch (ConfigurationException ignorable) {
        if (!injectableMember.optional) {
          errors.merge(ignorable.getErrorMessages());
        }
      }
    }
    return builder.build();
  }

  private static List<TypeLiteral<?>> hierarchyFor(TypeLiteral<?> type) {
    // Construct type hierarchy from Object.class down.
    List<TypeLiteral<?>> hierarchy = new ArrayList<TypeLiteral<?>>();
    TypeLiteral<?> current = type;
    while (current.getRawType() != Object.class) {
      hierarchy.add(current);
      current = current.getSupertype(current.getRawType().getSuperclass());
    }
    Collections.reverse(hierarchy);
    return hierarchy;
  }

  private static Method removeOverriddenMethod(Method method, List<Method> possibleOverrides) {
    for (Iterator<Method> iterator = possibleOverrides.iterator(); iterator.hasNext();) {
      Method possibleOverride = iterator.next();
      if (overrides(method, possibleOverride)) {
        iterator.remove();
        return possibleOverride;
      }
    }
    return null;
  }

  /**
   * Returns true if a overrides b. Assumes signatures of a and b are the same and a's declaring
   * class is a subclass of b's declaring class.
   */
  private static boolean overrides(Method a, Method b) {
    // See JLS section 8.4.8.1
    // TODO: Do I need to worry about the visibility of the declaring class? I don't think so...
    int modifiers = b.getModifiers();
    if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
      return true;
    }
    if (Modifier.isPrivate(modifiers)) {
      return false;
    }
    // b must be package-private
    return a.getDeclaringClass().getPackage().equals(b.getDeclaringClass().getPackage());
  }

  /**
   * A method signature. Used to handle method overridding.
   */
  static class Signature {

    final String name;
    final Class[] parameterTypes;
    final int hash;

    Signature(Method method) {
      this.name = method.getName();
      this.parameterTypes = method.getParameterTypes();

      int h = name.hashCode();
      h = h * 31 + parameterTypes.length;
      for (Class parameterType : parameterTypes) {
        h = h * 31 + parameterType.hashCode();
      }
      this.hash = h;
    }

    @Override public int hashCode() {
      return this.hash;
    }

    @Override public boolean equals(Object o) {
      if (!(o instanceof Signature)) {
        return false;
      }

      Signature other = (Signature) o;
      if (!name.equals(other.name)) {
        return false;
      }

      if (parameterTypes.length != other.parameterTypes.length) {
        return false;
      }

      for (int i = 0; i < parameterTypes.length; i++) {
        if (parameterTypes[i] != other.parameterTypes[i]) {
          return false;
        }
      }

      return true;
    }
  }
}
