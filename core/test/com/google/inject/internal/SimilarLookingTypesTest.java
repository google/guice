package com.google.inject.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.internal.MissingImplementationErrorHints.areSimilarLookingTypes;
import static com.google.inject.internal.MissingImplementationErrorHints.convertToLatterViaJvmAnnotations;

import com.google.inject.TypeLiteral;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class SimilarLookingTypesTest {

  private static class Wrapper {
    private static class Foo {}
  }

  private static class Foo {}

  @Test
  public void similarClasses() {
    assertThat(areSimilarLookingTypes(Foo.class, Wrapper.Foo.class)).isTrue();
  }

  @Test
  public void differentClasses() {
    assertThat(areSimilarLookingTypes(String.class, Integer.class)).isFalse();
  }

  @Test
  public void similarGenericTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<List<com.google.common.base.Optional<String>>>() {}.getType(),
                new TypeLiteral<List<Optional<String>>>() {}.getType()))
        .isTrue();
  }

  @Test
  public void multipleSimilarGenericTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<Optional<com.google.common.base.Optional<String>>>() {}.getType(),
                new TypeLiteral<com.google.common.base.Optional<Optional<String>>>() {}.getType()))
        .isTrue();
  }

  @Test
  public void differentGenericTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<List<String>>() {}.getType(),
                new TypeLiteral<List<Integer>>() {}.getType()))
        .isFalse();
  }

  @Test
  public void similarGenericArrayTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<com.google.common.base.Optional<String>[]>() {}.getType(),
                new TypeLiteral<Optional<String>[]>() {}.getType()))
        .isTrue();
  }

  @Test
  public void differentGenericArrayTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<List<String>[]>() {}.getType(),
                new TypeLiteral<List<String>[][]>() {}.getType()))
        .isFalse();
  }

  @Test
  public void similarWildcardTypes() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<com.google.common.base.Optional<?>>() {}.getType(),
                new TypeLiteral<Optional<?>>() {}.getType()))
        .isTrue();
  }

  @Test
  public void similarExtendsCase() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<Optional<Foo>>() {}.getType(),
                new TypeLiteral<Optional<? extends Foo>>() {}.getType()))
        .isTrue();
  }

  @Test
  public void differentExtendsCase() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<Optional<String>>() {}.getType(),
                new TypeLiteral<Optional<? extends Foo>>() {}.getType()))
        .isFalse();
  }

  @Test
  public void similarSuperCase() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<Optional<Foo>>() {}.getType(),
                new TypeLiteral<Optional<? super Foo>>() {}.getType()))
        .isTrue();
  }

  @Test
  public void differentSuperCase() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<Optional<String>>() {}.getType(),
                new TypeLiteral<Optional<? super Foo>>() {}.getType()))
        .isFalse();
  }

  @Test
  public void differentWildcardType_extends() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<List<?>>() {}.getType(),
                new TypeLiteral<List<? extends String>>() {}.getType()))
        .isFalse();
  }

  @Test
  public void differentWildcardType_super() {
    assertThat(
            areSimilarLookingTypes(
                new TypeLiteral<List<?>>() {}.getType(),
                new TypeLiteral<List<? super String>>() {}.getType()))
        .isFalse();
  }

  @Test
  public void oneTypeConversion() {
    assertThat(
            convertToLatterViaJvmAnnotations(
                new TypeLiteral<Map<String, Integer>>() {},
                new TypeLiteral<Map<? extends String, Integer>>() {}))
        .isEqualTo("java.util.Map<@JvmWildcard java.lang.String, java.lang.Integer>");
  }

  @Test
  public void mixedConversion() {
    assertThat(
            convertToLatterViaJvmAnnotations(
                new TypeLiteral<Map<String, ? extends Integer>>() {},
                new TypeLiteral<Map<? extends String, Integer>>() {}))
        .isEqualTo(
            "java.util.Map<@JvmWildcard java.lang.String,"
                + " @JvmSuppressWildcards java.lang.Integer>");
  }

  @Test
  public void complexConversion() {
    assertThat(
            convertToLatterViaJvmAnnotations(
                new TypeLiteral<Map<List<? extends String>, ? super Integer>>() {},
                new TypeLiteral<Map<? extends List<? extends String>, Integer>>() {}))
        .isEqualTo(
            "java.util.Map<@JvmWildcard java.util.List<out java.lang.String>,"
                + " @JvmSuppressWildcards java.lang.Integer>");
  }
}
