/**
 * Copyright (C) 2008 Google Inc.
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

/**
 * An enumeration of the available URI-pattern matching styles
 */
enum UriPatternType {
  SERVLET, REGEX;

  public static UriPatternMatcher get(UriPatternType type) {
    switch (type) {
      case SERVLET:
        return new ServletStyleUriPatternMatcher();
      case REGEX:
        return new RegexUriPatternMatcher();
      default:
        return null;
    }
  }

  /**
   * Matchers URIs using the pattern grammar of the Servlet API and web.xml.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class ServletStyleUriPatternMatcher implements UriPatternMatcher {
    public boolean matches(String uri, String pattern) {
      if (null == uri) {
        return false;
      }

      if (pattern.startsWith("*")) {
        return uri.endsWith(pattern.substring(1));
      }
      else if (pattern.endsWith("*")) {
        return uri.startsWith(pattern.substring(0, pattern.length() - 1));
      }

      //else treat as a literal
      return pattern.equals(uri);
    }

    public String extractPath(String pattern) {
      if (pattern.startsWith("*")) {
        return null;
      } else if (pattern.endsWith("*")) {
        String extract = pattern.substring(0, pattern.length() - 1);

        //trim the trailing '/'
        if (extract.endsWith("/")) {
          extract = extract.substring(0, extract.length() - 1);
        }

        return extract;
      }

      //else treat as literal
      return pattern;
    }
  }

  /**
   * Matchers URIs using a regular expression.
   * NOTE(dhanji) No path info is available when using regex mapping.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class RegexUriPatternMatcher implements UriPatternMatcher {
    public boolean matches(String uri, String pattern) {
      return null != uri && uri.matches(pattern);
    }

    public String extractPath(String pattern) {
      return null;
    }
  }
}
