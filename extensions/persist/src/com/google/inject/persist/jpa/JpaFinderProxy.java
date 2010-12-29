/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.jpa;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.internal.util.MapMaker;
import com.google.inject.name.Named;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.finder.FirstResult;
import com.google.inject.persist.finder.MaxResults;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * TODO(dhanji): Make this work!!
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class JpaFinderProxy implements MethodInterceptor {
  private final Map<Method, FinderDescriptor> finderCache = new MapMaker().weakKeys().makeMap();
  private final Provider<EntityManager> emProvider;

  @Inject
  public JpaFinderProxy(Provider<EntityManager> emProvider) {
    this.emProvider = emProvider;
  }

  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    EntityManager em = emProvider.get();

    //obtain a cached finder descriptor (or create a new one)
    JpaFinderProxy.FinderDescriptor finderDescriptor = getFinderDescriptor(methodInvocation);

    Object result = null;

    //execute as query (named params or otherwise)
    Query jpaQuery = finderDescriptor.createQuery(em);
    if (finderDescriptor.isBindAsRawParameters) {
      bindQueryRawParameters(jpaQuery, finderDescriptor, methodInvocation.getArguments());
    } else {
      bindQueryNamedParameters(jpaQuery, finderDescriptor, methodInvocation.getArguments());
    }

    //depending upon return type, decorate or return the result as is
    if (JpaFinderProxy.ReturnType.PLAIN.equals(finderDescriptor.returnType)) {
      result = jpaQuery.getSingleResult();
    } else if (JpaFinderProxy.ReturnType.COLLECTION.equals(finderDescriptor.returnType)) {
      result = getAsCollection(finderDescriptor, jpaQuery.getResultList());
    } else if (JpaFinderProxy.ReturnType.ARRAY.equals(finderDescriptor.returnType)) {
      result = jpaQuery.getResultList().toArray();
    }

    return result;
  }

  private Object getAsCollection(JpaFinderProxy.FinderDescriptor finderDescriptor,
      List results) {
    Collection<?> collection;
    try {
      collection = (Collection) finderDescriptor.returnCollectionTypeConstructor.newInstance();
    } catch (InstantiationException e) {
      throw new RuntimeException(
          "Specified collection class of Finder's returnAs could not be instantated: "
              + finderDescriptor.returnCollectionType, e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Specified collection class of Finder's returnAs could not be instantated (do not have access privileges): "
              + finderDescriptor.returnCollectionType, e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(
          "Specified collection class of Finder's returnAs could not be instantated (it threw an exception): "
              + finderDescriptor.returnCollectionType, e);
    }

    collection.addAll(results);
    return collection;
  }

  private void bindQueryNamedParameters(Query jpaQuery,
      JpaFinderProxy.FinderDescriptor descriptor, Object[] arguments) {
    for (int i = 0; i < arguments.length; i++) {
      Object argument = arguments[i];
      Object annotation = descriptor.parameterAnnotations[i];

      if (null == annotation)
      //noinspection UnnecessaryContinue
      {
        continue;   //skip param as it's not bindable
      } else if (annotation instanceof Named) {
        Named named = (Named) annotation;
        jpaQuery.setParameter(named.value(), argument);
      } else if (annotation instanceof javax.inject.Named) {
        javax.inject.Named named = (javax.inject.Named) annotation;
        jpaQuery.setParameter(named.value(), argument);
      } else if (annotation instanceof FirstResult) {
        jpaQuery.setFirstResult((Integer) argument);
      } else if (annotation instanceof MaxResults) {
        jpaQuery.setMaxResults((Integer) argument);
      }
    }
  }

  private void bindQueryRawParameters(Query jpaQuery,
      JpaFinderProxy.FinderDescriptor descriptor, Object[] arguments) {
    for (int i = 0, index = 1; i < arguments.length; i++) {
      Object argument = arguments[i];
      Object annotation = descriptor.parameterAnnotations[i];

      if (null == annotation) {
        //bind it as a raw param (1-based index, yes I know its different from Hibernate, blargh)
        jpaQuery.setParameter(index, argument);
        index++;
      } else if (annotation instanceof FirstResult) {
        jpaQuery.setFirstResult((Integer) argument);
      } else if (annotation instanceof MaxResults) {
        jpaQuery.setMaxResults((Integer) argument);
      }
    }
  }

  private JpaFinderProxy.FinderDescriptor getFinderDescriptor(MethodInvocation invocation) {
    Method method = invocation.getMethod();
    JpaFinderProxy.FinderDescriptor finderDescriptor = finderCache.get(method);
    if (null != finderDescriptor) {
      return finderDescriptor;
    }

    //otherwise reflect and cache finder info...
    finderDescriptor = new JpaFinderProxy.FinderDescriptor();

    //determine return type
    finderDescriptor.returnClass = invocation.getMethod().getReturnType();
    finderDescriptor.returnType = determineReturnType(finderDescriptor.returnClass);

    //determine finder query characteristics
    Finder finder = invocation.getMethod().getAnnotation(Finder.class);
    String query = finder.query();
    if (!"".equals(query.trim())) {
      finderDescriptor.setQuery(query);
    } else {
      finderDescriptor.setNamedQuery(finder.namedQuery());
    }

    //determine parameter annotations
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    Object[] discoveredAnnotations = new Object[parameterAnnotations.length];
    for (int i = 0; i < parameterAnnotations.length; i++) {
      Annotation[] annotations = parameterAnnotations[i];
      //each annotation per param
      for (Annotation annotation : annotations) {
        //discover the named, first or max annotations then break out
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (Named.class.equals(annotationType) || javax.inject.Named.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          finderDescriptor.isBindAsRawParameters = false;
          break;
        } else if (FirstResult.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          break;
        } else if (MaxResults.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          break;
        }   //leave as null for no binding
      }
    }

    //set the discovered set to our finder cache object
    finderDescriptor.parameterAnnotations = discoveredAnnotations;

    //discover the returned collection implementation if this finder returns a collection
    if (JpaFinderProxy.ReturnType.COLLECTION.equals(finderDescriptor.returnType)
        && finderDescriptor.returnClass != Collection.class) {
      finderDescriptor.returnCollectionType = finder.returnAs();
      try {
        finderDescriptor.returnCollectionTypeConstructor = finderDescriptor.returnCollectionType
            .getConstructor();
        finderDescriptor.returnCollectionTypeConstructor.setAccessible(true);   //UGH!
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Finder's collection return type specified has no default constructor! returnAs: "
                + finderDescriptor.returnCollectionType, e);
      }
    }

    //cache it
    cacheFinderDescriptor(method, finderDescriptor);

    return finderDescriptor;
  }

  /**
   * writes to a chm (used to provide copy-on-write but this is bettah!)
   *
   * @param method The key
   * @param finderDescriptor The descriptor to cache
   */
  private void cacheFinderDescriptor(Method method, FinderDescriptor finderDescriptor) {
    //write to concurrent map
    finderCache.put(method, finderDescriptor);
  }

  private JpaFinderProxy.ReturnType determineReturnType(Class<?> returnClass) {
    if (Collection.class.isAssignableFrom(returnClass)) {
      return JpaFinderProxy.ReturnType.COLLECTION;
    } else if (returnClass.isArray()) {
      return JpaFinderProxy.ReturnType.ARRAY;
    }

    return JpaFinderProxy.ReturnType.PLAIN;
  }

  /**
   * A wrapper data class that caches information about a finder method.
   */
  private static class FinderDescriptor {
    private volatile boolean isKeyedQuery = false;
    volatile boolean isBindAsRawParameters = true;
        //should we treat the query as having ? instead of :named params
    volatile JpaFinderProxy.ReturnType returnType;
    volatile Class<?> returnClass;
    volatile Class<? extends Collection> returnCollectionType;
    volatile Constructor returnCollectionTypeConstructor;
    volatile Object[] parameterAnnotations;
        //contract is: null = no bind, @Named = param, @FirstResult/@MaxResults for paging

    private String query;
    private String name;

    void setQuery(String query) {
      this.query = query;
    }

    void setNamedQuery(String name) {
      this.name = name;
      isKeyedQuery = true;
    }

    public boolean isKeyedQuery() {
      return isKeyedQuery;
    }

    Query createQuery(EntityManager em) {
      return isKeyedQuery ? em.createNamedQuery(name) : em.createQuery(query);
    }
  }

  private static enum ReturnType {
    PLAIN, COLLECTION, ARRAY
  }
}
