// Copyright 2006 Google Inc. All Rights Reserved.

package com.google.inject.spi;

import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Default {@link ConstructionProxyFactory} implementation. Simply invokes the
 * constructor. Can be reused by other {@code ConstructionProxyFactory}
 * implementations.
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class DefaultConstructionProxyFactory
    implements ConstructionProxyFactory {

  public <T> ConstructionProxy<T> get(Constructor<T> constructor) {
    FastClass fastClass = FastClass.create(constructor.getDeclaringClass());
    final FastConstructor fastConstructor =
        fastClass.getConstructor(constructor);
    return new ConstructionProxy<T>() {
      @SuppressWarnings({"unchecked"})
      public T newInstance(Object... arguments)
          throws InvocationTargetException {
        return (T) fastConstructor.newInstance(arguments);
      }
    };
  }
}
