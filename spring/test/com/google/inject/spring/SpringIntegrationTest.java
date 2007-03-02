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

import junit.framework.TestCase;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.BeanFactory;
import com.google.inject.PerformanceComparison.TeeImpl;
import com.google.inject.Injector;
import com.google.inject.Guice;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import static com.google.inject.spring.SpringIntegration.*;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class SpringIntegrationTest extends TestCase {

  public void testSpringIntegration() throws CreationException {
    final DefaultListableBeanFactory beanFactory
        = new DefaultListableBeanFactory();

    RootBeanDefinition singleton
        = new RootBeanDefinition(Singleton.class);
    beanFactory.registerBeanDefinition("singleton", singleton);

    RootBeanDefinition prototype
        = new RootBeanDefinition(Prototype.class, false);
    beanFactory.registerBeanDefinition("prototype", prototype);

    Injector injector = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(BeanFactory.class).toInstance(beanFactory);
        bind(Singleton.class)
            .toProvider(fromSpring(Singleton.class, "singleton"));
        bind(Prototype.class)
            .toProvider(fromSpring(Prototype.class, "prototype"));
      }
    });

    assertNotNull(injector.getInstance(Singleton.class));
    assertSame(injector.getInstance(Singleton.class),
        injector.getInstance(Singleton.class));

    assertNotNull(injector.getInstance(Prototype.class));
    assertNotSame(injector.getInstance(Prototype.class),
        injector.getInstance(Prototype.class));
  }

  static class Singleton {}
  static class Prototype {}
}
