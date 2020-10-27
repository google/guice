package com.google.inject.errors;

import static com.google.inject.errors.ErrorMessageTestUtils.assertGuiceErrorEqualsIgnoreLineNumber;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.internal.InternalFlags;
import com.google.inject.internal.InternalFlags.IncludeStackTraceOption;
import com.google.inject.multibindings.ClassMapKey;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import java.lang.annotation.Retention;
import javax.inject.Qualifier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class DuplicateMapKeyErrorTest {
  @Before
  public void checkStackTraceIsIncluded() {
    // Only run the tests when the stack traces are included in the errors.
    assumeTrue(InternalFlags.getIncludeStackTraceOption() != IncludeStackTraceOption.OFF);
  }

  @Qualifier
  @Retention(RUNTIME)
  @interface First {}

  static class MapModule extends AbstractModule {
    @Override
    protected void configure() {
      MapBinder<String, String> mapBinder =
          MapBinder.newMapBinder(binder(), String.class, String.class);
      mapBinder.addBinding("first").toInstance("a");
      mapBinder.addBinding("first").to(Key.get(String.class, First.class));
    }

    @Provides
    @First
    String provideFirstString() {
      return "b";
    }
  }

  static class ContributorModule extends AbstractModule {
    @ProvidesIntoMap
    @StringMapKey("first")
    String provideFirstIntoMap() {
      return "c";
    }

    @ProvidesIntoMap
    @StringMapKey("second")
    String provideSecondIntoMap() {
      return "d";
    }
  }

  @Test
  public void duplicateMapKeyError() {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () -> Guice.createInjector(new MapModule(), new ContributorModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(exception.getMessage(), "duplicate_map_key_error.txt");
  }

  static class SecondContributorModule extends AbstractModule {
    @Override
    protected void configure() {
      MapBinder.newMapBinder(binder(), String.class, String.class);
    }

    @ProvidesIntoMap
    @StringMapKey("second")
    String provideSecondIntoMap() {
      return "e";
    }
  }

  @Test
  public void multipleDuplicateMapKeysError() {
    CreationException exception =
        assertThrows(
            CreationException.class,
            () ->
                Guice.createInjector(
                    new MapModule(), new ContributorModule(), new SecondContributorModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "multiple_duplicate_map_keys_error.txt");
  }

  static class Foo {}

  static class ClassKeyMapBinderModule extends AbstractModule {

    @Override
    protected void configure() {
      MapBinder.newMapBinder(binder(), Class.class, String.class);
    }

    @ProvidesIntoMap
    @ClassMapKey(Foo.class)
    String provideFoo() {
      return "foo";
    }

    @ProvidesIntoMap
    @ClassMapKey(Foo.class)
    String provideFoo2() {
      return "foo2";
    }
  }

  @Test
  public void duplicateMapKeysError_classKeyIsNotCompressed() {
    // Test that the key printed in the error message are not compressed.
    CreationException exception =
        assertThrows(
            CreationException.class, () -> Guice.createInjector(new ClassKeyMapBinderModule()));
    assertGuiceErrorEqualsIgnoreLineNumber(
        exception.getMessage(), "duplicate_map_keys_error_class_key_is_not_compressed.txt");
  }
}
