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

package com.google.inject;

import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.Dependency;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal context. Used to coordinate injections and support circular
 * dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InternalContext {

  private final InjectorImpl injector;
  private Map<Object, ConstructionContext<?>> constructionContexts;
  private Dependency dependency;

  public InternalContext(InjectorImpl injector) {
    this.injector = injector;
  }

  public InjectorImpl getInjector() {
    return injector;
  }

  @SuppressWarnings("unchecked")
  public <T> ConstructionContext<T> getConstructionContext(Object key) {
    if (constructionContexts == null) {
      constructionContexts = new HashMap<Object, ConstructionContext<?>>();
      ConstructionContext<T> constructionContext = new ConstructionContext<T>();
      constructionContexts.put(key, constructionContext);
      return constructionContext;
    }
    else {
      ConstructionContext<T> constructionContext
          = (ConstructionContext<T>) constructionContexts.get(key);
      if (constructionContext == null) {
        constructionContext = new ConstructionContext<T>();
        constructionContexts.put(key, constructionContext);
      }
      return constructionContext;
    }
  }

  public Dependency getDependency() {
    return dependency;
  }

  public void setDependency(Dependency dependency) {
    this.dependency = dependency;
  }

  /**
   * Ensures that an object requiring injection at Injector-creation time has
   * been injected before its use.
   */
  public void ensureMemberInjected(Errors errors, Object toInject) throws ErrorsException {
    injector.memberInjector.ensureInjected(toInject, errors);
  }
}
