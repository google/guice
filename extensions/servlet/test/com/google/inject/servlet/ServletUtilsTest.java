// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

/**
 * Unit test for the servlet utility class.
 *
 * @author ntang@google.com (Michael Tang)
 */
public class ServletUtilsTest {
  @Test
  public void testGetContextRelativePath() {
    assertEquals(
        "/test.html", getContextRelativePath("/a_context_path", "/a_context_path/test.html"));
    assertEquals("/test.html", getContextRelativePath("", "/test.html"));
    assertEquals("/test.html", getContextRelativePath("", "/foo/../test.html"));
    assertEquals("/test.html", getContextRelativePath("", "/././foo/../test.html"));
    assertEquals("/test.html", getContextRelativePath("", "/foo/../../../../test.html"));
    assertEquals("/test.html", getContextRelativePath("", "/foo/%2E%2E/test.html"));
    // %2E == '.'
    assertEquals("/test.html", getContextRelativePath("", "/foo/%2E%2E/test.html"));
    // %2F == '/'
    assertEquals("/foo/%2F/test.html", getContextRelativePath("", "/foo/%2F/test.html"));
    // %66 == 'f'
    assertEquals("/foo.html", getContextRelativePath("", "/%66oo.html"));
  }

  @Test
  public void testGetContextRelativePath_preserveQuery() {
    assertEquals("/foo?q=f", getContextRelativePath("", "/foo?q=f"));
    assertEquals("/foo?q=%20+%20", getContextRelativePath("", "/foo?q=%20+%20"));
  }

  @Test
  public void testGetContextRelativePathWithWrongPath() {
    assertNull(getContextRelativePath("/a_context_path", "/test.html"));
  }

  @Test
  public void testGetContextRelativePathWithRootPath() {
    assertEquals("/", getContextRelativePath("/a_context_path", "/a_context_path"));
  }

  @Test
  public void testGetContextRelativePathWithEmptyPath() {
    assertNull(getContextRelativePath("", ""));
  }

  @Test
  public void testNormalizePath() {
    assertEquals("foobar", ServletUtils.normalizePath("foobar"));
    assertEquals("foo+bar", ServletUtils.normalizePath("foo+bar"));
    assertEquals("foo%20bar", ServletUtils.normalizePath("foo bar"));
    assertEquals("foo%25-bar", ServletUtils.normalizePath("foo%-bar"));
    assertEquals("foo%25+bar", ServletUtils.normalizePath("foo%+bar"));
    assertEquals("foo%25-0bar", ServletUtils.normalizePath("foo%-0bar"));
  }

  private String getContextRelativePath(String contextPath, String requestPath) {
    HttpServletRequest mock = mock(HttpServletRequest.class);
    when(mock.getContextPath()).thenReturn(contextPath);
    when(mock.getRequestURI()).thenReturn(requestPath);

    String contextRelativePath = ServletUtils.getContextRelativePath(mock);
    return contextRelativePath;
  }
}
