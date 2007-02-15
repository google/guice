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

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

/**
 * <p>Annotates members which should have their value[s] injected.
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Target({ METHOD, CONSTRUCTOR, FIELD })
@Retention(RUNTIME)
public @interface Inject {

  /**
   * Indicates whether injection at the target is optional or not. The default
   * is {@code false}. Can be used on methods and fields. If a method has
   * multiple parameters and one parameter binding is missing, the method
   * won't be invoked at all. Not applicable to constructors or other
   * annotations.
   */
  boolean optional() default false;
}
