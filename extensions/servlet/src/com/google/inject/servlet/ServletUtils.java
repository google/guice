// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.inject.servlet;

import javax.servlet.http.HttpServletRequest;

/**
 * Some servlet utility methods.
 *
 * @author ntang@google.com (Michael Tang)
 */
final class ServletUtils {
  private ServletUtils() {
    // private to prevent instantiation.
  }

  /**
   * Gets the context path relative path of the URI. Returns the path of the
   * resource relative to the context path for a request's URI, or null if no
   * path can be extracted.
   */
  // @Nullable
  public static String getContextRelativePath(
      // @Nullable
      final HttpServletRequest request) {
    if (request != null) {
      String contextPath = request.getContextPath();
      String requestURI = request.getRequestURI();
      if (contextPath.length() < requestURI.length()) {
        return requestURI.substring(contextPath.length());
      } else if (requestURI != null && requestURI.trim().length() > 0 &&
          contextPath.length() == requestURI.length()) {
        return "/";
      }
    }
    return null;
  }
}
