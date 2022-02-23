/*
 * Copyright (C) 2010 Google, Inc.
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

package com.google.inject.persist.jpa;

import com.google.inject.name.Named;
import com.google.inject.persist.finder.Finder;
import com.google.inject.persist.finder.FirstResult;
import com.google.inject.persist.finder.MaxResults;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;

/**
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
class JpaFinderDescriptorFactory {

  public JpaFinderProxy.FinderDescriptor createDescriptor(Method method) {
    JpaFinderProxy.FinderDescriptor finderDescriptor;
    finderDescriptor = new JpaFinderProxy.FinderDescriptor();

    //determine return type
    finderDescriptor.returnClass = method.getReturnType();
    finderDescriptor.returnType = determineReturnType(finderDescriptor.returnClass);

    //determine finder query characteristics
    Finder finder = method.getAnnotation(Finder.class);
    String query = finder.query();
    if (!"".equals(query.trim())) {
      finderDescriptor.setQuery(query);
    } else {
      finderDescriptor.setNamedQuery(finder.namedQuery());
    }

    //determine parameter annotations
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    Object[] discoveredAnnotations = new Object[parameterAnnotations.length];
    for (int i = 0; i < parameterAnnotations.length; i++) {
      Annotation[] annotations = parameterAnnotations[i];
      //each annotation per param
      for (Annotation annotation : annotations) {
        //discover the named, first or max annotations then break out
        Class<? extends Annotation> annotationType = annotation.annotationType();
        if (Named.class.equals(annotationType) || javax.inject.Named.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          finderDescriptor.isBindAsRawParameters = false;
          break;
        } else if (FirstResult.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          break;
        } else if (MaxResults.class.equals(annotationType)) {
          discoveredAnnotations[i] = annotation;
          break;
        } //leave as null for no binding
      }
    }

    //set the discovered set to our finder cache object
    finderDescriptor.parameterAnnotations = discoveredAnnotations;

    //discover the returned collection implementation if this finder returns a collection
    if (JpaFinderProxy.ReturnType.COLLECTION.equals(finderDescriptor.returnType)
        && finderDescriptor.returnClass != Collection.class) {
      finderDescriptor.returnCollectionType = (Class<? extends Collection<?>>) finder.returnAs();
      try {
        finderDescriptor.returnCollectionTypeConstructor =
            finderDescriptor.returnCollectionType.getConstructor();
        finderDescriptor.returnCollectionTypeConstructor.setAccessible(true);
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(
            "Finder's collection return type specified has no default constructor! returnAs: "
                + finderDescriptor.returnCollectionType,
            e);
      }
    }
    return finderDescriptor;
  }

  private JpaFinderProxy.ReturnType determineReturnType(Class<?> returnClass) {
    if (Collection.class.isAssignableFrom(returnClass)) {
      return JpaFinderProxy.ReturnType.COLLECTION;
    } else if (returnClass.isArray()) {
      return JpaFinderProxy.ReturnType.ARRAY;
    }

    return JpaFinderProxy.ReturnType.PLAIN;
  }
}
