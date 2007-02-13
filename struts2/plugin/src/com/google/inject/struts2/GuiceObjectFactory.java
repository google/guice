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

package com.google.inject.struts2;

import com.google.inject.Container;
import com.google.inject.ContainerBuilder;
import com.google.inject.Module;
import com.google.inject.servlet.ServletModule;

import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ObjectFactory;
import com.opensymphony.xwork2.config.ConfigurationException;
import com.opensymphony.xwork2.config.entities.InterceptorConfig;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.interceptor.Interceptor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class GuiceObjectFactory extends ObjectFactory {

  static final Logger logger =
      Logger.getLogger(GuiceObjectFactory.class.getName());

  ContainerBuilder builder = new ContainerBuilder();
  Container container;
  boolean developmentMode = false;

  public GuiceObjectFactory() {
    // Configure default servlet bindings.
    builder.install(new ServletModule());
  }

  public boolean isNoArgConstructorRequired() {
    return false;
  }

  @Inject(value = "guice.module", required = false)
  void setModule(String moduleClassName) {
    try {
      // Instantiate user's module and install it.
      @SuppressWarnings({"unchecked"})
      Class<? extends Module> moduleClass =
          (Class<? extends Module>) Class.forName(moduleClassName);

      Module module = moduleClass.newInstance();

      module.configure(builder);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Inject(value = "struts.devMode", required = false)
  void setDevelopmentMode(String developmentMode) {
    this.developmentMode = developmentMode.trim().equals("true");
  }

  Set<Class<?>> boundClasses = new HashSet<Class<?>>();

  public Class getClassInstance(String name) throws ClassNotFoundException {
    Class<?> clazz = super.getClassInstance(name);

    synchronized (this) {
      if (container == null) {
        // We can only bind each class once.
        if (boundClasses.add(clazz)) {
          try {
            clazz.getDeclaredFields();
            clazz.getDeclaredMethods();
          } catch (Throwable t) {
            // Struts should still work even though some classes aren't in the
            // classpath. It appears we always get the exception here when
            // this is the case.
            return clazz;
          }

          builder.bind(clazz);
        }
      }
    }

    return clazz;
  }

  public Object buildBean(Class clazz, Map extraContext) {
    synchronized (this) {
      if (container == null) {
        try {
          logger.info("Creating container...");
          container = builder.create(!developmentMode);
        } catch (Throwable t) {
          t.printStackTrace();
          System.exit(1);
        }
        logger.info("Container created successfully.");
      }
    }

    return container.getInstance(clazz);
  }

  public Interceptor buildInterceptor(InterceptorConfig interceptorConfig,
      Map interceptorRefParams) throws ConfigurationException {
    try {
      getClassInstance(interceptorConfig.getClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    // Defer the creation of interceptors so that we don't have to create the
    // container until we've bound all the actions. This enables us to
    // validate all the dependencies at once.
    return new LazyLoadedInterceptor(interceptorConfig, interceptorRefParams);
  }

  Interceptor superBuildInterceptor(InterceptorConfig interceptorConfig,
      Map interceptorRefParams) throws ConfigurationException {
    return super.buildInterceptor(interceptorConfig, interceptorRefParams);
  }

  class LazyLoadedInterceptor implements Interceptor {

    final InterceptorConfig config;
    final Map params;

    LazyLoadedInterceptor(InterceptorConfig config, Map params) {
      this.config = config;
      this.params = params;
    }

    Interceptor delegate = null;

    synchronized Interceptor getDelegate() {
      if (delegate == null) {
        delegate = superBuildInterceptor(config, params);
        delegate.init();
      }
      return delegate;
    }

    public void destroy() {
      getDelegate().destroy();
    }

    public void init() {
      throw new AssertionError();
    }

    public String intercept(ActionInvocation invocation) throws Exception {
      return getDelegate().intercept(invocation);
    }
  }
}
