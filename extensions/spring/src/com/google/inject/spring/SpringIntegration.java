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

import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
import com.google.inject.name.Names;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;

/**
 * Integrates Guice with Spring.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class SpringIntegration {
  private SpringIntegration() {}

  /**
   * Creates a provider which looks up objects from Spring using the given name.
   * Expects a binding to {@link
   * org.springframework.beans.factory.BeanFactory}. Example usage:
   *
   * <pre>
   * bind(DataSource.class)
   *   .toProvider(fromSpring(DataSource.class, "dataSource"));
   * </pre>
   */
  public static <T> Provider<T> fromSpring(Class<T> type, String name) {
    return new InjectableSpringProvider<T>(type, name);
  }

  /**
   * Binds all Spring beans from the given factory by name. For a Spring bean
   * named "foo", this method creates a binding to the bean's type and
   * {@code @Named("foo")}.
   *
   * @see com.google.inject.name.Named
   * @see com.google.inject.name.Names#named(String) 
   */
  public static void bindAll(Binder binder, ListableBeanFactory beanFactory) {
    binder = binder.skipSources(SpringIntegration.class);

    for (String name : beanFactory.getBeanDefinitionNames()) {
      Class<?> type = beanFactory.getType(name);
      bindBean(binder, beanFactory, name, type);
    }
  }

  static <T> void bindBean(Binder binder, ListableBeanFactory beanFactory,
      String name, Class<T> type) {
    SpringProvider<T> provider
        = SpringProvider.newInstance(type, name);
    try {
      provider.initialize(beanFactory);
    }
    catch (Exception e) {
      binder.addError(e);
      return;
    }

    binder.bind(type)
        .annotatedWith(Names.named(name))
        .toProvider(provider);
  }

  static class SpringProvider<T> implements Provider<T> {

    BeanFactory beanFactory;
    boolean singleton;
    final Class<T> type;
    final String name;

    public SpringProvider(Class<T> type, String name) {
      this.type = checkNotNull(type, "type");
      this.name = checkNotNull(name, "name");
    }

    static <T> SpringProvider<T> newInstance(Class<T> type, String name) {
      return new SpringProvider<T>(type, name);
    }

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

  static class InjectableSpringProvider<T> extends SpringProvider<T> {

    InjectableSpringProvider(Class<T> type, String name) {
      super(type, name);
    }

    @Inject
    @Override
    void initialize(BeanFactory beanFactory) {
      super.initialize(beanFactory);
    }
  }
}
