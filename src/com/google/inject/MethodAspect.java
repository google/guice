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

import com.google.inject.util.Objects;
import com.google.inject.query.Query;

import org.aopalliance.intercept.MethodInterceptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Arrays;

/**
 * Ties a query to a method interceptor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class MethodAspect {

  final Query<? super Class<?>> classQuery;
  final Query<? super Method> methodQuery;
  final List<MethodInterceptor> interceptors;

  MethodAspect(Query<? super Class<?>> classQuery, Query<? super Method> methodQuery,
      MethodInterceptor... interceptors) {
    this.classQuery = Objects.nonNull(classQuery, "class query");
    this.methodQuery = Objects.nonNull(methodQuery, "method query");
    this.interceptors =
        Arrays.asList(Objects.nonNull(interceptors, "interceptors"));
  }

  boolean matches(Class<?> clazz) {
    return classQuery.matches(clazz);
  }

  boolean matches(Method method) {
    return methodQuery.matches(method);
  }

  List<MethodInterceptor> interceptors() {
    return interceptors;
  }
}
