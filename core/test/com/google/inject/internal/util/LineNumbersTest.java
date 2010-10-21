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

package com.google.inject.internal.util;

import com.google.inject.AbstractModule;
import static com.google.inject.Asserts.assertContains;
import com.google.inject.CreationException;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.matcher.Matchers;
import junit.framework.TestCase;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class LineNumbersTest extends TestCase {

  public void testLineNumbers() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bind(A.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + B.class.getName() + " was bound.",
          "for parameter 0 at " + A.class.getName() + ".<init>(LineNumbersTest.java:",
          "at " + LineNumbersTest.class.getName(), ".configure(LineNumbersTest.java:");
    }
  }

  /*if[AOP]*/
  public void testCanHandleLineNumbersForGuiceGeneratedClasses() {
    try {
      Guice.createInjector(new AbstractModule() {
        protected void configure() {
          bindInterceptor(Matchers.only(A.class), Matchers.any(),
              new org.aopalliance.intercept.MethodInterceptor() {
                public Object invoke(org.aopalliance.intercept.MethodInvocation methodInvocation) {
                  return null;
                }
              });

          bind(A.class);
        }
      });
      fail();
    } catch (CreationException expected) {
      assertContains(expected.getMessage(),
          "1) No implementation for " + B.class.getName() + " was bound.",
          "for parameter 0 at " + A.class.getName() + ".<init>(LineNumbersTest.java:",
          "at " + LineNumbersTest.class.getName(), ".configure(LineNumbersTest.java:");
    }
  }
  /*end[AOP]*/

  static class A {
    @Inject A(B b) {}
  }
  interface B {}

}
