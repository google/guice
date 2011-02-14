/**
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

package com.google.inject.persist;

import com.google.inject.AbstractModule;
import static com.google.inject.matcher.Matchers.annotatedWith;
import static com.google.inject.matcher.Matchers.any;
import org.aopalliance.intercept.MethodInterceptor;

/**
 * Install this module to add guice-persist library support for JPA persistence
 * providers.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public abstract class PersistModule extends AbstractModule {

  @Override
  protected final void configure() {
    configurePersistence();

    requireBinding(PersistService.class);
    requireBinding(UnitOfWork.class);
    /*if[AOP]*/
    // wrapping in an if[AOP] just to allow this to compile in NO_AOP -- it won't be used
    
    // class-level @Transacational
    bindInterceptor(annotatedWith(Transactional.class), any(), getTransactionInterceptor());
    // method-level @Transacational
    bindInterceptor(any(), annotatedWith(Transactional.class), getTransactionInterceptor());
    /*end[AOP]*/
  }

  protected abstract void configurePersistence();

  protected abstract MethodInterceptor getTransactionInterceptor();
}
