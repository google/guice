package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.PrivateModule;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ChildBindingAlreadySetErrorTest {
  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class Foo {
    @Inject
    Foo() {}
  }

  static class SubFoo extends Foo {
    @Inject
    SubFoo() {}
  }

  static class ChildModule extends PrivateModule {
    @Override
    protected void configure() {
      bind(Foo.class).to(SubFoo.class);
    }
  }

  @Test
  public void childBindingAlreadySetError() throws Exception {
    Injector injector = Guice.createInjector(new ChildModule());
    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> injector.getInstance(Foo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "child_binding_already_set_error.txt");
  }

  static class ChildModule2 extends PrivateModule {
    @Override
    protected void configure() {}

    @Provides
    Foo provideFoo() {
      return new Foo();
    }
  }

  static class DependsOnFoo {
    @Inject
    DependsOnFoo(Foo foo) {}
  }

  @Test
  public void childBindingAlreadySetMultipleTimesError() throws Exception {
    Injector injector = Guice.createInjector(new ChildModule(), new ChildModule2());
    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> injector.getInstance(DependsOnFoo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "child_binding_already_set_multiple_times_error.txt");
  }

  static class Bar {
    @Inject
    Bar(Foo foo, DependsOnFoo dependsOnFoo) {}
  }

  static class BarModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(Bar.class);
    }
  }

  @Test
  public void multipleChildBindingAlreadySetErrors() throws Exception {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () -> Guice.createInjector(new ChildModule(), new ChildModule2(), new BarModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "multiple_child_binding_already_set_errors.txt");
  }

  static class ChildModule3 extends PrivateModule {
    @Override
    protected void configure() {
      bind(Foo.class).to(SubFoo.class);
      // Trigger a JIT binding for DependsOnFoo in this PrivateModule.
      getProvider(DependsOnFoo.class);
    }
  }

  static class ChildModule4 extends PrivateModule {
    @Override
    protected void configure() {
      bind(DependsOnFoo.class).toInstance(new DependsOnFoo(new Foo()));
    }
  }

  @Test
  public void childBindingAlreadySetByJustInTimeBinding() throws Exception {
    Injector injector = Guice.createInjector(new ChildModule3(), new ChildModule4());
    ConfigurationException exception =
        assertThrows(ConfigurationException.class, () -> injector.getInstance(DependsOnFoo.class));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "child_binding_already_set_by_just_in_time_binding.txt");
  }
}
