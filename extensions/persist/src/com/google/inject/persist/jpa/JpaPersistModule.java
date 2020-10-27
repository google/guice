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
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.persist.PersistModule;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.google.inject.persist.finder.DynamicFinder;
import com.google.inject.persist.finder.Finder;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * JPA provider for guice persist.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public final class JpaPersistModule extends PersistModule {
  private final String jpaUnit;

  public JpaPersistModule(String jpaUnit) {
    Preconditions.checkArgument(
        null != jpaUnit && jpaUnit.length() > 0, "JPA unit name must be a non-empty string.");
    this.jpaUnit = jpaUnit;
  }

  private Map<?, ?> properties;
  private MethodInterceptor transactionInterceptor;

  @Override
  protected void configurePersistence() {
    bindConstant().annotatedWith(Jpa.class).to(jpaUnit);

    bind(JpaPersistService.class).in(Singleton.class);

    bind(PersistService.class).to(JpaPersistService.class);
    bind(UnitOfWork.class).to(JpaPersistService.class);
    bind(EntityManager.class).toProvider(JpaPersistService.class);
    bind(EntityManagerFactory.class)
        .toProvider(JpaPersistService.EntityManagerFactoryProvider.class);

    transactionInterceptor = new JpaLocalTxnInterceptor();
    requestInjection(transactionInterceptor);

    // Bind dynamic finders.
    for (Class<?> finder : dynamicFinders) {
      bindFinder(finder);
    }
  }

  @Override
  protected MethodInterceptor getTransactionInterceptor() {
    return transactionInterceptor;
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

  private <T> void bindFinder(Class<T> iface) {
    if (!isDynamicFinderValid(iface)) {
      return;
    }

    InvocationHandler finderInvoker =
        new InvocationHandler() {
          @Inject JpaFinderProxy finderProxy;

          @Override
          public Object invoke(final Object thisObject, final Method method, final Object[] args)
              throws Throwable {

            // Don't intercept non-finder methods like equals and hashcode.
            if (!method.isAnnotationPresent(Finder.class)) {
              // NOTE(user): This is not ideal, we are using the invocation handler's equals
              // and hashcode as a proxy (!) for the proxy's equals and hashcode.
              return method.invoke(this, args);
            }

            return finderProxy.invoke(
                new MethodInvocation() {
                  @Override
                  public Method getMethod() {
                    return method;
                  }

                  @Override
                  public Object[] getArguments() {
                    return null == args ? new Object[0] : args;
                  }

                  @Override
                  public Object proceed() throws Throwable {
                    return method.invoke(thisObject, args);
                  }

                  @Override
                  public Object getThis() {
                    throw new UnsupportedOperationException(
                        "Bottomless proxies don't expose a this.");
                  }

                  @Override
                  public AccessibleObject getStaticPart() {
                    throw new UnsupportedOperationException();
                  }
                });
          }
        };
    requestInjection(finderInvoker);

    @SuppressWarnings("unchecked") // Proxy must produce instance of type given.
    T proxy =
        (T)
            Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {iface},
                finderInvoker);

    bind(iface).toInstance(proxy);
  }

  private boolean isDynamicFinderValid(Class<?> iface) {
    boolean valid = true;
    if (!iface.isInterface()) {
      addError(iface + " is not an interface. Dynamic Finders must be interfaces.");
      valid = false;
    }

    for (Method method : iface.getMethods()) {
      DynamicFinder finder = DynamicFinder.from(method);
      if (null == finder) {
        addError(
            "Dynamic Finder methods must be annotated with @Finder, but "
                + iface
                + "."
                + method.getName()
                + " was not");
        valid = false;
      }
    }
    return valid;
  }
}
