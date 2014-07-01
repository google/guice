/**
 * Copyright (C) 2014 Google Inc.
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

import junit.framework.TestCase;

public class UriPatternTypeTest extends TestCase {

  public void testMatches_servlet() {
    UriPatternMatcher pattern = UriPatternType.get(UriPatternType.SERVLET, "/foo/*");
    assertTrue(pattern.matches("/foo/asdf"));
    assertTrue(pattern.matches("/foo/asdf?val=1"));
    assertFalse(pattern.matches("/path/file.bar"));
    assertFalse(pattern.matches("/path/file.bar?val=1"));
    assertFalse(pattern.matches("/asdf"));
    assertFalse(pattern.matches("/asdf?val=1"));

    pattern = UriPatternType.get(UriPatternType.SERVLET, "*.bar");
    assertFalse(pattern.matches("/foo/asdf"));
    assertFalse(pattern.matches("/foo/asdf?val=1"));
    assertTrue(pattern.matches("/path/file.bar"));
    assertTrue(pattern.matches("/path/file.bar?val=1"));
    assertFalse(pattern.matches("/asdf"));
    assertFalse(pattern.matches("/asdf?val=1"));

    pattern = UriPatternType.get(UriPatternType.SERVLET, "/asdf");
    assertFalse(pattern.matches("/foo/asdf"));
    assertFalse(pattern.matches("/foo/asdf?val=1"));
    assertFalse(pattern.matches("/path/file.bar"));
    assertFalse(pattern.matches("/path/file.bar?val=1"));
    assertTrue(pattern.matches("/asdf"));
    assertTrue(pattern.matches("/asdf?val=1"));
  }

  public void testMatches_regex() {
    UriPatternMatcher pattern = UriPatternType.get(UriPatternType.REGEX, "/.*/foo");
    assertFalse(pattern.matches("/foo/asdf"));
    assertFalse(pattern.matches("/foo/asdf?val=1"));
    assertTrue(pattern.matches("/path/foo"));
    assertTrue(pattern.matches("/path/foo?val=1"));
    assertFalse(pattern.matches("/foo"));
    assertFalse(pattern.matches("/foo?val=1"));
  }
}
