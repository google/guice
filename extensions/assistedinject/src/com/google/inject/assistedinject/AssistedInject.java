/**
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.assistedinject;

import static java.lang.annotation.ElementType.CONSTRUCTOR;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Target;

import com.google.inject.Inject;

/**
 * <p>
 * When used in tandem with {@link FactoryModuleBuilder}, constructors annotated with 
 * {@code @AssistedInject} indicate that multiple constructors can be injected, each with different
 * parameters. AssistedInject annotations should not be mixed with {@literal @}{@link Inject}
 * annotations. The assisted parameters must exactly match one corresponding factory method within
 * the factory interface, but the parameters do not need to be in the same order. Constructors
 * annotated with AssistedInject <b>are</b> created by Guice and receive all the benefits
 * (such as AOP).
 * 
 * <p>
 * <strong>Obsolete Usage:</strong> When used in tandem with {@link FactoryProvider}, constructors
 * annotated with {@code @AssistedInject} trigger a "backwards compatibility mode". The assisted
 * parameters must exactly match one corresponding factory method within the factory interface and
 * all must be in the same order as listed in the factory. In this backwards compatable mode,
 * constructors annotated with AssistedInject <b>are not</b> created by Guice and thus receive
 * none of the benefits.
 * 
 * <p>
 * Constructor parameters must be either supplied by the factory interface and marked with
 * <code>@Assisted</code>, or they must be injectable.
 * 
 * @author jmourits@google.com (Jerome Mourits)
 * @author jessewilson@google.com (Jesse Wilson)
 */
@Target( { CONSTRUCTOR })
@Retention(RUNTIME)
public @interface AssistedInject {
}
