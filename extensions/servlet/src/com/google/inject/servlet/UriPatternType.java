/*
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
import java.util.regex.PatternSyntaxException;

/**
 * An enumeration of the available URI-pattern matching styles
 *
 * @since 3.0
 */
public enum UriPatternType {
  SERVLET,
  REGEX;

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

  private static String getUri(String uri) {
    // Strip out the query, if it existed in the URI.  See issue 379.
    int queryIdx = uri.indexOf('?');
    if (queryIdx != -1) {
      uri = uri.substring(0, queryIdx);
    }
    return uri;
  }

  /**
   * Matches URIs using the pattern grammar of the Servlet API and web.xml.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class ServletStyleUriPatternMatcher implements UriPatternMatcher {
    private final String literal;
    private final String originalPattern;
    private final Kind patternKind;

    private static enum Kind {
      PREFIX,
      SUFFIX,
      LITERAL,
    }

    public ServletStyleUriPatternMatcher(String pattern) {
      this.originalPattern = pattern;
      if (pattern.startsWith("*")) {
        this.literal = pattern.substring(1);
        this.patternKind = Kind.PREFIX;
      } else if (pattern.endsWith("*")) {
        this.literal = pattern.substring(0, pattern.length() - 1);
        this.patternKind = Kind.SUFFIX;
      } else {
        this.literal = pattern;
        this.patternKind = Kind.LITERAL;
      }
      String normalized = ServletUtils.normalizePath(literal);
      if (patternKind == Kind.PREFIX) {
        normalized = "*" + normalized;
      } else if (patternKind == Kind.SUFFIX) {
        normalized = normalized + "*";
      }
      if (!pattern.equals(normalized)) {
        throw new IllegalArgumentException(
            "Servlet patterns cannot contain escape patterns. Registered pattern: '"
                + pattern
                + "' normalizes to: '"
                + normalized
                + "'");
      }
    }

    @Override
    public boolean matches(String uri) {
      if (null == uri) {
        return false;
      }

      uri = getUri(uri);
      if (patternKind == Kind.PREFIX) {
        return uri.endsWith(literal);
      } else if (patternKind == Kind.SUFFIX) {
        return uri.startsWith(literal);
      }

      //else we need a complete match
      return literal.equals(uri);
    }

    @Override
    public String extractPath(String path) {
      if (patternKind == Kind.PREFIX) {
        return null;
      } else if (patternKind == Kind.SUFFIX) {
        String extract = literal;

        //trim the trailing '/'
        if (extract.endsWith("/")) {
          extract = extract.substring(0, extract.length() - 1);
        }

        return extract;
      }

      //else treat as literal
      return path;
    }

    @Override
    public UriPatternType getPatternType() {
      return UriPatternType.SERVLET;
    }

    @Override
    public String getOriginalPattern() {
      return originalPattern;
    }
  }

  /**
   * Matches URIs using a regular expression.
   *
   * @author dhanji@gmail.com (Dhanji R. Prasanna)
   */
  private static class RegexUriPatternMatcher implements UriPatternMatcher {
    private final Pattern pattern;
    private final String originalPattern;

    public RegexUriPatternMatcher(String pattern) {
      this.originalPattern = pattern;
      try {
        this.pattern = Pattern.compile(pattern);
      } catch (PatternSyntaxException pse) {
        throw new IllegalArgumentException("Invalid regex pattern: " + pse.getMessage());
      }
    }

    @Override
    public boolean matches(String uri) {
      return null != uri && this.pattern.matcher(getUri(uri)).matches();
    }

    @Override
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

    @Override
    public UriPatternType getPatternType() {
      return UriPatternType.REGEX;
    }

    @Override
    public String getOriginalPattern() {
      return originalPattern;
    }
  }
}
