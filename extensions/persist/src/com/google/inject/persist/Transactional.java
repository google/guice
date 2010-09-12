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
 * Consult the documentation on http://code.google.com/p/google-guice for detailed semantics.
 * Marking a method {@code @Transactional} will start a new transaction before the method
 * executes and commit it after the method returns.
 * <p>
 * If the method throws an exception, the transaction will be rolled back <em>unless</em>
 * you have specifically requested not to in the {@link #ignore()} clause.
 * <p>
 * Similarly, the set of exceptions that will trigger a rollback can be defined in
 * the {@link #rollbackOn()} clause. By default, only unchecked exceptions trigger a
 * rollback.
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
   */
  Class<? extends Exception>[] rollbackOn() default RuntimeException.class;

  /**
   * A list of exceptions to <b>not<b> rollback on. A caveat to the rollbackOn clause.
   * The disjunction of rollbackOn and ignore represents the list of exceptions
   * that will trigger a rollback.
   * The complement of rollbackOn and the universal set plus any exceptions in the
   * ignore set represents the list of exceptions that will trigger a commit.
   * Note that ignore exceptions take precedence over rollbackOn, but with subtype
   * granularity.
   */
  Class<? extends Exception>[] ignore() default { };
}
