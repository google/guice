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

package com.google.inject.internal.util;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.Map;

/**
 * Creates stack trace elements for members.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class StackTraceElements {

  /*if[AOP]*/
  static final Map<Class<?>, LineNumbers> lineNumbersCache = new MapMaker().weakKeys().softValues()
      .makeComputingMap(new Function<Class<?>, LineNumbers>() {
        public LineNumbers apply(Class<?> key) {
          try {
            return new LineNumbers(key);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
  /*end[AOP]*/

  public static Object forMember(Member member) {
    if (member == null) {
      return SourceProvider.UNKNOWN_SOURCE;
    }

    Class declaringClass = member.getDeclaringClass();

    /*if[AOP]*/
    LineNumbers lineNumbers = lineNumbersCache.get(declaringClass);
    String fileName = lineNumbers.getSource();
    Integer lineNumberOrNull = lineNumbers.getLineNumber(member);
    int lineNumber = lineNumberOrNull == null ? lineNumbers.getFirstLine() : lineNumberOrNull;
    /*end[AOP]*/
    /*if[NO_AOP]
    String fileName = null;
    int lineNumber = -1;
    end[NO_AOP]*/

    Class<? extends Member> memberType = Classes.memberType(member);
    String memberName = memberType == Constructor.class ? "<init>" : member.getName();
    return new StackTraceElement(declaringClass.getName(), memberName, fileName, lineNumber);
  }

  public static Object forType(Class<?> implementation) {
    /*if[AOP]*/
    LineNumbers lineNumbers = lineNumbersCache.get(implementation);
    int lineNumber = lineNumbers.getFirstLine();
    String fileName = lineNumbers.getSource();
    /*end[AOP]*/
    /*if[NO_AOP]
    String fileName = null;
    int lineNumber = -1;
    end[NO_AOP]*/

    return new StackTraceElement(implementation.getName(), "class", fileName, lineNumber);
  }
}
