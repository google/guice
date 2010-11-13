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

import javax.servlet.Filter;

import com.google.inject.Key;
import com.google.inject.internal.util.ToStringBuilder;

/**
 * Default implementation of LinkedFilterBinding.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class LinkedFilterBindingImpl extends AbstractServletModuleBinding<Key<? extends Filter>>
    implements LinkedFilterBinding {

  LinkedFilterBindingImpl(Map<String, String> initParams, String pattern,
      Key<? extends Filter> target, UriPatternMatcher patternMatcher) {
    super(initParams, pattern, target, patternMatcher);
  }

  public Key<? extends Filter> getLinkedKey() {
    return getTarget();
  }
  
  @Override public String toString() {
    return new ToStringBuilder(LinkedFilterBinding.class)
      .add("pattern", getPattern())
      .add("initParams", getInitParams())
      .add("uriPatternType", getUriPatternType())
      .add("linkedFilterKey", getLinkedKey())
      .toString();
  }
  
}
