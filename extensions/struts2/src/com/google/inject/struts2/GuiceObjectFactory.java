/*
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

import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.internal.Annotations;
import com.google.inject.servlet.ServletModule;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ObjectFactory;
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

/** @deprecated Use {@link com.google.inject.struts2.Struts2Factory} instead. */
@Deprecated
public class GuiceObjectFactory extends ObjectFactory {

  static final Logger logger = Logger.getLogger(GuiceObjectFactory.class.getName());

  Module module;
  volatile Injector injector;
  boolean developmentMode = false;
  List<ProvidedInterceptor> interceptors = new ArrayList<>();

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
      this.module = moduleClass.getConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Inject(value = "struts.devMode", required = false)
  void setDevelopmentMode(String developmentMode) {
    this.developmentMode = developmentMode.trim().equals("true");
  }

  Set<Class<?>> boundClasses = new HashSet<>();

  @SuppressWarnings("rawtypes") // Parent class uses raw type.
  @Override
  public Class getClassInstance(String name) throws ClassNotFoundException {
    Class<?> clazz = super.getClassInstance(name);

    synchronized (this) {
      if (injector == null) {
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
    if (injector == null) {
      synchronized (this) {
        if (injector == null) {
          createInjector();
        }
      }
    }

    return injector.getInstance(clazz);
  }

  private void createInjector() {
    try {
      logger.info("Creating injector...");
      this.injector =
          Guice.createInjector(
              new AbstractModule() {
                @Override
                protected void configure() {
                  // Install default servlet bindings.
                  install(new ServletModule());

                  // Install user's module.
                  if (module != null) {
                    logger.info("Installing " + module + "...");
                    install(module);
                  } else {
                    logger.info(
                        "No module found. Set 'guice.module' to a Module "
                            + "class name if you'd like to use one.");
                  }

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

    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
    logger.info("Injector created successfully.");
  }

  @Override
  @SuppressWarnings("unchecked")
  public Interceptor buildInterceptor(
      InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams) {
    // Ensure the interceptor class is present.
    Class<? extends Interceptor> interceptorClass;
    try {
      interceptorClass = getClassInstance(interceptorConfig.getClassName());
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }

    ProvidedInterceptor providedInterceptor =
        new ProvidedInterceptor(interceptorConfig, interceptorRefParams, interceptorClass);
    interceptors.add(providedInterceptor);
    return providedInterceptor;
  }

  Interceptor superBuildInterceptor(
      InterceptorConfig interceptorConfig, Map<String, String> interceptorRefParams) {
    return super.buildInterceptor(interceptorConfig, interceptorRefParams);
  }

  class ProvidedInterceptor implements Interceptor {

    final InterceptorConfig config;
    final Map<String, String> params;
    final Class<? extends Interceptor> interceptorClass;
    Interceptor delegate;

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
