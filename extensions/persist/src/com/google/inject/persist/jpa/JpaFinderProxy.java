/*
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

import com.google.common.collect.MapMaker;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.Query;

/**
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Singleton
class JpaFinderProxy implements InvocationHandler {
  private final Map<Method, FinderDescriptor> finderCache = new MapMaker().weakKeys().makeMap();
  private final Provider<EntityManager> emProvider;
  private final JpaQueryResultMapper resultMapper;
  private final JpaFinderParameterBinder parameterBinder;
  private final JpaFinderDescriptorFactory descriptorFactory;

  @Inject
  public JpaFinderProxy(Provider<EntityManager> emProvider,
                        JpaQueryResultMapper resultMapper,
                        JpaFinderParameterBinder parameterBinder,
                        JpaFinderDescriptorFactory descriptorFactory) {
    this.emProvider = emProvider;
    this.resultMapper = resultMapper;
    this.parameterBinder = parameterBinder;
    this.descriptorFactory = descriptorFactory;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object invoke(Object proxy, Method method, Object[] args) {
    EntityManager em = emProvider.get();

    //obtain a cached finder descriptor (or create a new one)
    JpaFinderProxy.FinderDescriptor finderDescriptor = getFinderDescriptor(method);

    //execute as query (named params or otherwise)
    Query jpaQuery = finderDescriptor.createQuery(em);
    parameterBinder.bindParameters(args, finderDescriptor, jpaQuery);

    return resultMapper.mapResult(finderDescriptor, jpaQuery);
  }

  @SuppressWarnings("unchecked")
  private JpaFinderProxy.FinderDescriptor getFinderDescriptor(Method method) {
    JpaFinderProxy.FinderDescriptor finderDescriptor = finderCache.get(method);
    if (null != finderDescriptor) {
      return finderDescriptor;
    }

    //otherwise reflect and cache finder info...
    finderDescriptor = descriptorFactory.createDescriptor(method);

    //cache it
    cacheFinderDescriptor(method, finderDescriptor);

    return finderDescriptor;
  }

  /**
   * writes to a chm (used to provide copy-on-write but this is bettah!)
   *
   * @param method           The key
   * @param finderDescriptor The descriptor to cache
   */
  private void cacheFinderDescriptor(Method method, FinderDescriptor finderDescriptor) {
    //write to concurrent map
    finderCache.put(method, finderDescriptor);
  }

  /**
   * A wrapper data class that caches information about a finder method.
   */
  static class FinderDescriptor {
    private volatile boolean isKeyedQuery = false;
    volatile boolean isBindAsRawParameters = true;
    //should we treat the query as having ? instead of :named params
    volatile JpaFinderProxy.ReturnType returnType;
    volatile Class<?> returnClass;

    @SuppressWarnings("rawtypes") // Unavoidable because class literal uses raw type
    volatile Class<? extends Collection> returnCollectionType;

    volatile Constructor<?> returnCollectionTypeConstructor;
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

  enum ReturnType {
    PLAIN,
    COLLECTION,
    ARRAY
  }
}
