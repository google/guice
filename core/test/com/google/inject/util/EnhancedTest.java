package com.google.inject.util;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.InternalFlags;
import com.google.inject.matcher.Matchers;
import java.util.Optional;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EnhancedTest {
  @Test
  public void isEnhanced() {
    assumeTrue(InternalFlags.isBytecodeGenEnabled());

    Injector injector = Guice.createInjector(new InterceptingModule());
    Foo foo = injector.getInstance(Foo.class);
    Bar bar = injector.getInstance(Bar.class);
    // Validate preconditions: foo is intercepted & bar isn't.
    assertThat(foo.foo()).isEqualTo("intercepted-foo");
    assertThat(bar.bar()).isEqualTo("bar");

    // The actual tests.
    assertThat(Enhanced.isEnhanced(foo.getClass())).isTrue();
    assertThat(Enhanced.isEnhanced(bar.getClass())).isFalse();
  }

  @Test
  public void unenhancedClass() {
    assumeTrue(InternalFlags.isBytecodeGenEnabled());

    Injector injector = Guice.createInjector(new InterceptingModule());
    Foo foo = injector.getInstance(Foo.class);
    Bar bar = injector.getInstance(Bar.class);
    // Validate preconditions: foo is intercepted & bar isn't.
    assertThat(foo.foo()).isEqualTo("intercepted-foo");
    assertThat(bar.bar()).isEqualTo("bar");

    // The actual tests.
    assertThat(Enhanced.unenhancedClass(foo.getClass())).isEqualTo(Optional.of(Foo.class));
    assertThat(Enhanced.unenhancedClass(bar.getClass())).isEmpty();
  }

  private static class InterceptingModule extends AbstractModule {
    @Override
    protected void configure() {
      binder()
          .bindInterceptor(
              Matchers.only(Foo.class),
              Matchers.any(),
              new MethodInterceptor() {
                @Override
                public Object invoke(MethodInvocation i) throws Throwable {
                  return "intercepted-" + i.proceed();
                }
              });
      bind(Foo.class);
      bind(Bar.class);
    }
  }

  public static class Foo {
    public String foo() {
      return "foo";
    }
  }

  public static class Bar {
    public String bar() {
      return "bar";
    }
  }
}
