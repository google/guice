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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.inject.ConfigurationException;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Types;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.NoSuchElementException;

/**
 * Static methods for working with types that we aren't publishing in the public {@code Types} API.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MoreTypes {

  public static final Type[] EMPTY_TYPE_ARRAY = new Type[] {};

  private MoreTypes() {}

  private static final ImmutableMap<TypeLiteral<?>, TypeLiteral<?>> PRIMITIVE_TO_WRAPPER =
      new ImmutableMap.Builder<TypeLiteral<?>, TypeLiteral<?>>()
          .put(TypeLiteral.get(boolean.class), TypeLiteral.get(Boolean.class))
          .put(TypeLiteral.get(byte.class), TypeLiteral.get(Byte.class))
          .put(TypeLiteral.get(short.class), TypeLiteral.get(Short.class))
          .put(TypeLiteral.get(int.class), TypeLiteral.get(Integer.class))
          .put(TypeLiteral.get(long.class), TypeLiteral.get(Long.class))
          .put(TypeLiteral.get(float.class), TypeLiteral.get(Float.class))
          .put(TypeLiteral.get(double.class), TypeLiteral.get(Double.class))
          .put(TypeLiteral.get(char.class), TypeLiteral.get(Character.class))
          .put(TypeLiteral.get(void.class), TypeLiteral.get(Void.class))
          .build();

  /**
   * Returns a key that doesn't hold any references to parent classes. This is necessary for
   * anonymous keys, so ensure we don't hold a ref to the containing module (or class) forever.
   */
  public static <T> Key<T> canonicalizeKey(Key<T> key) {
    // If we know this isn't a subclass, return as-is.
    // Otherwise, recreate the key to avoid the subclass
    if (key.getClass() == Key.class) {
      return key;
    } else {
      return key.ofType(key.getTypeLiteral());
    }
  }

  /**
   * Returns an type that's appropriate for use in a key.
   *
   * <p>If the raw type of {@code typeLiteral} is a {@code javax.inject.Provider}, this returns a
   * {@code com.google.inject.Provider} with the same type parameters.
   *
   * <p>If the type is a primitive, the corresponding wrapper type will be returned.
   *
   * @throws ConfigurationException if {@code type} contains a type variable
   */
  public static <T> TypeLiteral<T> canonicalizeForKey(TypeLiteral<T> typeLiteral) {
    Type type = typeLiteral.getType();
    if (!isFullySpecified(type)) {
      Errors errors = new Errors().keyNotFullySpecified(typeLiteral);
      throw new ConfigurationException(errors.getMessages());
    }

    if (typeLiteral.getRawType() == javax.inject.Provider.class) {
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // the following casts are generally unsafe, but com.google.inject.Provider extends
      // javax.inject.Provider and is covariant
      @SuppressWarnings("unchecked")
      TypeLiteral<T> guiceProviderType =
          (TypeLiteral<T>)
              TypeLiteral.get(Types.providerOf(parameterizedType.getActualTypeArguments()[0]));
      return guiceProviderType;
    }

    @SuppressWarnings("unchecked")
    TypeLiteral<T> wrappedPrimitives = (TypeLiteral<T>) PRIMITIVE_TO_WRAPPER.get(typeLiteral);
    if (wrappedPrimitives != null) {
      return wrappedPrimitives;
    }

    // If we know this isn't a subclass, return as-is.
    if (typeLiteral.getClass() == TypeLiteral.class) {
      return typeLiteral;
    }

    // recreate the TypeLiteral to avoid anonymous TypeLiterals from holding refs to their
    // surrounding classes.
    @SuppressWarnings("unchecked")
    TypeLiteral<T> recreated = (TypeLiteral<T>) TypeLiteral.get(typeLiteral.getType());
    return recreated;
  }

  /** Returns true if {@code type} is free from type variables. */
  private static boolean isFullySpecified(Type type) {
    if (type instanceof Class) {
      return true;

    } else if (type instanceof CompositeType) {
      return ((CompositeType) type).isFullySpecified();

    } else if (type instanceof TypeVariable) {
      return false;

    } else {
      return ((CompositeType) canonicalize(type)).isFullySpecified();
    }
  }

  /**
   * Returns a type that is functionally equal but not necessarily equal according to {@link
   * Object#equals(Object) Object.equals()}. The returned type is {@link Serializable}.
   */
  public static Type canonicalize(Type type) {
    if (type instanceof Class) {
      Class<?> c = (Class<?>) type;
      return c.isArray() ? new GenericArrayTypeImpl(canonicalize(c.getComponentType())) : c;

    } else if (type instanceof CompositeType) {
      return type;

    } else if (type instanceof ParameterizedType) {
      ParameterizedType p = (ParameterizedType) type;
      return new ParameterizedTypeImpl(
          p.getOwnerType(), p.getRawType(), p.getActualTypeArguments());

    } else if (type instanceof GenericArrayType) {
      GenericArrayType g = (GenericArrayType) type;
      return new GenericArrayTypeImpl(g.getGenericComponentType());

    } else if (type instanceof WildcardType) {
      WildcardType w = (WildcardType) type;
      return new WildcardTypeImpl(w.getUpperBounds(), w.getLowerBounds());

    } else {
      // type is either serializable as-is or unsupported
      return type;
    }
  }

  public static Class<?> getRawType(Type type) {
    if (type instanceof Class<?>) {
      // type is a normal class.
      return (Class<?>) type;

    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;

      // I'm not exactly sure why getRawType() returns Type instead of Class.
      // Neal isn't either but suspects some pathological case related
      // to nested classes exists.
      Type rawType = parameterizedType.getRawType();
      checkArgument(
          rawType instanceof Class,
          "Expected a Class, but <%s> is of type %s",
          type,
          type.getClass().getName());
      return (Class<?>) rawType;

    } else if (type instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) type).getGenericComponentType();
      return Array.newInstance(getRawType(componentType), 0).getClass();

    } else if (type instanceof TypeVariable || type instanceof WildcardType) {
      // we could use the variable's bounds, but that'll won't work if there are multiple.
      // having a raw type that's more general than necessary is okay
      return Object.class;

    } else {
      throw new IllegalArgumentException(
          "Expected a Class, ParameterizedType, or "
              + "GenericArrayType, but <"
              + type
              + "> is of type "
              + type.getClass().getName());
    }
  }

  /** Returns true if {@code a} and {@code b} are equal. */
  public static boolean equals(Type a, Type b) {
    if (a == b) {
      // also handles (a == null && b == null)
      return true;

    } else if (a instanceof Class) {
      // Class already specifies equals().
      return a.equals(b);

    } else if (a instanceof ParameterizedType) {
      if (!(b instanceof ParameterizedType)) {
        return false;
      }

      // TODO: save a .clone() call
      ParameterizedType pa = (ParameterizedType) a;
      ParameterizedType pb = (ParameterizedType) b;
      return Objects.equal(pa.getOwnerType(), pb.getOwnerType())
          && pa.getRawType().equals(pb.getRawType())
          && Arrays.equals(pa.getActualTypeArguments(), pb.getActualTypeArguments());

    } else if (a instanceof GenericArrayType) {
      if (!(b instanceof GenericArrayType)) {
        return false;
      }

      GenericArrayType ga = (GenericArrayType) a;
      GenericArrayType gb = (GenericArrayType) b;
      return equals(ga.getGenericComponentType(), gb.getGenericComponentType());

    } else if (a instanceof WildcardType) {
      if (!(b instanceof WildcardType)) {
        return false;
      }

      WildcardType wa = (WildcardType) a;
      WildcardType wb = (WildcardType) b;
      return Arrays.equals(wa.getUpperBounds(), wb.getUpperBounds())
          && Arrays.equals(wa.getLowerBounds(), wb.getLowerBounds());

    } else if (a instanceof TypeVariable) {
      if (!(b instanceof TypeVariable)) {
        return false;
      }
      TypeVariable<?> va = (TypeVariable) a;
      TypeVariable<?> vb = (TypeVariable) b;
      return va.getGenericDeclaration().equals(vb.getGenericDeclaration())
          && va.getName().equals(vb.getName());

    } else {
      // This isn't a type we support. Could be a generic array type, wildcard type, etc.
      return false;
    }
  }

  private static int hashCodeOrZero(Object o) {
    return o != null ? o.hashCode() : 0;
  }

  public static String typeToString(Type type) {
    return type instanceof Class ? ((Class) type).getName() : type.toString();
  }

  /**
   * Returns the generic supertype for {@code type}. For example, given a class {@code IntegerSet},
   * the result for when supertype is {@code Set.class} is {@code Set<Integer>} and the result when
   * the supertype is {@code Collection.class} is {@code Collection<Integer>}.
   */
  public static Type getGenericSupertype(Type type, Class<?> rawType, Class<?> toResolve) {
    if (toResolve == rawType) {
      return type;
    }

    // we skip searching through interfaces if unknown is an interface
    if (toResolve.isInterface()) {
      Class<?>[] interfaces = rawType.getInterfaces();
      for (int i = 0, length = interfaces.length; i < length; i++) {
        if (interfaces[i] == toResolve) {
          return rawType.getGenericInterfaces()[i];
        } else if (toResolve.isAssignableFrom(interfaces[i])) {
          return getGenericSupertype(rawType.getGenericInterfaces()[i], interfaces[i], toResolve);
        }
      }
    }

    // check our supertypes
    if (!rawType.isInterface()) {
      while (rawType != Object.class) {
        Class<?> rawSupertype = rawType.getSuperclass();
        if (rawSupertype == toResolve) {
          return rawType.getGenericSuperclass();
        } else if (toResolve.isAssignableFrom(rawSupertype)) {
          return getGenericSupertype(rawType.getGenericSuperclass(), rawSupertype, toResolve);
        }
        rawType = rawSupertype;
      }
    }

    // we can't resolve this further
    return toResolve;
  }

  public static Type resolveTypeVariable(Type type, Class<?> rawType, TypeVariable<?> unknown) {
    Class<?> declaredByRaw = declaringClassOf(unknown);

    // we can't reduce this further
    if (declaredByRaw == null) {
      return unknown;
    }

    Type declaredBy = getGenericSupertype(type, rawType, declaredByRaw);
    if (declaredBy instanceof ParameterizedType) {
      int index = indexOf(declaredByRaw.getTypeParameters(), unknown);
      return ((ParameterizedType) declaredBy).getActualTypeArguments()[index];
    }

    return unknown;
  }

  private static int indexOf(Object[] array, Object toFind) {
    for (int i = 0; i < array.length; i++) {
      if (toFind.equals(array[i])) {
        return i;
      }
    }
    throw new NoSuchElementException();
  }

  /**
   * Returns the declaring class of {@code typeVariable}, or {@code null} if it was not declared by
   * a class.
   */
  private static Class<?> declaringClassOf(TypeVariable<?> typeVariable) {
    GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
    return genericDeclaration instanceof Class ? (Class<?>) genericDeclaration : null;
  }

  public static class ParameterizedTypeImpl
      implements ParameterizedType, Serializable, CompositeType {
    private final Type ownerType;
    private final Type rawType;
    private final Type[] typeArguments;

    public ParameterizedTypeImpl(Type ownerType, Type rawType, Type... typeArguments) {
      // require an owner type if the raw type needs it
      ensureOwnerType(ownerType, rawType);

      this.ownerType = ownerType == null ? null : canonicalize(ownerType);
      this.rawType = canonicalize(rawType);
      int providedArgumentLength = typeArguments.length;
      Type[] clonedTypeArguments = typeArguments.clone();
      int validArgLength = providedArgumentLength;
      if (this.rawType instanceof Class) {
        Class<?> klass = (Class) this.rawType;
        int classArgumentLength = klass.getTypeParameters().length;
        // TODO(b/163147654): change following if condition from < to =
        if (providedArgumentLength < classArgumentLength) {
          throw new IllegalArgumentException(
              "Length of provided type arguments is less than length of required parameters for"
                  + " class:"
                  + klass.getName()
                  + " provided type argument length:"
                  + providedArgumentLength
                  + " length of class parameters:"
                  + classArgumentLength);
        } else if (providedArgumentLength > classArgumentLength) {
          validArgLength = classArgumentLength;
        }
      }

      this.typeArguments = new Type[validArgLength];
      for (int t = 0; t < validArgLength; t++) {
        checkNotNull(clonedTypeArguments[t], "type parameter");
        checkNotPrimitive(clonedTypeArguments[t], "type parameters");
        this.typeArguments[t] = canonicalize(clonedTypeArguments[t]);
      }
    }

    @Override
    public Type[] getActualTypeArguments() {
      return typeArguments.clone();
    }

    @Override
    public Type getRawType() {
      return rawType;
    }

    @Override
    public Type getOwnerType() {
      return ownerType;
    }

    @Override
    public boolean isFullySpecified() {
      if (ownerType != null && !MoreTypes.isFullySpecified(ownerType)) {
        return false;
      }

      if (!MoreTypes.isFullySpecified(rawType)) {
        return false;
      }

      for (Type type : typeArguments) {
        if (!MoreTypes.isFullySpecified(type)) {
          return false;
        }
      }

      return true;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof ParameterizedType
          && MoreTypes.equals(this, (ParameterizedType) other);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(typeArguments) ^ rawType.hashCode() ^ hashCodeOrZero(ownerType);
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder = new StringBuilder(30 * (typeArguments.length + 1));
      stringBuilder.append(typeToString(rawType));

      if (typeArguments.length == 0) {
        return stringBuilder.toString();
      }

      stringBuilder.append("<").append(typeToString(typeArguments[0]));
      for (int i = 1; i < typeArguments.length; i++) {
        stringBuilder.append(", ").append(typeToString(typeArguments[i]));
      }
      return stringBuilder.append(">").toString();
    }

    private static void ensureOwnerType(Type ownerType, Type rawType) {
      if (rawType instanceof Class<?>) {
        Class<?> rawTypeAsClass = (Class<?>) rawType;
        checkArgument(
            ownerType != null || rawTypeAsClass.getEnclosingClass() == null,
            "No owner type for enclosed %s",
            rawType);
        checkArgument(
            ownerType == null || rawTypeAsClass.getEnclosingClass() != null,
            "Owner type for unenclosed %s",
            rawType);
      }
    }

    private static final long serialVersionUID = 0;
  }

  public static class GenericArrayTypeImpl
      implements GenericArrayType, Serializable, CompositeType {
    private final Type componentType;

    public GenericArrayTypeImpl(Type componentType) {
      this.componentType = canonicalize(componentType);
    }

    @Override
    public Type getGenericComponentType() {
      return componentType;
    }

    @Override
    public boolean isFullySpecified() {
      return MoreTypes.isFullySpecified(componentType);
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof GenericArrayType && MoreTypes.equals(this, (GenericArrayType) o);
    }

    @Override
    public int hashCode() {
      return componentType.hashCode();
    }

    @Override
    public String toString() {
      return typeToString(componentType) + "[]";
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * The WildcardType interface supports multiple upper bounds and multiple lower bounds. We only
   * support what the Java 6 language needs - at most one bound. If a lower bound is set, the upper
   * bound must be Object.class.
   */
  public static class WildcardTypeImpl implements WildcardType, Serializable, CompositeType {
    private final Type upperBound;
    private final Type lowerBound;

    public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
      checkArgument(lowerBounds.length <= 1, "Must have at most one lower bound.");
      checkArgument(upperBounds.length == 1, "Must have exactly one upper bound.");

      if (lowerBounds.length == 1) {
        checkNotNull(lowerBounds[0], "lowerBound");
        checkNotPrimitive(lowerBounds[0], "wildcard bounds");
        checkArgument(upperBounds[0] == Object.class, "bounded both ways");
        this.lowerBound = canonicalize(lowerBounds[0]);
        this.upperBound = Object.class;

      } else {
        checkNotNull(upperBounds[0], "upperBound");
        checkNotPrimitive(upperBounds[0], "wildcard bounds");
        this.lowerBound = null;
        this.upperBound = canonicalize(upperBounds[0]);
      }
    }

    @Override
    public Type[] getUpperBounds() {
      return new Type[] {upperBound};
    }

    @Override
    public Type[] getLowerBounds() {
      return lowerBound != null ? new Type[] {lowerBound} : EMPTY_TYPE_ARRAY;
    }

    @Override
    public boolean isFullySpecified() {
      return MoreTypes.isFullySpecified(upperBound)
          && (lowerBound == null || MoreTypes.isFullySpecified(lowerBound));
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof WildcardType && MoreTypes.equals(this, (WildcardType) other);
    }

    @Override
    public int hashCode() {
      // this equals Arrays.hashCode(getLowerBounds()) ^ Arrays.hashCode(getUpperBounds());
      return (lowerBound != null ? 31 + lowerBound.hashCode() : 1) ^ (31 + upperBound.hashCode());
    }

    @Override
    public String toString() {
      if (lowerBound != null) {
        return "? super " + typeToString(lowerBound);
      } else if (upperBound == Object.class) {
        return "?";
      } else {
        return "? extends " + typeToString(upperBound);
      }
    }

    private static final long serialVersionUID = 0;
  }

  private static void checkNotPrimitive(Type type, String use) {
    checkArgument(
        !(type instanceof Class<?>) || !((Class) type).isPrimitive(),
        "Primitive types are not allowed in %s: %s",
        use,
        type);
  }

  /** A type formed from other types, such as arrays, parameterized types or wildcard types */
  private interface CompositeType {
    /** Returns true if there are no type variables in this type. */
    boolean isFullySpecified();
  }
}
