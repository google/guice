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


package com.google.inject;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.inject.internal.Errors;
import com.google.inject.internal.ErrorsException;
import com.google.inject.spi.InjectionPoint;
import java.lang.reflect.Constructor;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 */
class RuntimeReflectionFactory implements Reflection.Factory {
  public Reflection create(ConstructionProxyFactory constructionProxyFactory) {
    return new RuntimeReflection(constructionProxyFactory);
  }

  private static class RuntimeReflection implements Reflection {
    private final ConstructionProxyFactory constructionProxyFactory;

    private RuntimeReflection(ConstructionProxyFactory constructionProxyFactory) {
      this.constructionProxyFactory
          = checkNotNull(constructionProxyFactory, "constructionProxyFatory");
    }

    public <T> ConstructionProxy<T> getConstructionProxy(Errors errors, Class<T> implementation)
        throws ErrorsException {
      InjectionPoint injectionPoint;
      try {
        injectionPoint = InjectionPoint.forConstructorOf(implementation);
      } catch (ConfigurationException e) {
        throw errors.merge(e.getErrorMessages()).toException();
      }

      errors = errors.withSource(injectionPoint);
      return constructionProxyFactory.get(errors, injectionPoint);
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
