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

import com.google.inject.name.Named;
import com.google.inject.name.Names;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;
import java.util.List;

/**
 * @author crazybob@google.com (Bob Lee)
 */
public class ErrorHandlingTest {

  public static void main(String[] args) throws CreationException {
    try {
      Guice.createInjector(new MyModule());
    }
    catch (CreationException e) {
      e.printStackTrace();
      System.err.println("--");
    }

    Injector bad = Guice.createInjector(new AbstractModule() {
      protected void configure() {
        bind(String.class).toProvider(new Provider<String>() {
          public String get() {
            return null;
          }
        });
      }
    });
    try {
      bad.getInstance(String.class);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.err.println("--");
    }
    try {
      bad.getInstance(NeedsString.class);
    }
    catch (Exception e) {
      e.printStackTrace();
      System.err.println("--");
    }
  }

  static class NeedsString {
    @Inject String mofo;
  }

  @Inject @Named("missing")
  static List<String> missing = null;

  static class Foo {
    @Inject
    public Foo(Runnable r) {}

    @Inject void setNames(List<String> names) {}
  }

  static class Bar {
    // Invalid constructor.
    Bar(String s) {}

    @Inject void setNumbers(@Named("numbers") List<Integer> numbers) {}

    @Inject void bar(@Named("foo") String s) {}
  }

  static class Tee {
    @Inject String s;

    @Inject void tee(String s, int i) {}

    @Inject Invalid invalid;
  }

  static class Invalid {
    Invalid(String s) {}
  }

  @Singleton @GoodScope
  static class TooManyScopes {
  }

  @Target(ElementType.TYPE)
  @Retention(RUNTIME)
  @ScopeAnnotation
  @interface GoodScope {}

  @interface BadScope {}

  @ImplementedBy(String.class)
  interface I {}

  static class MyModule extends AbstractModule {
    protected void configure() {
      bind(Runnable.class);
      bind(Foo.class);
      bind(Bar.class);
      bind(Tee.class);
      bind(new TypeLiteral<List<String>>() {});
      bind(String.class).annotatedWith(Names.named("foo")).in(Named.class);
      bind(Key.get(Runnable.class)).to(Key.get(Runnable.class));
      bind(TooManyScopes.class);
      bindScope(BadScope.class, Scopes.SINGLETON);
      bind(Object.class).toInstance(new Object() {
        @Inject void foo() {
          throw new RuntimeException();
        }
      });
      requestStaticInjection(ErrorHandlingTest.class);

      addError("I don't like %s", "you");
      
      Object o = "2";
      try {
        Integer i = (Integer) o;
      } catch (Exception e) {
        addError(e);
      }

      bind(Module.class).toInstance(this);
      bind(I.class);
    }
  }
}
