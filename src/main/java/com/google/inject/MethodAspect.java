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

import com.google.inject.matcher.Matcher;
import com.google.inject.util.Objects;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Ties a matcher to a method interceptor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class MethodAspect {

  final Matcher<? super Class<?>> classMatcher;
  final Matcher<? super Method> methodMatcher;
  final List<MethodInterceptor> interceptors;

  MethodAspect(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    this.classMatcher = Objects.nonNull(classMatcher, "class matcher");
    this.methodMatcher = Objects.nonNull(methodMatcher, "method matcher");
    this.interceptors
        = Arrays.asList(Objects.nonNull(interceptors, "interceptors"));
  }

  boolean matches(Class<?> clazz) {
    return classMatcher.matches(clazz);
  }

  boolean matches(Method method) {
    return methodMatcher.matches(method);
  }

  List<MethodInterceptor> interceptors() {
    return interceptors;
  }
}
