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

/**
 * Injects dependencies into constructors, methods and fields annotated with
 * {@link Inject}. Immutable.
 *
 * <p>When injecting a method or constructor, you can additionally annotate
 * its parameters with {@link Inject} and specify a dependency name. When a
 * parameter has no annotation, the container uses the name from the method or
 * constructor's {@link Inject} annotation respectively.
 *
 * <p>For example:
 *
 * <pre>
 *  class Foo {
 *
 *    // Inject the int constant named "i".
 *    &#64;Inject("i") int i;
 *
 *    // Inject the default implementation of Bar and the String constant
 *    // named "s".
 *    &#64;Inject Foo(Bar bar, @Inject("s") String s) {
 *      ...
 *    }
 *
 *    // Inject the default implementation of Baz and the Bob implementation
 *    // named "foo".
 *    &#64;Inject void initialize(Baz baz, @Inject("foo") Bob bob) {
 *      ...
 *    }
 *
 *    // Inject the default implementation of Tee.
 *    &#64;Inject void setTee(Tee tee) {
 *      ...
 *    }
 *  }
 * </pre>
 *
 * <p>To create and inject an instance of {@code Foo}:
 *
 * <pre>
 *  Container c = ...;
 *  Foo foo = c.inject(Foo.class);
 * </pre>
 *
 * @see ContainerBuilder
 * @author crazybob@google.com (Bob Lee)
 */
public interface Container {

  /**
   * Default dependency name.
   */
  String DEFAULT_NAME = "default";

  /**
   * Injects dependencies into the fields and methods of an existing object.
   */
  void inject(Object o);

  /**
   * Creates and injects a new instance of type {@code implementation}.
   */
  <T> T inject(Class<T> implementation);

  /**
   * Gets an instance of the given dependency which was declared in
   * {@link com.google.inject.ContainerBuilder}.
   */
  <T> T getInstance(Class<T> type, String name);

  /**
   * Convenience method.&nbsp;Equivalent to {@code getInstance(type,
   * DEFAULT_NAME)}.
   */
  <T> T getInstance(Class<T> type);

  /**
   * Sets the scope strategy for the current thread.
   */
  void setScopeStrategy(Scope.Strategy scopeStrategy);

  /**
   * Removes the scope strategy for the current thread.
   */
  void removeScopeStrategy();
}
