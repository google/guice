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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.jpa.JpaDynamicFinderFactory.FinderCreationResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * JPA provider for guice persist.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public final class JpaPersistModule extends PersistModule {
  private final String jpaUnit;

  public JpaPersistModule(String jpaUnit) {
    Preconditions.checkArgument(
        null != jpaUnit && !jpaUnit.trim().isEmpty(), "JPA unit name must be a non-empty string.");
    this.jpaUnit = jpaUnit;
  }

  private Map<?, ?> properties;

  @Override
  protected void configurePersistence() {
    bindConstant().annotatedWith(Jpa.class).to(jpaUnit);
    bind(JpaPersistService.class).in(Singleton.class);

    bind(PersistService.class).to(JpaPersistService.class);
    bind(UnitOfWork.class).to(ReentrantUnitOfWork.class);
    bind(LocalTransaction.class);
    bind(EntityManager.class).toProvider(ReentrantUnitOfWork.class);
    bind(EntityManagerFactory.class).toProvider(JpaPersistService.class);
    bindFinders();
  }

  @Override
  protected MethodInterceptor getTransactionInterceptor() {
    return new JpaTransactionInterceptor(getProvider(LocalTransaction.class));
  }

  @Provides
  @Jpa
  Map<?, ?> provideProperties() {
    return properties;
  }

  /**
   * Configures the JPA persistence provider with a set of properties.
   *
   * @param properties A set of name value pairs that configure a JPA persistence provider as per
   *     the specification.
   * @since 4.0 (since 3.0 with a parameter type of {@code java.util.Properties})
   */
  public JpaPersistModule properties(Map<?, ?> properties) {
    this.properties = properties;
    return this;
  }

  private final List<Class<?>> dynamicFinders = Lists.newArrayList();

  /**
   * Adds an interface to this module to use as a dynamic finder.
   *
   * @param iface Any interface type whose methods are all dynamic finders.
   */
  public <T> JpaPersistModule addFinder(Class<T> iface) {
    dynamicFinders.add(iface);
    return this;
  }

  private void bindFinders() {
      JpaDynamicFinderFactory finderFactory = new JpaDynamicFinderFactory();
      for (Class<?> finder : dynamicFinders) {
          FinderCreationResult result = finderFactory.createFinder(finder);
          if (result.hasErrors()) {
              result.getErrors().forEach(this::addError);
          } else {
              bindFinder(finder, result.getHandler());
          }
      }
  }

    @SuppressWarnings("unchecked")
    private <T> void bindFinder(Class<T> finder, InvocationHandler handler) {
        requestInjection(handler);
        bind(finder).toInstance((T) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(),
            new Class<?>[] {finder},
            handler));
    }
}
