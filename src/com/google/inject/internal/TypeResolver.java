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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.util.Types;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.List;
import java.util.Map;

/**
 * Applies known information to generic types to discover more precise generic
 * types. For example, the Java reflection API exposes the return type of
 * {@code Collection.iterator()} as {@code Iterator<E>}. This class leverages
 * more precise type (such as {@code Set<String>}) to expose a more precise
 * return type: {@code Iterator<String>}. To accomplish this, type resolver
 * fills the generic parameter {@code E} with the known type {@code String}.
 * Parameters whose types are unavailable will not be resolved.
 *
 * <p>This class is not threadsafe.
 * 
 * @author jessewilson@google.com (Jesse Wilson)
 */
public final class TypeResolver {

  /** the type being resolved, such as {@code List<String>} */
  public final Type type;

  /** maps from type variables to the types they resolve to */
  private final Map<FqTypeVar, Type> index = Maps.newHashMap();

  /** types to resolved types */
  private final Map<Type, Type> cache = Maps.newHashMap();

  /** raw supertypes to the corresponding resolved generic types */
  private final Map<Class<?>, Type> implementedTypes = Maps.newHashMap();

  /**
   * Creates a new type resolver that can resolve the members of {@code type}.
   */
  public TypeResolver(Type type) {
    this.type = type;
    resolveRecursive(type);
  }

  /**
   * Resolves and stores all generic information for {@code type} and all of
   * its implemented interfaces and supertypes.
   */
  private void resolveRecursive(Type type) {
    Class<?> rawType = MoreTypes.getRawType(type);
    implementedTypes.put(rawType, resolve(type));

    Type supertype = rawType.getGenericSuperclass();
    if (supertype != null) {
      resolveRecursive(supertype);
    }
    for (Type genericInterface : rawType.getGenericInterfaces()) {
      resolveRecursive(genericInterface);
    }
  }

  /**
   * Projects the known type information from this resolver on {@code type} and
   * returns the result.
   */
  private Type resolve(Type type) {
    checkNotNull(type, "type");
    Type alreadyResolved = cache.get(type);

    if (alreadyResolved != null) {
      return alreadyResolved;

    } else if (type instanceof ParameterizedType) {
      ParameterizedType parameterizedType = (ParameterizedType) type;
      Class<?> rawType = MoreTypes.getRawType(parameterizedType);
      TypeVariable<? extends Class<?>>[] typeVariables = rawType.getTypeParameters();
      Type[] arguments = parameterizedType.getActualTypeArguments();
      Type[] resolvedArguments = new Type[typeVariables.length];
      for (int v = 0; v < typeVariables.length; v++) {
        FqTypeVar typeVar = new FqTypeVar(rawType, typeVariables[v].getName());
        resolvedArguments[v] = resolve(arguments[v]);
        index.put(typeVar, resolvedArguments[v]);
      }

      Type resolvedRawType = resolve(rawType);
      Type ownerType = parameterizedType.getOwnerType();
      Type resolvedOwnerType = ownerType != null ? resolve(ownerType) : null;
      Type resolved = Types.newParameterizedTypeWithOwner(resolvedOwnerType,
          resolvedRawType, resolvedArguments);
      cache.put(type, resolved);
      return resolved;

    } else if (type instanceof TypeVariable) {
      TypeVariable typeVariable = (TypeVariable) type;
      GenericDeclaration genericDeclaration = typeVariable.getGenericDeclaration();
      Type result = typeVariable;
      if (genericDeclaration instanceof Class) {
        FqTypeVar fqTypeVar = new FqTypeVar((Class<?>) genericDeclaration, typeVariable.getName());
        Type resolved = index.get(fqTypeVar);
        result = resolved != null ? resolved : result;
      }
      cache.put(type, result);
      return result;

    } else if (type instanceof Class) {
      cache.put(type, type);
      return type;

    } else if (type instanceof GenericArrayType) {
      GenericArrayType arrayType = (GenericArrayType) type;
      Type componentTypeResolved = resolve(arrayType.getGenericComponentType());
      GenericArrayType resolved = Types.arrayOf(componentTypeResolved);
      cache.put(type, resolved);
      return resolved;

    } else {
      return type;
    }
  }

  /**
   * Returns an immutable list of the resolved types.
   */
  private List<Type> resolveAll(Type[] types) {
    Type[] result = new Type[types.length];
    for (int t = 0; t < types.length; t++) {
      result[t] = resolve(types[t]);
    }
    return ImmutableList.of(result);
  }

  /**
   * Returns the type used to resolve other types. Ideally this is a fully
   * specified type such as {@code List<String>}.
   */
  public Type getType() {
    return type;
  }

  /**
   * Returns the generic equivalent of {@code supertype}. For example, if this
   * is a resolver of {@code ArrayList<String>}, this method returns {@code
   * Iterable<String>} given the input {@code Iterable.class}.
   *
   * @param supertype a superclass of, or interface implemented by, this
   *      resolver's type.
   */
  public Type getSupertype(Class<?> supertype) {
    Type type = implementedTypes.get(supertype);
    checkArgument(type != null,
        "%s is not a supertype of %s", supertype, this.type);
    return type;
  }

  /**
   * Returns the resolved generic type of {@code field}.
   *
   * @param field a field defined by this resolver's type or any of its
   *      superclasses.
   */
  public Type getFieldType(Field field) {
    checkArgument(implementedTypes.containsKey(field.getDeclaringClass()),
        "%s is not defined by a supertype of %s", field, type);
    return resolve(field.getGenericType());
  }

  /**
   * Returns a list of the resolved generic parameter types of {@code
   * constructor}.
   *
   * @param constructor a constructor defined by this resolver's type or any of
   *      its superclasses.
   */
  public List<Type> getParameterTypes(Constructor constructor) {
    checkArgument(implementedTypes.containsKey(constructor.getDeclaringClass()),
        "%s does not construct a supertype of %s", constructor, type);
    return resolveAll(constructor.getGenericParameterTypes());
  }

  /**
   * Returns a list of the resolved generic exception types of {@code
   * constructor}.
   *
   * @param constructor a constructor defined by this resolver's type or any of
   *      its superclasses.
   */
  public List<Type> getExceptionTypes(Constructor constructor) {
    checkArgument(implementedTypes.containsKey(constructor.getDeclaringClass()),
        "%s does not construct a supertype of %s", constructor, type);
    return resolveAll(constructor.getGenericExceptionTypes());
  }

  /**
   * Returns a list of the resolved generic parameter types of {@code method}.
   *
   * @param method a method defined by this resolver's type, its superclasses
   *      or implemented interfaces.
   */
  public List<Type> getParameterTypes(Method method) {
    checkArgument(implementedTypes.containsKey(method.getDeclaringClass()),
        "%s is not defined by a supertype of %s", method, type);
    return resolveAll(method.getGenericParameterTypes());
  }

  /**
   * Returns a list of the resolved generic exception types of {@code method}.
   *
   * @param method a method defined by this resolver's type, its superclasses
   *      or implemented interfaces.
   */
  public List<Type> getExceptionTypes(Method method) {
    checkArgument(implementedTypes.containsKey(method.getDeclaringClass()),
        "%s is not defined by a supertype of %s", method, type);
    return resolveAll(method.getGenericExceptionTypes());
  }

  /**
   * Returns the resolved generic return type of {@code method}.
   *
   * @param method a method defined by this resolver's type, its superclasses
   *      or implemented interfaces.
   */
  public Type getReturnType(Method method) {
    checkArgument(implementedTypes.containsKey(method.getDeclaringClass()),
        "%s is not defined by a supertype of %s", method, type);
    return resolve(method.getGenericReturnType());
  }

  @Override public boolean equals(Object obj) {
    return obj instanceof TypeResolver
        && getType().equals(((TypeResolver) obj).getType());
  }

  @Override public int hashCode() {
    return 79 * type.hashCode();
  }

  @Override public String toString() {
    return "Resolver:" + type;
  }

  /**
   * A fully-qualified type variable, such as the "E" in java.util.List.
   */
  private static class FqTypeVar {
    private final Class<?> rawType;
    private final String name;

    private FqTypeVar(Class<?> rawType, String name) {
      this.rawType = rawType;
      this.name = name;
    }

    @Override public boolean equals(Object o) {
      return o instanceof FqTypeVar
          && ((FqTypeVar) o).rawType == rawType
          && ((FqTypeVar) o).name.equals(name);
    }

    @Override public int hashCode() {
      return rawType.hashCode() ^ name.hashCode();
    }

    @Override public String toString() {
      return rawType + ":" + name;
    }
  }
}