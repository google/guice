/**
 * Copyright (C) 2010 Google Inc.
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

import java.util.Map;

/**
 * Abstract implementation for all servlet module bindings
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class AbstractServletModuleBinding<T> implements ServletModuleBinding {
  
  private final Map<String, String> initParams;
  private final String pattern;
  private final T target;
  private final UriPatternMatcher patternMatcher;

  AbstractServletModuleBinding(Map<String, String> initParams, String pattern, T target,
      UriPatternMatcher patternMatcher) {
    this.initParams = initParams;
    this.pattern = pattern;
    this.target = target;
    this.patternMatcher = patternMatcher;
  }

  public Map<String, String> getInitParams() {
    return initParams;
  }

  public String getPattern() {
    return pattern;
  }

  protected T getTarget() {
    return target;
  }

  public UriPatternType getUriPatternType() {
    return patternMatcher.getPatternType();
  }
  
  public boolean matchesUri(String uri) {
    return patternMatcher.matches(uri);
  }

}
