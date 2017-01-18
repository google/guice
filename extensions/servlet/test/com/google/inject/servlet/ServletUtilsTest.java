// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.servlet;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import javax.servlet.http.HttpServletRequest;
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

  public void testGetContextRelativePathWithWrongPath() {
    assertNull(getContextRelativePath("/a_context_path", "/test.html"));
  }

  public void testGetContextRelativePathWithRootPath() {
    assertEquals("/", getContextRelativePath("/a_context_path", "/a_context_path"));
  }

  public void testGetContextRelativePathWithEmptyPath() {
    assertNull(getContextRelativePath("", ""));
  }

  private String getContextRelativePath(String contextPath, String requestPath) {
    HttpServletRequest mock = createMock(HttpServletRequest.class);
    expect(mock.getContextPath()).andReturn(contextPath);
    expect(mock.getRequestURI()).andReturn(requestPath);
    replay(mock);
    String contextRelativePath = ServletUtils.getContextRelativePath(mock);
    verify(mock);
    return contextRelativePath;
  }
}
