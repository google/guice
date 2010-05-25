/**
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p> Any method or class marked with this annotation will be considered for transactionality.
 * Consult the documentation on http://www.wideplay.com for detailed semantics. <p> Marking a method
 * {@code @Transactional} will work with the default configuration as expected. Any classes marked
 * {@code @Transactional} will only work if you specify the
 *  {@code forAll(Matchers.annotatedWith(Transactional.class), Matchers.any()} clause in your
 * guice-persist module configuration. <p> Class level {@code
 *  * @Transactional} allows you to specify transaction semantics for all non-private methods in the
 * class once at the top. You can optionally override it on a per-method basis too. However, this
 * means that classes not marked {@code @Transactional} but with methods marked {@code
 * @Transactional} will *not* be intercepted for transaction wrapping.

 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Transactional {

  /**
   * A list of exceptions to rollback on, if thrown by the transactional method.
   * These exceptions are propagated correctly after a rollback.
   *
   * @return Returns the configured rollback exceptions.
   */
  Class<? extends Exception>[] rollbackOn() default RuntimeException.class;

  /**
   * A list of exceptions to *not* rollback on. A caveat to the rollbackOn clause.
   * The disjunction of rollbackOn and exceptOn represents the list of exceptions
   * that will trigger a rollback.
   * The complement of rollbackOn and the universal set plus any exceptions in the
   * exceptOn set represents the list of exceptions that will trigger a commit.
   * <p/>
   * Note that exceptOn exceptions take precedence over rollbackOn, but with subtype
   * granularity.
   *
   * @return Returns the configured rollback exceptions.
   */
  Class<? extends Exception>[] exceptOn() default { };
}
