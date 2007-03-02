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

package com.google.inject.spring;

import com.google.inject.Provider;
import com.google.inject.Inject;
import org.springframework.beans.factory.BeanFactory;

/**
 * Integrates Guice with Spring. Requires a binding to
 * {@link org.springframework.beans.factory.BeanFactory}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class SpringIntegration {

  private SpringIntegration() {}

  /**
   * Creates a provider which looks up objects from Spring using the given name.
   * Example usage:
   *
   * <pre>
   * bind(DataSource.class).toProvider(fromSpring(DataSource.class, "dataSource"));
   * </pre>
   */
  public static <T> Provider<T> fromSpring(Class<T> type, String name) {
    return new SpringProvider<T>(type, name);
  }

  static class SpringProvider<T> implements Provider<T> {

    BeanFactory beanFactory;
    boolean singleton;
    final Class<T> type;
    final String name;

    public SpringProvider(Class<T> type, String name) {
      this.type = type;
      this.name = name;
    }

    @Inject
    void initialize(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      if (!beanFactory.isTypeMatch(name, type)) {
        throw new ClassCastException("Spring bean named '" + name
            + "' does not implement " + type.getName() + ".");
      }
      singleton = beanFactory.isSingleton(name);
    }

    public T get() {
      return singleton ? getSingleton() : type.cast(beanFactory.getBean(name));
    }

    volatile T instance;

    private T getSingleton() {
      if (instance == null) {
        instance = type.cast(beanFactory.getBean(name));
      }
      return instance;
    }
  }
}
