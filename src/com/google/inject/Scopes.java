/**
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject;

/**
 * Scope constants.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class Scopes {

  /**
   * Default scope's name. One instance per injection.
   */
  public static final String DEFAULT_SCOPE = Key.DEFAULT_NAME;

  /**
   * Container scope's name. One instance per {@link Container}.
   */
  public static final String CONTAINER_SCOPE = "container";
}