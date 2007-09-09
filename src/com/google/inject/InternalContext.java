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

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Internal context. Used to coordinate injections and support circular
 * dependencies.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class InternalContext {

  final InjectorImpl injector;
  Map<Object, ConstructionContext<?>> constructionContexts;
  final List<ExternalContext<?>> externalContextStack =
      new ArrayList<ExternalContext<?>>(5);

  InternalContext(InjectorImpl injector) {
    this.injector = injector;
  }

  InjectorImpl getInjectorImpl() {
    return injector;
  }

  @SuppressWarnings("unchecked")
  <T> ConstructionContext<T> getConstructionContext(Object key) {
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

  @SuppressWarnings("unchecked")
  <T> ExternalContext<T> getExternalContext() {
    if (externalContextStack.isEmpty()) {
      throw new IllegalStateException("No external context on stack");
    }
    return (ExternalContext<T>) externalContextStack.get(
        externalContextStack.size() - 1);
  }

  public List<ExternalContext<?>> getExternalContextStack() {
    return Collections.unmodifiableList(
        new ArrayList<ExternalContext<?>>(externalContextStack));
  }

  Class<?> getExpectedType() {
    return getExternalContext().getKey().getRawType();
  }

  /**
   * Push a new external context onto the stack. Each call to {@code #push()}
   * requires a matching call to {@code #pop()} so that the contexts are
   * balanced.
   */
  void pushExternalContext(ExternalContext<?> externalContext) {
    externalContextStack.add(externalContext);
  }

  /**
   * Pop the external context off the stack.
   */
  void popExternalContext() {
    externalContextStack.remove(externalContextStack.size() - 1);
  }

  <T> T checkForNull(T value, Object source) {
    if (value != null
        || getExternalContext().getNullability() == Nullability.NULLABLE
        || allowNullsBadBadBad()) {
      return value;
    }

    String message = getExternalContext().getMember() != null
        ? String.format(ErrorMessages.CANNOT_INJECT_NULL_INTO_MEMBER, source,
            getExternalContext().getMember())
        : String.format(ErrorMessages.CANNOT_INJECT_NULL, source);

    throw new ProvisionException(getExternalContextStack(),
        new NullPointerException(message),
        String.format(ErrorMessages.CANNOT_INJECT_NULL, source));
  }

  // TODO(kevinb): gee, ya think we might want to remove this?
  private static boolean allowNullsBadBadBad() {
    return "I'm a bad hack".equals(
          System.getProperty("guice.allow.nulls.bad.bad.bad"));
  }
}
