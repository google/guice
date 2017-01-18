/*
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

import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.spi.MembersInjectorLookup;
import com.google.inject.spi.ProviderLookup;

/**
 * Handles {@code Binder.getProvider} and {@code Binder.getMembersInjector(TypeLiteral)} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 */
final class LookupProcessor extends AbstractProcessor {

  LookupProcessor(Errors errors) {
    super(errors);
  }

  @Override
  public <T> Boolean visit(MembersInjectorLookup<T> lookup) {
    try {
      MembersInjector<T> membersInjector =
          injector.membersInjectorStore.get(lookup.getType(), errors);
      lookup.initializeDelegate(membersInjector);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors()); // TODO: source
    }

    return true;
  }

  @Override
  public <T> Boolean visit(ProviderLookup<T> lookup) {
    // ensure the provider can be created
    try {
      Provider<T> provider = injector.getProviderOrThrow(lookup.getDependency(), errors);
      lookup.initializeDelegate(provider);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors()); // TODO: source
    }

    return true;
  }
}
