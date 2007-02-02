// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.intercept;

import com.google.inject.util.Objects;

import org.aopalliance.intercept.MethodInterceptor;

import java.lang.reflect.Method;

/**
 * Ties a query to a method interceptor.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class MethodAspect {

  final Query<? super Class> classQuery;
  final Query<? super Method> methodQuery;
  final MethodInterceptor interceptor;

  MethodAspect(Query<? super Class> classQuery, Query<? super Method> methodQuery,
      MethodInterceptor interceptor) {
    this.classQuery = Objects.nonNull(classQuery, "class query");
    this.methodQuery = Objects.nonNull(methodQuery, "method query");
    this.interceptor = Objects.nonNull(interceptor, "interceptor");
  }

  boolean matches(Class<?> clazz) {
    return classQuery.matches(clazz);
  }

  boolean matches(Method method) {
    return methodQuery.matches(method);
  }

  MethodInterceptor interceptor() {
    return interceptor;
  }
}
