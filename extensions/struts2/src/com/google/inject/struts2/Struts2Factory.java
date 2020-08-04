/*
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

package com.google.inject.struts2;

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Injector;
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
 * Cleanup up version from Bob's GuiceObjectFactory. Now works properly with GS2 and fixes several
 * bugs.
 *
 * @author dhanji@gmail.com
 * @author benmccann.com
 */
public class Struts2Factory extends ObjectFactory {

  private static final long serialVersionUID = 1L;
  private static final Logger logger = Logger.getLogger(Struts2Factory.class.getName());
  private static final String ERROR_NO_INJECTOR =
      "Cannot find a Guice injector.  Are you sure you registered a GuiceServletContextListener "
          + "that uses the Struts2GuicePluginModule in your application's web.xml?";

  private static @com.google.inject.Inject Injector injector;

  private final List<ProvidedInterceptor> interceptors = new ArrayList<>();
  private volatile Injector strutsInjector;

  @Override
  public boolean isNoArgConstructorRequired() {
    return false;
  }

  @Inject(value = "guice.module", required = false)
  void setModule(String moduleClassName) {
    throw new RuntimeException(
        "The struts2 plugin no longer supports"
            + " specifying a module via the 'guice.module' property in XML."
            + " Please install your module via a GuiceServletContextListener instead.");
  }

  Set<Class<?>> boundClasses = new HashSet<>();

  @Override
  public Class<?> getClassInstance(String name) throws ClassNotFoundException {
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

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"}) // Parent class uses raw type.
  public Object buildBean(Class clazz, Map<String, Object> extraContext) {
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

    // Something is wrong, since this should be there if GuiceServletContextListener
    // was present.
    if (injector == null) {
      logger.severe(ERROR_NO_INJECTOR);
      throw new RuntimeException(ERROR_NO_INJECTOR);
    }

    this.strutsInjector =
        injector.createChildInjector(
            new AbstractModule() {
              @Override
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

  @Override
  @SuppressWarnings("unchecked")
  public Interceptor buildInterceptor(
      InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams)
      throws ConfigurationException {
    // Ensure the interceptor class is present.
    Class<? extends Interceptor> interceptorClass;
    try {
      interceptorClass =
          (Class<? extends Interceptor>) getClassInstance(interceptorConfig.getClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    ProvidedInterceptor providedInterceptor =
        new ProvidedInterceptor(interceptorConfig, interceptorRefParams, interceptorClass);
    interceptors.add(providedInterceptor);
    if (strutsInjector != null) {
      synchronized (this) {
        if (strutsInjector != null) {
          providedInterceptor.inject();
        }
      }
    }
    return providedInterceptor;
  }

  private Interceptor superBuildInterceptor(
      InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams)
      throws ConfigurationException {
    return super.buildInterceptor(interceptorConfig, interceptorRefParams);
  }

  private class ProvidedInterceptor implements Interceptor {

    private static final long serialVersionUID = 1L;

    private final InterceptorConfig config;
    private final Map<String, String> params;
    private final Class<? extends Interceptor> interceptorClass;
    private Interceptor delegate;

    ProvidedInterceptor(
        InterceptorConfig config,
        Map<String, String> params,
        Class<? extends Interceptor> interceptorClass) {
      this.config = config;
      this.params = params;
      this.interceptorClass = interceptorClass;
    }

    void validate(Binder binder) {
      // TODO: Set source from Struts XML.
      if (hasScope(interceptorClass)) {
        binder.addError(
            "Scoping interceptors is not currently supported."
                + " Please remove the scope annotation from "
                + interceptorClass.getName()
                + ".");
      }

      // Make sure it implements Interceptor.
      if (!Interceptor.class.isAssignableFrom(interceptorClass)) {
        binder.addError(
            interceptorClass.getName() + " must implement " + Interceptor.class.getName() + ".");
      }
    }

    void inject() {
      delegate = superBuildInterceptor(config, params);
    }

    @Override
    public void destroy() {
      if (null != delegate) {
        delegate.destroy();
      }
    }

    @Override
    public void init() {
      throw new AssertionError();
    }

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
      return delegate.intercept(invocation);
    }
  }

  /** Returns true if the given class has a scope annotation. */
  private static boolean hasScope(Class<? extends Interceptor> interceptorClass) {
    for (Annotation annotation : interceptorClass.getAnnotations()) {
      if (Annotations.isScopeAnnotation(annotation.annotationType())) {
        return true;
      }
    }
    return false;
  }
}
