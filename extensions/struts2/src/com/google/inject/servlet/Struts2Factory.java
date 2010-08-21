/**
 * Copyright (C) 2009 Google Inc.
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

package com.google.inject.servlet;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.internal.Annotations;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ObjectFactory;
import com.opensymphony.xwork2.config.ConfigurationException;
import com.opensymphony.xwork2.config.entities.InterceptorConfig;
import com.opensymphony.xwork2.inject.Inject;
import com.opensymphony.xwork2.interceptor.Interceptor;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Cleanup up version from Bob's GuiceObjectFactory. Now works properly with
 * GS2 and fixes several bugs.
 *
 * @author dhanji@gmail.com
 */
public class Struts2Factory extends ObjectFactory {

  static final Logger logger =
      Logger.getLogger(Struts2Factory.class.getName());

  Module module;
  volatile Injector strutsInjector;
  boolean developmentMode = false;
  List<ProvidedInterceptor> interceptors
      = new ArrayList<ProvidedInterceptor>();
  private static final String ERROR_NO_INJECTOR =
      "Cannot find a Guice injector in the servlet context. Are you"
          + " sure you registered GuiceServletContextListener in your application's web.xml?";

  @Override
  public boolean isNoArgConstructorRequired() {
    return false;
  }

  @Inject(value = "guice.module", required = false)
  void setModule(String moduleClassName) {
    try {
      // Instantiate user's module.
      @SuppressWarnings({"unchecked"})
      Class<? extends Module> moduleClass =
          (Class<? extends Module>) Class.forName(moduleClassName);
      this.module = moduleClass.newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Inject(value = "struts.devMode", required = false)
  void setDevelopmentMode(String developmentMode) {
    this.developmentMode = "true".equals(developmentMode.trim());
  }

  Set<Class<?>> boundClasses = new HashSet<Class<?>>();

  public Class getClassInstance(String name) throws ClassNotFoundException {
    Class<?> clazz = super.getClassInstance(name);

    synchronized (this) {
      if (strutsInjector == null) {
        // We can only bind each class once.
        if (!boundClasses.contains(clazz)) {
          try {
            // Calling these methods now helps us detect ClassNotFoundErrors
            // early.
            clazz.getDeclaredFields();
            clazz.getDeclaredMethods();

            boundClasses.add(clazz);
          } catch (Throwable t) {
            // Struts should still work even though some classes aren't in the
            // classpath. It appears we always get the exception here when
            // this is the case.
            return clazz;
          }
        }
      }
    }

    return clazz;
  }

  @SuppressWarnings("unchecked")
  public Object buildBean(Class clazz, Map extraContext) {
    if (strutsInjector == null) {
      synchronized (this) {
        if (strutsInjector == null) {
          createInjector();
        }
      }
    }

    return strutsInjector.getInstance(clazz);
  }

  private void createInjector() {
    logger.info("Loading struts2 Guice support...");

    // Attach to parent Guice injector from GS2.
    Injector injector = (Injector) GuiceFilter.getServletContext()
        .getAttribute(GuiceServletContextListener.INJECTOR_NAME);

    // Something is wrong, since this should be there if GuiceServletContextListener
    // was present.
    if (null == injector) {
      logger.severe(ERROR_NO_INJECTOR);
      throw new RuntimeException(ERROR_NO_INJECTOR);
    }

    if (module != null) {
      throw new RuntimeException("The struts2 plugin no longer supports specifying a module"
          + "via the 'guice.module' property in XML."
          + " Please install your module via a GuiceServletContextListener instead.");
    }

    this.strutsInjector = injector.createChildInjector(new AbstractModule() {
      protected void configure() {

        // Tell the injector about all the action classes, etc., so it
        // can validate them at startup.
        for (Class<?> boundClass : boundClasses) {
          // TODO: Set source from Struts XML.
          bind(boundClass);
        }

        // Validate the interceptor class.
        for (ProvidedInterceptor interceptor : interceptors) {
          interceptor.validate(binder());
        }
      }
    });

    // Inject interceptors.
    for (ProvidedInterceptor interceptor : interceptors) {
      interceptor.inject();
    }

    logger.info("Injector created successfully.");
  }

  @SuppressWarnings("unchecked")
  public Interceptor buildInterceptor(InterceptorConfig interceptorConfig,
      Map interceptorRefParams) throws ConfigurationException {
    // Ensure the interceptor class is present.
    Class<? extends Interceptor> interceptorClass;
    try {
      interceptorClass = getClassInstance(interceptorConfig.getClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    ProvidedInterceptor providedInterceptor = new ProvidedInterceptor(
        interceptorConfig, interceptorRefParams, interceptorClass);
    interceptors.add(providedInterceptor);
    return providedInterceptor;
  }

  Interceptor superBuildInterceptor(InterceptorConfig interceptorConfig,
      Map interceptorRefParams) throws ConfigurationException {
    return super.buildInterceptor(interceptorConfig, interceptorRefParams);
  }

  class ProvidedInterceptor implements Interceptor {

    final InterceptorConfig config;
    final Map params;
    final Class<? extends Interceptor> interceptorClass;
    Interceptor delegate;

    ProvidedInterceptor(InterceptorConfig config, Map params,
        Class<? extends Interceptor> interceptorClass) {
      this.config = config;
      this.params = params;
      this.interceptorClass = interceptorClass;
    }

    void validate(Binder binder) {
      // TODO: Set source from Struts XML.
      if (hasScope(interceptorClass)) {
        binder.addError("Scoping interceptors is not currently supported."
            + " Please remove the scope annotation from "
            + interceptorClass.getName() + ".");
      }

      // Make sure it implements Interceptor.
      if (!Interceptor.class.isAssignableFrom(interceptorClass)) {
        binder.addError(interceptorClass.getName() + " must implement "
          + Interceptor.class.getName() + ".");
      }
    }

    void inject() {
      delegate = superBuildInterceptor(config, params);
    }

    public void destroy() {
      if (null != delegate) {
        delegate.destroy();
      }
    }

    public void init() {
      throw new AssertionError();
    }

    public String intercept(ActionInvocation invocation) throws Exception {
      return delegate.intercept(invocation);
    }
  }

  /**
   * Returns true if the given class has a scope annotation.
   */
  private static boolean hasScope(Class<? extends Interceptor> interceptorClass) {
    for (Annotation annotation : interceptorClass.getAnnotations()) {
      if (Annotations.isScopeAnnotation(annotation.annotationType())) {
        return true;
      }
    }
    return false;
  }
}
