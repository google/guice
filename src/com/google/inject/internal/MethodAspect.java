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

package com.google.inject.internal;

import static com.google.inject.internal.util.Preconditions.checkNotNull;
import com.google.inject.matcher.Matcher;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Ties a matcher to a method interceptor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
final class MethodAspect {

  private final Matcher<? super Class<?>> classMatcher;
  private final Matcher<? super Method> methodMatcher;
  private final List<MethodInterceptor> interceptors;

  /**
   * @param classMatcher matches classes the interceptor should apply to. For example: {@code
   *     only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For example: {@code
   *     annotatedWith(Transactional.class)}.
   * @param interceptors to apply
   */
  MethodAspect(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, List<MethodInterceptor> interceptors) {
    this.classMatcher = checkNotNull(classMatcher, "class matcher");
    this.methodMatcher = checkNotNull(methodMatcher, "method matcher");
    this.interceptors = checkNotNull(interceptors, "interceptors");
  }

  MethodAspect(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher, MethodInterceptor... interceptors) {
    this(classMatcher, methodMatcher, Arrays.asList(interceptors));
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
