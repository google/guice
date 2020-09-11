package com.google.inject.persist.jpa;

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import com.google.inject.Inject;
import com.google.inject.persist.RequiresUnitOfWork;

public class RequiresUnitOfWorkInterceptor implements MethodInterceptor {
  @Inject
  private UnitOfWorkHandler unitOfWorkHandler;

  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    RequiresUnitOfWork annotation = readAnnotationMetadata(methodInvocation);
    if (annotation == null) {
      // Avoid creating the unit of work in Object class methods
      return methodInvocation.proceed();
      
    } else {
      unitOfWorkHandler.requireUnitOfWork();
      try {
        return methodInvocation.proceed();
      } finally {
        unitOfWorkHandler.endRequireUnitOfWork();
      }
    }
  }

  private RequiresUnitOfWork readAnnotationMetadata(MethodInvocation methodInvocation) {
    RequiresUnitOfWork annotation;
    Method method = methodInvocation.getMethod();

    // Annotation in method or class
    annotation = method.getAnnotation(RequiresUnitOfWork.class);
    if (annotation == null) {
      annotation = method.getClass().getAnnotation(RequiresUnitOfWork.class);
    }

    return annotation;
  }
}
