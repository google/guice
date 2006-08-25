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

import java.util.concurrent.Callable;

/**
 * Scope of an injected objects.
 *
 * @author crazybob
 */
public enum Scope {

  /**
   * One instance per injection.
   */
  DEFAULT {
    <T> InternalFactory<? extends T> scopeFactory(Class<T> type, String name,
        InternalFactory<? extends T> factory) {
      return factory;
    }
  },

  /**
   * One instance per container.
   */
  SINGLETON {
    <T> InternalFactory<? extends T> scopeFactory(Class<T> type, String name,
        final InternalFactory<? extends T> factory) {
      return new InternalFactory<T>() {
        T instance;
        public T create(InternalContext context) {
          synchronized (context.getContainer()) {
            if (instance == null) {
              instance = factory.create(context);
            }
            return instance;
          }
        }

        public String toString() {
          return factory.toString();
        }
      };
    }
  },

  /**
   * One instance per thread.
   *
   * <p><b>Note:</b> if a thread local object strongly references its {@link
   * Container}, neither the {@code Container} nor the object will be
   * eligible for garbage collection, i.e. memory leak.
   */
  THREAD {
    <T> InternalFactory<? extends T> scopeFactory(Class<T> type, String name,
        final InternalFactory<? extends T> factory) {
      return new InternalFactory<T>() {
        final ThreadLocal<T> threadLocal = new ThreadLocal<T>();
        public T create(final InternalContext context) {
          T t = threadLocal.get();
          if (t == null) {
            t = factory.create(context);
            threadLocal.set(t);
          }
          return t;
        }

        public String toString() {
          return factory.toString();
        }
      };
    }
  },

  /**
   * One instance per request.
   */
  REQUEST {
    <T> InternalFactory<? extends T> scopeFactory(final Class<T> type,
        final String name, final InternalFactory<? extends T> factory) {
      return new InternalFactory<T>() {
        public T create(InternalContext context) {
          Strategy strategy = context.getScopeStrategy();
          try {
            return strategy.findInRequest(
                type, name, toCallable(context, factory));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

        public String toString() {
          return factory.toString();
        }
      };
    }
  },

  /**
   * One instance per session.
   */
  SESSION {
    <T> InternalFactory<? extends T> scopeFactory(final Class<T> type,
        final String name, final InternalFactory<? extends T> factory) {
      return new InternalFactory<T>() {
        public T create(InternalContext context) {
          Strategy strategy = context.getScopeStrategy();
          try {
            return strategy.findInSession(
                type, name, toCallable(context, factory));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

        public String toString() {
          return factory.toString();
        }
      };
    }
  },

  /**
   * One instance per wizard.
   */
  WIZARD {
    <T> InternalFactory<? extends T> scopeFactory(final Class<T> type,
        final String name, final InternalFactory<? extends T> factory) {
      return new InternalFactory<T>() {
        public T create(InternalContext context) {
          Strategy strategy = context.getScopeStrategy();
          try {
            return strategy.findInWizard(
                type, name, toCallable(context, factory));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }

        public String toString() {
          return factory.toString();
        }
      };
    }
  };

  <T> Callable<? extends T> toCallable(final InternalContext context,
      final InternalFactory<? extends T> factory) {
    return new Callable<T>() {
      public T call() throws Exception {
        return factory.create(context);
      }
    };
  }

  /**
   * Wraps factory with scoping logic.
   */
  abstract <T> InternalFactory<? extends T> scopeFactory(
      Class<T> type, String name, InternalFactory<? extends T> factory);

  /**
   * Pluggable scoping strategy. Enables users to provide custom
   * implementations of request, session, and wizard scopes. Implement and
   * pass to {@link
   * Container#setScopeStrategy(com.google.inject.Scope.Strategy)}.
   */
  public interface Strategy {

    /**
     * Finds an object for the given type and name in the request scope.
     * Creates a new object if necessary using the given factory.
     */
    <T> T findInRequest(Class<T> type, String name,
        Callable<? extends T> factory) throws Exception;

    /**
     * Finds an object for the given type and name in the session scope.
     * Creates a new object if necessary using the given factory.
     */
    <T> T findInSession(Class<T> type, String name,
        Callable<? extends T> factory) throws Exception;

    /**
     * Finds an object for the given type and name in the wizard scope.
     * Creates a new object if necessary using the given factory.
     */
    <T> T findInWizard(Class<T> type, String name,
        Callable<? extends T> factory) throws Exception;
  }
}
