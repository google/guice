// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject;

import com.google.inject.util.Objects;

import java.util.Map;
import java.util.Properties;

/**
 * A support class for {@link Module}s which reduces repetition and
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

    } finally {
      this.builder = null;
    }
  }

  /**
   * Configures a {@link ContainerBuilder} via the exposed methods.
   */
  protected abstract void configure();

  /**
   * @see ContainerBuilder#put(String, Scope)
   */
  protected void put(String name, Scope scope) {
    builder.put(name, scope);
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
  protected <T> ContainerBuilder.BindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
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
   * @see ContainerBuilder#bind(String)
   */
  protected ContainerBuilder.ConstantBindingBuilder bind(String name) {
    return builder.bind(name);
  }

  /**
   * @see ContainerBuilder#bindProperties(java.util.Map)
   */
  protected void bindProperties(Map<String, String> properties) {
    builder.bindProperties(properties);
  }

  /**
   * @see ContainerBuilder#bindProperties(java.util.Properties)
   */
  protected void bindProperties(Properties properties) {
    builder.bindProperties(properties);
  }

  /**
   * @see ContainerBuilder#requestStaticInjection(Class[])
   */
  protected void requestStaticInjection(Class<?>... types) {
    builder.requestStaticInjection(types);
  }

  /**
   * @see ContainerBuilder#apply(Module)
   */
  protected void apply(Module module) {
    builder.apply(module);
  }
}
