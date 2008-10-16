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

package com.google.inject;

import com.google.inject.internal.ProviderMethodsModule;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Deprecated
public class ProviderMethods {

  /**
   * @deprecated provider methods are automatically discovered on Modules. Ensure the argument
   *      implements Module, and install that. If you're installing the provider methods for the
   *      current module, you can remove the install() call completely - it does nothing.
   */
  @Deprecated
  public static Module from(Object hasProviderMethods) {
    return ProviderMethodsModule.forModule(hasProviderMethods);
  }
}
