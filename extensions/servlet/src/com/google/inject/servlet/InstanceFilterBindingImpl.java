/*
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

import com.google.common.base.MoreObjects;
import java.util.Map;
import javax.servlet.Filter;

/**
 * Default implementation of InstanceFilterBinding.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class InstanceFilterBindingImpl extends AbstractServletModuleBinding<Filter>
    implements InstanceFilterBinding {

  InstanceFilterBindingImpl(
      Map<String, String> initParams, Filter target, UriPatternMatcher patternMatcher) {
    super(initParams, target, patternMatcher);
  }

  @Override
  public Filter getFilterInstance() {
    return getTarget();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(InstanceFilterBinding.class)
        .add("pattern", getPattern())
        .add("initParams", getInitParams())
        .add("uriPatternType", getUriPatternType())
        .add("filterInstance", getFilterInstance())
        .toString();
  }
}
