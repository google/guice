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


package com.google.inject.internal;

import com.google.inject.Inject;
import static com.google.inject.internal.Objects.nonNull;

import java.lang.reflect.Constructor;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class RuntimeReflectionFactory implements Reflection.Factory {
  public Reflection create(ErrorHandler errorHandler,
      ConstructionProxyFactory constructionProxyFactory) {
    return new RuntimeReflection(errorHandler, constructionProxyFactory);
  }

  private static class RuntimeReflection implements Reflection {
    private final ErrorHandler errorHandler;
    private final ConstructionProxyFactory constructionProxyFactory;

    private RuntimeReflection(ErrorHandler errorHandler,
        ConstructionProxyFactory constructionProxyFactory) {
      this.errorHandler = nonNull(errorHandler, "errorHandler");
      this.constructionProxyFactory = nonNull(constructionProxyFactory, "constructionProxyFatory");
    }

    public <T> ConstructionProxy<T> getConstructionProxy(Class<T> implementation) {
      return constructionProxyFactory.get(findConstructorIn(implementation));
    }

    private <T> Constructor<T> findConstructorIn(Class<T> implementation) {
      Constructor<T> found = null;
      @SuppressWarnings("unchecked")
      Constructor<T>[] constructors
          = (Constructor<T>[]) implementation.getDeclaredConstructors();
      for (Constructor<T> constructor : constructors) {
        Inject inject = constructor.getAnnotation(Inject.class);
        if (inject != null) {
          if (inject.optional()) {
            errorHandler.handle(
                StackTraceElements.forMember(constructor),
                ErrorMessages.OPTIONAL_CONSTRUCTOR);
          }

          if (found != null) {
            errorHandler.handle(
                StackTraceElements.forMember(found),
                ErrorMessages.TOO_MANY_CONSTRUCTORS);
            return invalidConstructor();
          }
          found = constructor;
        }
      }
      if (found != null) {
        return found;
      }

      // If no annotated constructor is found, look for a no-arg constructor
      // instead.
      try {
        return implementation.getDeclaredConstructor();
      }
      catch (NoSuchMethodException e) {
        errorHandler.handle(
            StackTraceElements.forMember(
                implementation.getDeclaredConstructors()[0]),
            ErrorMessages.MISSING_CONSTRUCTOR,
            implementation);
        return invalidConstructor();
      }
    }

    /**
     * A placeholder. This enables us to continue processing and gather more
     * errors but blows up if you actually try to use it.
     */
    static class InvalidConstructor {
      InvalidConstructor() {
        throw new AssertionError();
      }
    }

    @SuppressWarnings("unchecked")
    static <T> Constructor<T> invalidConstructor() {
      try {
        return (Constructor<T>) InvalidConstructor.class.getDeclaredConstructor();
      }
      catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
    }
  }
}
