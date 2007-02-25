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
 * The stage we're running in.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public enum Stage {

  /**
   * We want fast startup times and better error reporting at the expense of
   * runtime performance and some up front error checking.
   */
  DEVELOPMENT,

  /**
   * We want to catch errors as early as possible and take performance hits
   * up front.
   */
  PRODUCTION
}
