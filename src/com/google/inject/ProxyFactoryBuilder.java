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
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Creates a {@link ProxyFactory}.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ProxyFactoryBuilder {

  final List<MethodAspect> methodAspects = new ArrayList<MethodAspect>();

  /**
   * Applies the given method interceptor to the methods matched by the class
   * and method matchers.
   *
   * @param classMatcher matches classes the interceptor should apply to. For
   *     example: {@code only(Runnable.class)}.
   * @param methodMatcher matches methods the interceptor should apply to. For
   *     example: {@code annotatedWith(Transactional.class)}.
   * @param interceptors to apply
   */
  public ProxyFactoryBuilder intercept(Matcher<? super Class<?>> classMatcher,
      Matcher<? super Method> methodMatcher,
      MethodInterceptor... interceptors) {
    methodAspects.add(
        new MethodAspect(classMatcher, methodMatcher, interceptors));
    return this;
  }

  /**
   * Creates a {@code ProxyFactory}.
   */
  public ProxyFactory create() {
    return new ProxyFactory(new ArrayList<MethodAspect>(methodAspects));
  }
}
