/*
 * Copyright (C) 2011 Google Inc.
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

import com.google.inject.internal.InjectorImpl.JitLimitation;
import com.google.inject.spi.MembersInjectorLookup;
import com.google.inject.spi.ProviderLookup;

/** Processes just MembersInjectorLookups and ProviderLookups to create their bindings. */
final class LookupBindingProcessor extends AbstractProcessor {

  LookupBindingProcessor(Errors errors) {
    super(errors);
  }

  @Override
  public <T> Boolean visit(MembersInjectorLookup<T> lookup) {
    injector.getBindingData().putMembersInjectorLookup(lookup);
    // Members injector lookups are resolved by the lookup processor but may require jit bindings to
    // be created, do that now, so that other phases can see them.
    try {
      var unused = injector.membersInjectorStore.get(lookup.getType(), errors);
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }
    return false; // leave the lookups for the LookupProcessor to handle
  }

  @Override
  public <T> Boolean visit(ProviderLookup<T> lookup) {
    injector.getBindingData().putProviderLookup(lookup);
    // Provider lookups are resolved by the lookup processor but may require jit bindings to be
    // created, do that now, so that other phases can see them.
    try {
      var unused = injector.getBindingOrThrow(lookup.getKey(), errors, JitLimitation.NO_JIT);
      // ProviderLookups need a Provider but we cannot create it yet since it is too early in the
      // processing.
    } catch (ErrorsException e) {
      errors.merge(e.getErrors());
    }

    return false; // leave the lookups for the LookupProcessor to handle
  }
}
