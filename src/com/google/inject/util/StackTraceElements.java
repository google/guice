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

package com.google.inject.util;

import java.lang.reflect.Method;
import java.lang.reflect.Member;
import java.util.Map;
import java.io.IOException;
import static com.google.inject.util.ReferenceType.*;

/**
 * Creates stack trace elements for members.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class StackTraceElements {

  static final Map<Class<?>, LineNumbers> lineNumbersCache
      = new ReferenceCache<Class<?>, LineNumbers>(WEAK, SOFT) {
    protected LineNumbers create(Class<?> key) {
      try {
        return new LineNumbers(key);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public static Object forMember(Member member) {
    if (member == null) {
      return "[unknown source]";  
    }

    Class declaringClass = member.getDeclaringClass();
    LineNumbers lineNumbers = lineNumbersCache.get(declaringClass);
    Integer lineNumber = lineNumbers.getLineNumber(member);
    StackTraceElement element = new StackTraceElement(
      declaringClass.getName(), member.getName(), lineNumbers.getSource(),
        lineNumber == null ? 0 : lineNumber);
    return element;
  }

  public static Object forType(Class<?> implementation) {
    LineNumbers lineNumbers = lineNumbersCache.get(implementation);
    return new StackTraceElement(
        implementation.getName(), "<init>", lineNumbers.getSource(), 0);
  }
}
