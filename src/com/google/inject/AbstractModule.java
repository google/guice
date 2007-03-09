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

import com.google.inject.binder.ConstantBindingBuilder;
import com.google.inject.binder.AnnotatedBindingBuilder;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.binder.AnnotatedConstantBindingBuilder;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.SourceProviders;
import com.google.inject.util.Objects;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * A support class for {@link Module}s which reduces repetition and results in
 * a more readable configuration. Simply extend this class, implement {@link
 * #configure()}, and call the inherited methods which mirror those found in
 * {@link Binder}. For example:
 *
 * <pre>
 * import static com.google.inject.Names.named;
 *
 * public class MyModule extends AbstractModule {
 *   protected void configure() {
 *     bind(Foo.class).to(FooImpl.class).in(Scopes.SINGLETON);
 *     bind(BarImpl.class);
 *     link(Bar.class).to(BarImpl.class);
 *     bindConstant(named("port")).to(8080);
 *   }
 * }
 * </pre>
 *
 * @author crazybob@google.com (Bob Lee)
 */
public abstract class AbstractModule implements Module {

  static {
    SourceProviders.skip(AbstractModule.class);
  }

  Binder builder;

  public final synchronized void configure(Binder builder) {
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
   * Configures a {@link Binder} via the exposed methods.
   */
  protected abstract void configure();

  /**
   * Gets direct access to the underlying {@code Binder}.
   */
  protected Binder binder() {
    return builder;
  }

  /**
   * @see Binder#bindScope(Class, Scope)
   */
  protected void bindScope(Class<? extends Annotation> scopeAnnotation,
      Scope scope) {
    builder.bindScope(scopeAnnotation, scope);
  }

  /**
   * @see Binder#bind(Key)
   */
  protected <T> LinkedBindingBuilder<T> bind(Key<T> key) {
    return builder.bind(key);
  }

  /**
   * @see Binder#bind(TypeLiteral)
   */
  protected <T> AnnotatedBindingBuilder<T> bind(TypeLiteral<T> typeLiteral) {
    return builder.bind(typeLiteral);
  }

  /**
   * @see Binder#bind(Class)
   */
  protected <T> AnnotatedBindingBuilder<T> bind(Class<T> clazz) {
    return builder.bind(clazz);
  }

  /**
   * @see Binder#bindConstant()
   */
  protected AnnotatedConstantBindingBuilder bindConstant() {
    return builder.bindConstant();
  }

  /**
   * @see Binder#install(Module)
   */
  protected void install(Module module) {
    builder.install(module);
  }

  /**
   * @see Binder#addError(String, Object[])
   */
  protected void addError(String message, Object... arguments) {
    builder.addError(message, arguments);
  }

  /**
   * @see Binder#addError(Throwable) 
   */
  protected void addError(Throwable t) {
    builder.addError(t);
  }

  /**
   * @see Binder#requestStaticInjection(Class[])
   */
  protected void requestStaticInjection(Class<?>... types) {
    builder.requestStaticInjection(types);
  }

  /**
   * @see Binder#bindInterceptor(com.google.inject.matcher.Matcher,
   *  com.google.inject.matcher.Matcher,
   *  org.aopalliance.intercept.MethodInterceptor[])
   */
  protected void bindInterceptor(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      MethodInterceptor... interceptors) {
    builder.bindInterceptor(classMatcher, methodMatcher, interceptors);
  }
}
