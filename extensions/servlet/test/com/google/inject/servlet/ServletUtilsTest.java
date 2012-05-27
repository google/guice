// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.servlet;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import junit.framework.TestCase;

import javax.servlet.http.HttpServletRequest;

/**
 * Unit test for the servlet utility class.
 *
 * @author ntang@google.com (Michael Tang)
 */
public class ServletUtilsTest extends TestCase {
  public void testGetContextRelativePath() {
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/a_context_path/test.html");
    replay(servletRequest);
    String path = ServletUtils.getContextRelativePath(servletRequest);
    assertEquals("/test.html", path);
    verify(servletRequest);
  }

  public void testGetContextRelativePathWithWrongPath() {
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/test.html");
    replay(servletRequest);
    String path = ServletUtils.getContextRelativePath(servletRequest);
    assertNull(path);
    verify(servletRequest);
  }

  public void testGetContextRelativePathWithRootPath() {
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    expect(servletRequest.getContextPath()).andReturn("/a_context_path");
    expect(servletRequest.getRequestURI()).andReturn("/a_context_path");
    replay(servletRequest);
    String path = ServletUtils.getContextRelativePath(servletRequest);
    assertEquals("/", path);
    verify(servletRequest);
  }

  public void testGetContextRelativePathWithEmptyPath() {
    HttpServletRequest servletRequest = createMock(HttpServletRequest.class);
    expect(servletRequest.getContextPath()).andReturn("");
    expect(servletRequest.getRequestURI()).andReturn("");
    replay(servletRequest);
    String path = ServletUtils.getContextRelativePath(servletRequest);
    assertNull(path);
    verify(servletRequest);
  }
}
