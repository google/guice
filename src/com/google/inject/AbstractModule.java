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

import com.google.inject.util.Objects;
import java.util.Map;
import java.util.Properties;
import java.lang.annotation.Annotation;

/**
 * A support class for {@link Module Modules} which reduces repetition and
 * results in a more readable configuration. Simply extends this class,
 * implement {@link #configure()}, and call the inherited methods which mirror
 * those found in {@link ContainerBuilder}. For example:
 *
 * <pre>
 * public class MyModule extends AbstractModule {
 *   protected void configure() {
 *     bind(Foo.class).to(FooImpl.class).in(Scopes.CONTAINER);
 *     bind(BarImpl.class);
 *     link(Bar.class).to(BarImpl.class);
 *     bind("port").to(8080);
 *   }
 * }
 * </pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class AbstractModule implements Module {

  ContainerBuilder builder;

  public final synchronized void configure(ContainerBuilder builder) {
    try {
      if (this.builder != null) {
        throw new IllegalStateException("Re-entry is not allowed.");
      }
      this.builder = Objects.nonNull(builder, "builder");

      configure();

    }
    finally {
      this.builder = null;
    }
  }

  /**
   * Configures a {@link ContainerBuilder} via the exposed methods.
   */
  protected abstract void configure();

  /**
   * Gets the builder.
   */
  protected ContainerBuilder builder() {
    return builder;
  }

  /**
   * @see ContainerBuilder#scope(Class, Scope)
   */
  protected void scope(Class<? extends Annotation> scopeAnnotation,
      Scope scope) {
    builder.scope(scopeAnnotation, scope);
  }

  /**
   * @see ContainerBuilder#bind(Key)
   */
  protected <T> ContainerBuilder.BindingBuilder<T> bind(Key<T> key) {
    return builder.bind(key);
  }

  /**
   * @see ContainerBuilder#bind(TypeLiteral)
   */
  protected <T> ContainerBuilder.BindingBuilder<T> bind(
      TypeLiteral<T> typeLiteral) {
    return builder.bind(typeLiteral);
  }

  /**
   * @see ContainerBuilder#bind(Class)
   */
  protected <T> ContainerBuilder.BindingBuilder<T> bind(Class<T> clazz) {
    return builder.bind(clazz);
  }

  /**
   * @see ContainerBuilder#link(Key)
   */
  protected <T> ContainerBuilder.LinkedBindingBuilder<T> link(Key<T> key) {
    return builder.link(key);
  }

  /**
   * @see ContainerBuilder#bindConstant(Class)
   */
  protected ContainerBuilder.ConstantBindingBuilder bindConstant(
      Class<? extends Annotation> annotationType) {
    return builder.bindConstant(annotationType);
  }

  /**
   * @see ContainerBuilder#bindConstant(java.lang.annotation.Annotation)
   */
  protected ContainerBuilder.ConstantBindingBuilder bindConstant(
      Annotation annotation) {
    return builder.bindConstant(annotation);
  }

  /**
   * @see ContainerBuilder#install(Module)
   */
  protected void install(Module module) {
    builder.install(module);
  }

  /**
   * @see ContainerBuilder#requestStaticInjection(Class[])
   */
  protected void requestStaticInjection(Class<?>... types) {
    builder.requestStaticInjection(types);
  }
}
