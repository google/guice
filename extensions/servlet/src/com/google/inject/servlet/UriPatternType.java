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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An enumeration of the available URI-pattern matching styles
 * 
 * @since 3.0
 */
public enum UriPatternType {
  SERVLET, REGEX;

  static UriPatternMatcher get(UriPatternType type, String pattern) {
    switch (type) {
      case SERVLET:
        return new ServletStyleUriPatternMatcher(pattern);
      case REGEX:
        return new RegexUriPatternMatcher(pattern);
      default:
        return null;
    }
  }

  /**
   * Matches URIs using the pattern grammar of the Servlet API and web.xml.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class ServletStyleUriPatternMatcher implements UriPatternMatcher {
    private final String pattern;
    private final Kind patternKind;

    private static enum Kind { PREFIX, SUFFIX, LITERAL, }

    public ServletStyleUriPatternMatcher(String pattern) {
      if (pattern.startsWith("*")) {
        this.pattern = pattern.substring(1);
        this.patternKind = Kind.PREFIX;
      } else if (pattern.endsWith("*")) {
        this.pattern = pattern.substring(0, pattern.length() - 1);
        this.patternKind = Kind.SUFFIX;
      } else {
        this.pattern = pattern;
        this.patternKind = Kind.LITERAL;
      }
    }

    public boolean matches(String uri) {
      if (null == uri) {
        return false;
      }

      if (patternKind == Kind.PREFIX) {
        return uri.endsWith(pattern);
      } else if (patternKind == Kind.SUFFIX) {
        return uri.startsWith(pattern);
      }

      //else treat as a literal
      return pattern.equals(uri);
    }

    public String extractPath(String path) {
      if (patternKind == Kind.PREFIX) {
        return null;
      } else if (patternKind == Kind.SUFFIX) {
        String extract = pattern;

        //trim the trailing '/'
        if (extract.endsWith("/")) {
          extract = extract.substring(0, extract.length() - 1);
        }

        return extract;
      }

      //else treat as literal
      return path;
    }
    
    public UriPatternType getPatternType() {
      return UriPatternType.SERVLET;
    }
  }

  /**
   * Matches URIs using a regular expression.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class RegexUriPatternMatcher implements UriPatternMatcher {
    private final Pattern pattern;

    public RegexUriPatternMatcher(String pattern) {
      this.pattern = Pattern.compile(pattern);
    }

    public boolean matches(String uri) {
      return null != uri && this.pattern.matcher(uri).matches();
    }

    public String extractPath(String path) {
      Matcher matcher = pattern.matcher(path);
      if (matcher.matches() && matcher.groupCount() >= 1) {

        // Try to capture the everything before the regex begins to match
        // the path. This is a rough approximation to try and get parity
        // with the servlet style mapping where the path is a capture of
        // the URI before the wildcard.
        int end = matcher.start(1);
        if (end < path.length()) {
          return path.substring(0, end);
        }
      }
      return null;
    }
    
    public UriPatternType getPatternType() {
      return UriPatternType.REGEX;
    }
  }
}
