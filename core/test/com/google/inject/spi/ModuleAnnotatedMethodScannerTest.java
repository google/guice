/**
 * Copyright (C) 2015 Google Inc.
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

package com.google.inject.spi;

import static com.google.inject.Asserts.assertContains;
import static com.google.inject.name.Names.named;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.internal.util.StackTraceElements;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import junit.framework.TestCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Set;

/** Tests for {@link ModuleAnnotatedMethodScanner} usage. */
public class ModuleAnnotatedMethodScannerTest extends TestCase {
  
  public void testScanning() throws Exception {
    Module module = new AbstractModule() {
      @Override protected void configure() {
        install(new NamedMunger().forModule(this));
      }
      
      @TestProvides @Named("foo") String foo() {
        return "foo";
      }
      
      @TestProvides @Named("foo2") String foo2() {
        return "foo2";
      }
    };
    Injector injector = Guice.createInjector(module);

    // assert no bindings named "foo" or "foo2" exist -- they were munged.
    assertNull(injector.getExistingBinding(Key.get(String.class, named("foo"))));
    assertNull(injector.getExistingBinding(Key.get(String.class, named("foo2"))));

    Binding<String> fooBinding = injector.getBinding(Key.get(String.class, named("foo-munged")));
    Binding<String> foo2Binding = injector.getBinding(Key.get(String.class, named("foo2-munged")));
    assertEquals("foo", fooBinding.getProvider().get());
    assertEquals("foo2", foo2Binding.getProvider().get());
    
    // Validate the provider has a sane toString
    assertEquals(methodName(TestProvides.class, "foo", module),
        fooBinding.getProvider().toString());
    assertEquals(methodName(TestProvides.class, "foo2", module),
        foo2Binding.getProvider().toString());
  }

  public void testMoreThanOneClaimedAnnotationFails() throws Exception {
    final NamedMunger scanner = new NamedMunger();
    Module module = new AbstractModule() {
      @Override protected void configure() {
        install(scanner.forModule(this));
      }
      
      @TestProvides @TestProvides2 String foo() {
        return "foo";
      }
    };
    try {
      Guice.createInjector(module);
      fail();
    } catch(CreationException expected) {
      assertEquals(1, expected.getErrorMessages().size());
      assertContains(expected.getMessage(),
          "More than one annotation claimed by " + scanner + " on method "
              + module.getClass().getName() + ".foo(). Methods can only have "
              + "one annotation claimed per scanner.");
    }
  }
  
  private String methodName(Class<? extends Annotation> annotation, String method, Object container)
      throws Exception {
    return "@" + annotation.getName() + " "
        + StackTraceElements.forMember(container.getClass().getDeclaredMethod(method));
  }
  
  @Documented @Target(METHOD) @Retention(RUNTIME)
  private @interface TestProvides {}

  @Documented @Target(METHOD) @Retention(RUNTIME)
  private @interface TestProvides2 {}
  
  private static class NamedMunger extends ModuleAnnotatedMethodScanner {
    @Override
    public Set<? extends Class<? extends Annotation>> annotationClasses() {
      return ImmutableSet.of(TestProvides.class, TestProvides2.class);
    }

    @Override
    public <T> Key<T> prepareMethod(Binder binder, Annotation annotation, Key<T> key,
        InjectionPoint injectionPoint) {
      return Key.get(key.getTypeLiteral(),
          Names.named(((Named) key.getAnnotation()).value() + "-munged"));
    }
  }
}
