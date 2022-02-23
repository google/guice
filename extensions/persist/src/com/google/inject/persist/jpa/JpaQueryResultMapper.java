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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import javax.persistence.Query;

/**
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
class JpaQueryResultMapper {

  public Object mapResult(JpaFinderProxy.FinderDescriptor finderDescriptor, Query jpaQuery) {
    switch (finderDescriptor.returnType) {
      case PLAIN:
        return jpaQuery.getSingleResult();
      case COLLECTION:
        return getAsCollection(finderDescriptor, jpaQuery.getResultList());
      case ARRAY:
        return getAsArray(finderDescriptor, jpaQuery.getResultList());
      default:
        throw new IllegalArgumentException(
            "Unrecognized enum value ReturnType." + finderDescriptor.returnType);
    }
  }

  private Object[] getAsArray(JpaFinderProxy.FinderDescriptor finderDescriptor, List<?> resultList) {
    return resultList.toArray(
        (Object[]) Array.newInstance(finderDescriptor.returnClass.getComponentType(), 0));
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // JPA Query returns raw type.
  private Object getAsCollection(JpaFinderProxy.FinderDescriptor finderDescriptor, List results) {
    Collection<?> collection;
    try {
      collection = (Collection) finderDescriptor.returnCollectionTypeConstructor.newInstance();
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(
          "Specified collection class of Finder's returnAs could not be instantated: "
              + finderDescriptor.returnCollectionType,
          e);
    }

    collection.addAll(results);
    return collection;
  }
}
