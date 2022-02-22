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
import com.google.inject.persist.finder.FirstResult;
import com.google.inject.persist.finder.MaxResults;
import javax.persistence.Query;

/**
 *
 * @author Dhanji R. Prasanna (dhanji@gmail.com)
 * @author Krzysztof Sierszeń (krzysztof.sierszen@digitalnewagency.com)
 */
class JpaFinderParameterBinder {

  public void bindParameters(Object[] args, JpaFinderProxy.FinderDescriptor finderDescriptor, Query jpaQuery) {
    if (finderDescriptor.isBindAsRawParameters) {
      bindQueryRawParameters(jpaQuery, finderDescriptor, args);
    } else {
      bindQueryNamedParameters(jpaQuery, finderDescriptor, args);
    }
  }

  private void bindQueryNamedParameters(
      Query jpaQuery, JpaFinderProxy.FinderDescriptor descriptor, Object[] arguments) {
    for (int i = 0; i < arguments.length; i++) {
      Object annotation = descriptor.parameterAnnotations[i];
      if (annotation != null) {
        bindQueryNamedParameter(jpaQuery, arguments[i], annotation);
      }
    }
  }

  private void bindQueryRawParameters(
      Query jpaQuery, JpaFinderProxy.FinderDescriptor descriptor, Object[] arguments) {
    for (int i = 0, index = 1; i < arguments.length; i++) {
      Object argument = arguments[i];
      Object annotation = descriptor.parameterAnnotations[i];

      if (null == annotation) {
        //bind it as a raw param (1-based index, yes I know its different from Hibernate, blargh)
        jpaQuery.setParameter(index, argument);
        index++;
      } else {
        bindQuerySpecialParameter(annotation, jpaQuery, (Integer) argument);
      }
    }
  }

  private void bindQueryNamedParameter(Query jpaQuery, Object argument, Object annotation) {
    if (annotation instanceof Named) {
      Named named = (Named) annotation;
      jpaQuery.setParameter(named.value(), argument);
    } else if (annotation instanceof javax.inject.Named) {
      javax.inject.Named named = (javax.inject.Named) annotation;
      jpaQuery.setParameter(named.value(), argument);
    } else {
      bindQuerySpecialParameter(annotation, jpaQuery, (Integer) argument);
    }
  }

  private void bindQuerySpecialParameter(Object annotation, Query jpaQuery, Integer argument) {
    if (annotation instanceof FirstResult) {
      jpaQuery.setFirstResult(argument);
    } else if (annotation instanceof MaxResults) {
      jpaQuery.setMaxResults(argument);
    }
  }
}
