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

import com.google.inject.Inject;
import com.google.inject.persist.finder.DynamicFinder;
import com.google.inject.persist.finder.Finder;
import com.google.inject.spi.Message;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;

class JpaDynamicFinderFactory {

  public FinderCreationResult createFinder(Class<?> iface) {
    Set<Message> messages = validateDynamicFinder(iface);
    if (!messages.isEmpty()) {
      return new FinderCreationResult(null, messages);
    }

    return new FinderCreationResult(
        new InvocationHandler() {
          @Inject
          JpaFinderProxy finderProxy;

          @Override
          public Object invoke(final Object thisObject, final Method method, final Object[] args)
              throws Throwable {

            // Don't intercept non-finder methods like equals and hashcode.
            if (!method.isAnnotationPresent(Finder.class)) {
              // NOTE(user): This is not ideal, we are using the invocation handler's equals
              // and hashcode as a proxy (!) for the proxy's equals and hashcode.
              return method.invoke(this, args);
            }

            return finderProxy.invoke(thisObject, method, args != null ? args : new Object[0]);
          }
        }, Collections.emptySet());
  }

  private Set<Message> validateDynamicFinder(Class<?> iface) {
    Set<Message> messages = new HashSet<>();
    if (!iface.isInterface()) {
      messages.add(
          new Message(iface + " is not an interface. Dynamic Finders must be interfaces."));
    }

    for (Method method : iface.getMethods()) {
      DynamicFinder finder = DynamicFinder.from(method);
      if (null == finder) {
        messages.add(new Message(
            "Dynamic Finder methods must be annotated with @Finder, but "
                + iface
                + "."
                + method.getName()
                + " was not"));
      }
    }
    return messages;
  }

  static class FinderCreationResult {
    private final InvocationHandler handler;
    private final Set<Message> errors;

    public FinderCreationResult(InvocationHandler handler, @Nonnull Set<Message> errors) {
      this.handler = handler;
      this.errors = errors;
    }

    public InvocationHandler getHandler() {
      return handler;
    }

    public Set<Message> getErrors() {
      return errors;
    }

    public boolean hasErrors() {
      return !errors.isEmpty();
    }
  }
}
