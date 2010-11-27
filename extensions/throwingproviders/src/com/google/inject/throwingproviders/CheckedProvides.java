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

package com.google.inject.throwingproviders;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * Annotates methods of a {@link Module} to create a {@link CheckedProvider}
 * method binding that can throw exceptions. The method's return type is bound
 * to a {@link CheckedProvider} that can be injected. Guice will pass
 * dependencies to the method as parameters. Install {@literal @}CheckedProvides
 * methods by using
 * {@link ThrowingProviderBinder#forModule(com.google.inject.Module)} on the
 * module where the methods are declared.
 * 
 * @author sameb@google.com (Sam Berlin)
 * @since 3.0
 */
@Documented @Target(METHOD) @Retention(RUNTIME)
public @interface CheckedProvides {
  
  /**
   * The interface that provides this value, a subinterface of {@link CheckedProvider}.
   */
  Class<? extends CheckedProvider> value();
  
}
