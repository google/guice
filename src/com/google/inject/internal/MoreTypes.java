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


package com.google.inject.internal;

import com.google.common.base.Objects;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableMap;
import com.google.inject.TypeLiteral;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Map;

/**
 * Static methods for working with types that we aren't publishing in the
 * public {@code Types} API.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class MoreTypes {

  public static final Type[] EMPTY_TYPE_ARRAY = new Type[] {};

  private MoreTypes() {}

  private static final Map<TypeLiteral<?>, TypeLiteral<?>> PRIMITIVE_TO_WRAPPER
      = new ImmutableMap.Builder<TypeLiteral<?>, TypeLiteral<?>>()
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
   * Returns an equivalent (but not necessarily equal) type literal that is
   * free of primitive types. Type literals of primitives will return the
   * corresponding wrapper types.
   */
  public static <T> TypeLiteral<T> wrapPrimitives(TypeLiteral<T> typeLiteral) {
    @SuppressWarnings("unchecked")
    TypeLiteral<T> wrappedPrimitives = (TypeLiteral<T>) PRIMITIVE_TO_WRAPPER.get(typeLiteral);
    return wrappedPrimitives != null
        ? wrappedPrimitives
        : typeLiteral;
  }

  /**
   * Returns a type that is functionally equal but not necessarily equal
   * according to {@link Object#equals(Object) Object.equals()}. The returned
   * type is {@link Serializable}.
   */
  public static Type canonicalize(Type type) {
    if (type instanceof ParameterizedTypeImpl
        || type instanceof GenericArrayTypeImpl
        || type instanceof WildcardTypeImpl) {
      return type;

    } else if (type instanceof ParameterizedType) {
      ParameterizedType p = (ParameterizedType) type;
      return new ParameterizedTypeImpl(p.getOwnerType(),
          p.getRawType(), p.getActualTypeArguments());

    } else if (type instanceof GenericArrayType) {
      GenericArrayType g = (GenericArrayType) type;
      return new GenericArrayTypeImpl(g.getGenericComponentType());

    } else if (type instanceof Class && ((Class<?>) type).isArray()) {
      Class<?> c = (Class<?>) type;
      return new GenericArrayTypeImpl(c.getComponentType());

    } else if (type instanceof WildcardType) {
      WildcardType w = (WildcardType) type;
      return new WildcardTypeImpl(w.getUpperBounds(), w.getLowerBounds());

    } else {
      // type is either serializable as-is or unsupported
      return type;
    }
  }

  /**
   * Returns a type that's functionally equal but not necessarily equal
   * according to {@link Object#equals(Object) Object.equals}. The returned
   * member is {@link Serializable}.
   */
  public static Member serializableCopy(Member member) {
    return member instanceof MemberImpl
        ? member
        : new MemberImpl(member);
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
      checkArgument(rawType instanceof Class,
          "Expected a Class, but <%s> is of type %s", type, type.getClass().getName());
      return (Class<?>) rawType;

    } else if (type instanceof GenericArrayType) {
      // TODO: Is this sufficient?
      return Object[].class;

    } else {
      throw new IllegalArgumentException("Expected a Class, ParameterizedType, or "
          + "GenericArrayType, but <" + type + "> is of type " + type.getClass().getName());
    }
  }

  /**
   * Returns true if {@code a} and {@code b} are equal.
   */
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

      // TODO(jessewilson): save a .clone() call
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

    } else {
      // This isn't a type we support. Could be a generic array type, wildcard
      // type, etc.
      return false;
    }
  }

  /**
   * Returns the hashCode of {@code type}.
   */
  public static int hashCode(Type type) {
    if (type instanceof Class) {
      // Class specifies hashCode().
      return type.hashCode();

    } else if (type instanceof ParameterizedType) {
      ParameterizedType p = (ParameterizedType) type;
      return Arrays.hashCode(p.getActualTypeArguments())
          ^ p.getRawType().hashCode()
          ^ hashCodeOrZero(p.getOwnerType());

    } else if (type instanceof GenericArrayType) {
      return hashCode(((GenericArrayType) type).getGenericComponentType());

    } else if (type instanceof WildcardType) {
      WildcardType w = (WildcardType) type;
      return Arrays.hashCode(w.getLowerBounds()) ^ Arrays.hashCode(w.getUpperBounds());

    } else {
      // This isn't a type we support. Could be a generic array type, wildcard type, etc.
      return hashCodeOrZero(type);
    }
  }

  private static int hashCodeOrZero(Object o) {
    return o != null ? o.hashCode() : 0;
  }

  public static String toString(Type type) {
    if (type instanceof Class<?>) {
      return ((Class) type).getName();

    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Type[] arguments = parameterizedType.getActualTypeArguments();
      Type ownerType = parameterizedType.getOwnerType();
      StringBuilder stringBuilder = new StringBuilder();
      if (ownerType != null) {
        stringBuilder.append(toString(ownerType)).append(".");
      }
      stringBuilder.append(toString(parameterizedType.getRawType()))
          .append("<")
          .append(toString(arguments[0]));
      for (int i = 1; i < arguments.length; i++) {
        stringBuilder.append(", ").append(toString(arguments[i]));
      }
      return stringBuilder.append(">").toString();

    } else if (type instanceof GenericArrayType) {
      return toString(((GenericArrayType) type).getGenericComponentType()) + "[]";

    } else if (type instanceof WildcardType) {
      WildcardType wildcardType = (WildcardType) type;
      Type[] lowerBounds = wildcardType.getLowerBounds();
      Type[] upperBounds = wildcardType.getUpperBounds();

      if (upperBounds.length != 1 || lowerBounds.length > 1) {
        throw new UnsupportedOperationException("Unsupported wildcard type " + type);
      }

      if (lowerBounds.length == 1) {
        if (upperBounds[0] != Object.class) {
          throw new UnsupportedOperationException("Unsupported wildcard type " + type);
        }
        return "? super " + toString(lowerBounds[0]);
      } else if (upperBounds[0] == Object.class) {
        return "?";
      } else {
        return "? extends " + toString(upperBounds[0]);
      }

    } else {
      return type.toString();
    }
  }

  /**
   * Returns {@code Field.class}, {@code Method.class} or {@code Constructor.class}.
   */
  public static Class<? extends Member> memberType(Member member) {
    checkNotNull(member, "member");

    if (member instanceof MemberImpl) {
      return ((MemberImpl) member).memberType;

    } else if (member instanceof Field) {
      return Field.class;

    } else if (member instanceof Method) {
      return Method.class;

    } else if (member instanceof Constructor) {
      return Constructor.class;

    } else {
      throw new IllegalArgumentException(
          "Unsupported implementation class for Member, " + member.getClass());
    }
  }

  /**
   * Formats a member as concise string, such as {@code java.util.ArrayList.size},
   * {@code java.util.ArrayList<init>()} or {@code java.util.List.remove()}.
   */
  public static String toString(Member member) {
    Class<? extends Member> memberType = memberType(member);

    if (memberType == Method.class) {
      return member.getDeclaringClass().getName() + "." + member.getName() + "()";
    } else if (memberType == Field.class) {
      return member.getDeclaringClass().getName() + "." + member.getName();
    } else if (memberType == Constructor.class) {
      return member.getDeclaringClass().getName() + ".<init>()";
    } else {
      throw new AssertionError();
    }
  }

  public static String memberKey(Member member) {
    checkNotNull(member, "member");

    if (member instanceof MemberImpl) {
      return ((MemberImpl) member).memberKey;

    } else if (member instanceof Field) {
      return member.getName();

    } else if (member instanceof Method) {
      return member.getName() + org.objectweb.asm.Type.getMethodDescriptor((Method) member);

    } else if (member instanceof Constructor) {
      StringBuilder sb = new StringBuilder().append("<init>(");
      for (Class param : ((Constructor) member).getParameterTypes()) {
          sb.append(org.objectweb.asm.Type.getDescriptor(param));
      }
      return sb.append(")V").toString();

    } else {
      throw new IllegalArgumentException(
          "Unsupported implementation class for Member, " + member.getClass());
    }
  }

  public static class ParameterizedTypeImpl implements ParameterizedType, Serializable {
    private final Type ownerType;
    private final Type rawType;
    private final Type[] typeArguments;

    public ParameterizedTypeImpl(Type ownerType, Type rawType, Type... typeArguments) {
      // require an owner type if the raw type needs it
      if (rawType instanceof Class<?>) {
        Class rawTypeAsClass = (Class) rawType;
        checkArgument(ownerType != null || rawTypeAsClass.getEnclosingClass() == null,
            "No owner type for enclosed %s", rawType);
        checkArgument(ownerType == null || rawTypeAsClass.getEnclosingClass() != null,
            "Owner type for unenclosed %s", rawType);
      }

      this.ownerType = ownerType == null ? null : canonicalize(ownerType);
      this.rawType = canonicalize(rawType);
      this.typeArguments = typeArguments.clone();
      for (int t = 0; t < this.typeArguments.length; t++) {
        checkNotNull(this.typeArguments[t], "type parameter");
        checkNotPrimitive(this.typeArguments[t], "type parameters");
        this.typeArguments[t] = canonicalize(this.typeArguments[t]);
      }
    }

    public Type[] getActualTypeArguments() {
      return typeArguments.clone();
    }

    public Type getRawType() {
      return rawType;
    }

    public Type getOwnerType() {
      return ownerType;
    }

    @Override public boolean equals(Object other) {
      return other instanceof ParameterizedType
          && MoreTypes.equals(this, (ParameterizedType) other);
    }

    @Override public int hashCode() {
      return MoreTypes.hashCode(this);
    }

    @Override public String toString() {
      return MoreTypes.toString(this);
    }

    private static final long serialVersionUID = 0;
  }

  public static class GenericArrayTypeImpl implements GenericArrayType, Serializable {
    private final Type componentType;

    public GenericArrayTypeImpl(Type componentType) {
      this.componentType = canonicalize(componentType);
    }

    public Type getGenericComponentType() {
      return componentType;
    }

    @Override public boolean equals(Object o) {
      return o instanceof GenericArrayType
          && MoreTypes.equals(this, (GenericArrayType) o);
    }

    @Override public int hashCode() {
      return MoreTypes.hashCode(this);
    }

    @Override public String toString() {
      return MoreTypes.toString(this);
    }

    private static final long serialVersionUID = 0;
  }

  /**
   * The WildcardType interface supports multiple upper bounds and multiple
   * lower bounds. We only support what the Java 6 language needs - at most one
   * bound. If a lower bound is set, the upper bound must be Object.class.
   */
  public static class WildcardTypeImpl implements WildcardType, Serializable {
    private final Type upperBound;
    private final Type lowerBound;

    public WildcardTypeImpl(Type[] upperBounds, Type[] lowerBounds) {
      checkArgument(upperBounds.length <= 1, "Must have at most one lower bound.");
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

    public Type[] getUpperBounds() {
      return new Type[] { upperBound };
    }

    public Type[] getLowerBounds() {
      return lowerBound != null ? new Type[] { lowerBound } : EMPTY_TYPE_ARRAY;
    }

    @Override public boolean equals(Object other) {
      return other instanceof WildcardType
          && MoreTypes.equals(this, (WildcardType) other);
    }

    @Override public int hashCode() {
      return MoreTypes.hashCode(this);
    }

    @Override public String toString() {
      return MoreTypes.toString(this);
    }

    private static final long serialVersionUID = 0;
  }

  private static void checkNotPrimitive(Type type, String use) {
    checkArgument(!(type instanceof Class<?>) || !((Class) type).isPrimitive(),
        "Primitive types are not allowed in %s: %s", use, type);
  }

  /**
   * We cannot serialize the built-in Java member classes, which prevents us from using Members in
   * our exception types. We workaround this with this serializable implementation. It includes all
   * of the API methods, plus everything we use for line numbers and messaging.
   */
  public static class MemberImpl implements Member, Serializable {
    private final Class<?> declaringClass;
    private final String name;
    private final int modifiers;
    private final boolean synthetic;
    private final Class<? extends Member> memberType;
    private final String memberKey;

    private MemberImpl(Member member) {
      this.declaringClass = member.getDeclaringClass();
      this.name = member.getName();
      this.modifiers = member.getModifiers();
      this.synthetic = member.isSynthetic();
      this.memberType = memberType(member);
      this.memberKey = memberKey(member);
    }

    public Class getDeclaringClass() {
      return declaringClass;
    }

    public String getName() {
      return name;
    }

    public int getModifiers() {
      return modifiers;
    }

    public boolean isSynthetic() {
      return synthetic;
    }

    @Override public String toString() {
      return MoreTypes.toString(this);
    }
  }
}
