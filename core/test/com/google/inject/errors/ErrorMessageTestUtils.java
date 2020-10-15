package com.google.inject.errors;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.Resources;
import java.io.IOException;

final class ErrorMessageTestUtils {
  private static final String LINE_NUMBER_REGEX = ":\\d+";
  private static final String LINE_NUMBER_PLACEHOLDER = ":0";

  private ErrorMessageTestUtils() {}

  /**
   * Compare expected error message with the actual error message.
   *
   * <p>Differences in line number are ignored. This make it so that unrelated changes like adding
   * or removing an import would not break the test because the line numbers would change.
   */
  static void assertGuiceErrorEqualsIgnoreLineNumber(String actual, String expectedFileName) {
    String expectedError;
    try {
      expectedError = getExpectedError(expectedFileName);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read expected error message.", e);
    }
    assertErrorMessage(actual, expectedError);
  }

  private static void assertErrorMessage(String actual, String expected) {
    String actualWithoutLineNumber = actual.replaceAll(LINE_NUMBER_REGEX, LINE_NUMBER_PLACEHOLDER);
    String expectedWithoutLineNumber =
        expected.replaceAll(LINE_NUMBER_REGEX, LINE_NUMBER_PLACEHOLDER);
    assertWithMessage("Actual: \n%s\nExpected:\n%s\n", actual, expected)
        .that(actualWithoutLineNumber)
        .isEqualTo(expectedWithoutLineNumber);
  }

  private static String getExpectedError(String fileName) throws IOException {
    return Resources.toString(
        ErrorMessageTestUtils.class.getResource("testdata/" + fileName), UTF_8);
  }
}
