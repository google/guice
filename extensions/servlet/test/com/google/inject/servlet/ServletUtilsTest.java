// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.servlet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import junit.framework.TestCase;

/**
 * Unit test for the servlet utility class.
 *
 * @author ntang@google.com (Michael Tang)
 */
public class ServletUtilsTest extends TestCase {
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

  public void testGetContextRelativePath_preserveQuery() {
    assertEquals("/foo?q=f", getContextRelativePath("", "/foo?q=f"));
    assertEquals("/foo?q=%20+%20", getContextRelativePath("", "/foo?q=%20+%20"));
  }

  public void testGetContextRelativePath_stripsPathParameters() {
    assertEquals("/admin/secret", getContextRelativePath("", "/admin;x=y/secret"));
    assertEquals("/admin/secret", getContextRelativePath("", "/admin;/secret"));
    assertEquals("/admin/secret", getContextRelativePath("", "/admin;a=b;c=d/secret;e=f"));
    assertEquals(
        "/admin/secret",
        getContextRelativePath("/a_context_path", "/a_context_path/admin;x/secret"));
    assertEquals("/foo.jsp", getContextRelativePath("", "/foo.jsp;jsessionid=1234"));
    // %3B == ';'
    assertEquals("/admin/secret", getContextRelativePath("", "/admin%3Bx=y/secret"));
    assertEquals("/admin/secret", getContextRelativePath("", "/admin%3bx=y/secret"));
    // Path parameters on dot-segments are stripped before the dot-segments are resolved.
    assertEquals("/admin/secret", getContextRelativePath("", "/foo/..;x=y/admin/secret"));
    assertEquals("/admin/secret", getContextRelativePath("", "/foo/%2E%2E;x=y/admin/secret"));
    assertEquals("/admin/secret", getContextRelativePath("", "/.;x=y/admin/secret"));
    // Query strings are preserved as is.
    assertEquals("/admin/secret?q=a;b", getContextRelativePath("", "/admin;x/secret?q=a;b"));
  }

  public void testGetContextRelativePathWithWrongPath() {
    assertNull(getContextRelativePath("/a_context_path", "/test.html"));
  }

  public void testGetContextRelativePathWithRootPath() {
    assertEquals("/", getContextRelativePath("/a_context_path", "/a_context_path"));
  }

  public void testGetContextRelativePathWithEmptyPath() {
    assertNull(getContextRelativePath("", ""));
  }

  public void testNormalizePath() {
    assertEquals("foobar", ServletUtils.normalizePath("foobar"));
    assertEquals("foo+bar", ServletUtils.normalizePath("foo+bar"));
    assertEquals("foo%20bar", ServletUtils.normalizePath("foo bar"));
    assertEquals("foo%25-bar", ServletUtils.normalizePath("foo%-bar"));
    assertEquals("foo%25+bar", ServletUtils.normalizePath("foo%+bar"));
    assertEquals("foo%25-0bar", ServletUtils.normalizePath("foo%-0bar"));
  }

  public void testRemoveDotSegments() {
    assertNull(ServletUtils.removeDotSegments(null));
    assertEquals("", ServletUtils.removeDotSegments(""));
    assertEquals("/", ServletUtils.removeDotSegments("/"));
    assertEquals("/foo", ServletUtils.removeDotSegments("/foo"));
    assertEquals("/foo/", ServletUtils.removeDotSegments("/foo/"));
    assertEquals("/foo/bar", ServletUtils.removeDotSegments("/foo//bar"));
    assertEquals("foo/bar", ServletUtils.removeDotSegments("foo/bar"));
    assertEquals("/.../h.thing", ServletUtils.removeDotSegments("/.../h.thing"));
    assertEquals("/..foo/bar..", ServletUtils.removeDotSegments("/..foo/bar.."));
    assertEquals("/foo/bar", ServletUtils.removeDotSegments("/foo/./bar"));
    assertEquals("/foo/", ServletUtils.removeDotSegments("/foo/."));
    assertEquals("/bar", ServletUtils.removeDotSegments("/foo/../bar"));
    assertEquals("/foo/", ServletUtils.removeDotSegments("/foo/bar/.."));
    assertEquals("/", ServletUtils.removeDotSegments("/foo/.."));
    assertEquals("/", ServletUtils.removeDotSegments("/.."));
    assertEquals("/secret", ServletUtils.removeDotSegments("/../../secret"));
    assertEquals("/secret", ServletUtils.removeDotSegments("/foo/../../../secret"));
    assertEquals("secret", ServletUtils.removeDotSegments("../secret"));
  }

  private String getContextRelativePath(String contextPath, String requestPath) {
    HttpServletRequest mock = mock(HttpServletRequest.class);
    when(mock.getContextPath()).thenReturn(contextPath);
    when(mock.getRequestURI()).thenReturn(requestPath);

    String contextRelativePath = ServletUtils.getContextRelativePath(mock);
    return contextRelativePath;
  }
}
