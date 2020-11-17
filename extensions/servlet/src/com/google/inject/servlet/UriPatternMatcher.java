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

/**
 * A general interface for matching a URI against a URI pattern. Guice-servlet provides regex and
 * servlet-style pattern matching out of the box.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
interface UriPatternMatcher {
  /**
   * @param uri A "contextual" (i.e. relative) and "normalized" Request URI, *not* a complete one.
   * @return Returns true if the uri matches the pattern.
   */
  boolean matches(String uri);

  /**
   * @param pattern The Path that this service pattern can match against.
   * @return Returns a canonical servlet path from this pattern. For instance, if the pattern is
   *     {@code /home/*} then the path extracted will be {@code /home}. Each pattern matcher
   *     implementation must decide and publish what a canonical path represents.
   *     <p>NOTE(user): This method returns null for the regex pattern matcher.
   */
  String extractPath(String pattern);

  /** Returns the type of pattern this is. */
  UriPatternType getPatternType();

  /** Returns the original pattern that was registered. */
  String getOriginalPattern();
}
