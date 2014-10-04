/**
 * Copyright (C) 2012 Google Inc.
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
