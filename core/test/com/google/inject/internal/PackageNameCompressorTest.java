/*
 * Copyright (C) 2020 The Dagger Authors.
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

package com.google.inject.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.inject.internal.PackageNameCompressor.LEGEND_FOOTER;
import static com.google.inject.internal.PackageNameCompressor.LEGEND_HEADER;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PackageNameCompressor}. */
@RunWith(JUnit4.class)
public class PackageNameCompressorTest {
  @Test
  public void testEmptyInput() {
    String input = "";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(input);
  }

  @Test
  public void testSimple() {
    String input = "Something is wrong with foo.bar.baz.Foo class!";
    String expectedOutput =
        "Something is wrong with Foo class!"
            + LEGEND_HEADER
            + "Foo: \"foo.bar.baz.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testSingleLetterClassName() {
    String input = "Something is wrong with foo.bar.baz.F class!";
    String expectedOutput =
        "Something is wrong with F class!"
            + LEGEND_HEADER
            + "F: \"foo.bar.baz.F\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testSameSimpleNames() {
    String input = "Something is wrong with foo.bar.baz.Foo and foo.bar.qux.Foo class!";
    String expectedOutput =
        "Something is wrong with baz.Foo and qux.Foo class!"
            + LEGEND_HEADER
            + "baz.Foo: \"foo.bar.baz.Foo\"\n"
            + "qux.Foo: \"foo.bar.qux.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testMethodNames() {
    String input = "Something is wrong with foo.bar.baz.Foo.provideFoo()!";
    String expectedOutput =
        "Something is wrong with Foo.provideFoo()!"
            + LEGEND_HEADER
            + "Foo: \"foo.bar.baz.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testMultipleLevelsOfConflicts() {
    String input = "Something is wrong with z.a.b.c.Foo, z.b.b.c.Foo, z.a.b.d.Foo class!";
    String expectedOutput =
        "Something is wrong with a.b.c.Foo, b.b.c.Foo, d.Foo class!"
            + LEGEND_HEADER
            + "a.b.c.Foo: \"z.a.b.c.Foo\"\n"
            + "b.b.c.Foo: \"z.b.b.c.Foo\"\n"
            + "d.Foo:     \"z.a.b.d.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  // In some sense, we're really just compressing the outer class since the legend is going to
  // only refer to the outer class.
  @Test
  public void testInnerClassesKeepOuterClassNameToo() {
    String input = "Something is wrong with foo.bar.baz.Foo.Bar.Baz class!";
    String expectedOutput =
        "Something is wrong with Foo.Bar.Baz class!"
            + LEGEND_HEADER
            + "Foo: \"foo.bar.baz.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  // If relying on conflicts by inserting into the map, an extra conflict on c.Foo may result in
  // uneven renaming because when the first two conflict on c.Foo they may make room for the next
  // conflict to just take over what had previously been a conflict. Make sure that this unevenness
  // doesn't happen.
  @Test
  public void testThreeMultiLevelConflicts() {
    String input = "Something is wrong with z.a.c.Foo, z.b.c.Foo, and z.c.c.Foo class!";
    String expectedOutput =
        "Something is wrong with a.c.Foo, b.c.Foo, and c.c.Foo class!"
            + LEGEND_HEADER
            + "a.c.Foo: \"z.a.c.Foo\"\n"
            + "b.c.Foo: \"z.b.c.Foo\"\n"
            + "c.c.Foo: \"z.c.c.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testSingleGeneric() {
    String input = "No implementation found for java.util.Set<java.lang.String>.";
    String expectedOutput = "No implementation found for Set<String>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testMultipleGeneric() {
    String input = "No implementation found for java.util.Map<java.lang.String, java.lang.String>.";
    String expectedOutput = "No implementation found for Map<String, String>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testNestedGeneric() {
    String input =
        "No implementation found for java.util.Map<java.lang.String,"
            + " java.util.Set<java.lang.String>>.";
    String expectedOutput = "No implementation found for Map<String, Set<String>>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testQuotedStringShouldNotBeCompressed() {
    String input =
        "Duplicate key \"com.google.Foo\" found in java.util.Map<java.lang.String,"
            + " java.lang.Object>.";
    String expectedOutput = "Duplicate key \"com.google.Foo\" found in Map<String, Object>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testEmptyQuotedString() {
    String input =
        "Duplicate key \"\" found in java.util.Map<java.lang.String," + " java.lang.Object>.";
    String expectedOutput = "Duplicate key \"\" found in Map<String, Object>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testUnbalancedQuotedString() {
    String input =
        "Duplicate key \"java.util.List found in java.util.Map<java.lang.String,"
            + " java.lang.Object>.";
    String expectedOutput = "Duplicate key \"List found in Map<String, Object>.";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testQuotedStringSpanMultipleLines() {
    String input = "Some strange string with \"java.util.List, \n and java.lang.Integer\".";
    String expectedOutput = "Some strange string with \"List, \n and Integer\".";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testDoesNotCompressSubstringsOfClasses() {
    // This shouldn't try to compress the "ar.Foo" in "Bar.Foo"
    String input = "Something is wrong with Bar.Foo class!";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(input);
  }

  @Test
  public void testDoesNotCompressShortPackageNames() {
    // This shouldn't try to compress the foo.Foo.
    String input = "Something is wrong with foo.Foo should not be empty!";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(input);
  }

  @Test
  public void testNoClassNamesDoNotPutInLegend() {
    String input = "Something is wrong with something!";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(input);
  }

  @Test
  public void testFullConflictsDoNotPutInLegend() {
    String input = "Something is wrong with foo.Foo and bar.Foo class!";
    // No shortening can be done without loss of clarity so do not modify this and add no legend.
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(input);
  }

  @Test
  public void testLegendDoesNotIncludeJavaLang() {
    String input = "Something is wrong with java.lang.Set, java.lang.a.Foo,"
        + " and java.lang.b.Foo class!";
    String expectedOutput =
        "Something is wrong with Set, a.Foo, and b.Foo class!"
            + LEGEND_HEADER
            + "a.Foo: \"java.lang.a.Foo\"\n"
            + "b.Foo: \"java.lang.b.Foo\"\n"
            + LEGEND_FOOTER;
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testOnlyExcludedPrefixesDoesNotPutInLegend() {
    String input = "Something is wrong with java.lang.Set class!";
    String expectedOutput = "Something is wrong with Set class!";
    assertThat(PackageNameCompressor.compressPackagesInMessage(input)).isEqualTo(expectedOutput);
  }

  @Test
  public void testWrappedErrorMessageRemovesEmbeddedLegend() {
    String error =
        "No implement bound for com.google.foo.bar.Baz. Required by com.google.foo.BarImpl.";
    String compressedError = PackageNameCompressor.compressPackagesInMessage(error);
    String input =
        "Something's gone wrong while locating com.google.foo.baz.BazImpl. See error:\n"
            + compressedError;

    String expected =
        "Something's gone wrong while locating BazImpl. See error:\n"
            + compressedError
            + LEGEND_HEADER
            + "BazImpl: \"com.google.foo.baz.BazImpl\"\n"
            + LEGEND_FOOTER;
    String actual = PackageNameCompressor.compressPackagesInMessage(input);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void wrappedErrorMessageRemovesEmbeddedLegend_differentCompressionNames() {
    String error =
        "No implement bound for com.google.foo.bar.Baz. Required by com.google.foo.BazImpl.";
    String compressedError = PackageNameCompressor.compressPackagesInMessage(error);
    String input =
        "Something's gone wrong while locating com.google.foo.baz.BazImpl. See error:\n"
            + compressedError;

    String expected =
        "Something's gone wrong while locating baz.BazImpl. See error:\n"
            + compressedError
            + LEGEND_HEADER
            + "baz.BazImpl: \"com.google.foo.baz.BazImpl\"\n"
            + LEGEND_FOOTER;
    String actual = PackageNameCompressor.compressPackagesInMessage(input);
    assertThat(actual).isEqualTo(expected);
  }
}
