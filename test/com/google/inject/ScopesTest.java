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

import junit.framework.TestCase;

import java.lang.annotation.Target;
import java.lang.annotation.Retention;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ScopesTest extends TestCase {

  public void testScopedAnnotation() {
    assertEquals("foo", Scopes.getScopeNameForType(AnnotatedWithScoped.class,
        InvalidErrorHandler.INSTANCE));
  }

  @Scoped("foo")
  static class AnnotatedWithScoped {}

  public void testCustomAnnotation() {
    assertEquals("custom", Scopes.getScopeNameForType(CustomAnnotated.class,
        InvalidErrorHandler.INSTANCE));
  }

  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Scoped("custom")
  @interface CustomScoped {}

  @CustomScoped
  static class CustomAnnotated {}

  public void testMultipleAnnotations() {
    final String[] messageHolder = new String[1];
    Scopes.getScopeNameForType(MultiplyAnnotated.class,
        new AbstractErrorHandler() {
          public void handle(String message) {
            messageHolder[0] = message;
          }

          public void handle(Throwable t) {
            fail();
          }
        });
    String message = messageHolder[0];
    assertTrue(message.startsWith("Scope is set more than once by annotations"
        + " on com.google.inject.ScopesTest$MultiplyAnnotated."));

    // We don't know what order these will come in, so we do our best.
    assertTrue(message.contains("@CustomScoped"));
    assertTrue(message.contains("custom"));
    assertTrue(message.contains("@Scoped"));
    assertTrue(message.contains("foo"));
  }

  @Scoped("foo")
  @CustomScoped
  static class MultiplyAnnotated {}

  public void testContainerScopedAnnotation()
      throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    ContainerBuilder.BindingBuilder<Singleton> bindingBuilder
        = builder.bind(Singleton.class);
    builder.create(false);
    assertSame(Scopes.CONTAINER, bindingBuilder.scope);
  }

  @ContainerScoped
  static class Singleton {}

  public void testOverriddingAnnotation()
      throws ContainerCreationException {
    ContainerBuilder builder = new ContainerBuilder();
    ContainerBuilder.BindingBuilder<Singleton> bindingBuilder
        = builder.bind(Singleton.class).in(Scopes.DEFAULT);
    builder.create(false);
    assertSame(Scopes.DEFAULT, bindingBuilder.scope);
  }

  public void testBindingToInstanceWithScope() {
    ContainerBuilder builder = new ContainerBuilder();
    builder.bind(Singleton.class).to(new Singleton()).in(Scopes.DEFAULT);
    try {
      builder.create(false);
      fail();
    } catch (ContainerCreationException e) { /* expected */ }
  }
}
