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

import com.google.inject.commands.BindInterceptorCommand;
import com.google.inject.internal.ErrorHandler;

/**
 * Handles {@link Binder#bindInterceptor} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
class BindInterceptorCommandProcessor extends CommandProcessor {

  private final ProxyFactoryBuilder proxyFactoryBuilder;

  BindInterceptorCommandProcessor(ErrorHandler errorHandler) {
    super(errorHandler);
    proxyFactoryBuilder = new ProxyFactoryBuilder(errorHandler);
  }

  @Override public Boolean visitBindInterceptor(BindInterceptorCommand command) {
    proxyFactoryBuilder.intercept(
        command.getClassMatcher(), command.getMethodMatcher(), command.getInterceptors());
    return true;
  }

  ProxyFactory createProxyFactory() {
    return proxyFactoryBuilder.create();
  }
}
