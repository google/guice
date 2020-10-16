package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import java.lang.annotation.Retention;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DuplicateElementErrorTest {

  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  static class DuplicateElementModule extends AbstractModule {
    @ProvidesIntoSet
    String provideFirst() {
      return "element";
    }

    @ProvidesIntoSet
    String provideSecond() {
      return "element";
    }

    @Override
    protected void configure() {
      Multibinder.newSetBinder(binder(), String.class).addBinding().toInstance("element");
    }
  }

  @Test
  public void duplicateElementError() {
    Injector injector = Guice.createInjector(new DuplicateElementModule());
    ProvisionException exception =
        assertThrows(
            ProvisionException.class, () -> injector.getInstance(new Key<Set<String>>() {}));
    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), "duplicate_element_error.txt");
  }

  @Qualifier
  @Retention(RUNTIME)
  @interface Foo {}

  static class IntWrapper {
    private static final AtomicInteger counter = new AtomicInteger(100);

    int value;
    int id;

    IntWrapper(int value) {
      this.value = value;
      this.id = counter.getAndIncrement();
    }

    @Override
    public int hashCode() {
      return value;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof IntWrapper) {
        return ((IntWrapper) other).value == value;
      }
      return false;
    }

    @Override
    public String toString() {
      // Return different value for different instance even when they equal to one and other.
      // This is used to test when duplicate elements have different string representation.
      return String.format("IntWrapper(%s)", id);
    }
  }

  static class MultipleDuplicateElementsModule extends AbstractModule {
    @ProvidesIntoSet
    @Foo
    IntWrapper provideFirstIntWrapper0() {
      return new IntWrapper(0);
    }

    @ProvidesIntoSet
    @Foo
    IntWrapper provideSecondIntWrapper0() {
      return new IntWrapper(0);
    }

    @ProvidesIntoSet
    @Foo
    IntWrapper provideFirstIntWrapper1() {
      return new IntWrapper(1);
    }

    @ProvidesIntoSet
    @Foo
    IntWrapper provideSecondIntWrapper1() {
      return new IntWrapper(1);
    }

    @Override
    protected void configure() {
      Multibinder.newSetBinder(binder(), Key.get(IntWrapper.class, Foo.class))
          .addBinding()
          .toProvider(() -> new IntWrapper(1));
    }
  }

  @Test
  public void multipleDuplicatesElementError() {
    Injector injector = Guice.createInjector(new MultipleDuplicateElementsModule());
    ProvisionException exception =
        assertThrows(
            ProvisionException.class,
            () -> injector.getInstance(new Key<Set<IntWrapper>>(Foo.class) {}));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "multiple_duplicate_elements_error.txt");
  }
}
