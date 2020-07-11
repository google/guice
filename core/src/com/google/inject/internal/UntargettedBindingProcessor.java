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

import com.google.inject.Binding;
import com.google.inject.spi.UntargettedBinding;

/**
 * Processes just UntargettedBindings.
 *
 * @author sameb@google.com (Sam Berlin)
 */
class UntargettedBindingProcessor extends AbstractBindingProcessor {

  UntargettedBindingProcessor(Errors errors, ProcessedBindingData processedBindingData) {
    super(errors, processedBindingData);
  }

  @Override
  public <T> Boolean visit(Binding<T> binding) {
    return binding.acceptTargetVisitor(
        new Processor<T, Boolean>((BindingImpl<T>) binding) {
          @Override
          public Boolean visit(UntargettedBinding<? extends T> untargetted) {
            prepareBinding();

            // Error: Missing implementation.
            // Example: bind(Date.class).annotatedWith(Red.class);
            // We can't assume abstract types aren't injectable. They may have an
            // @ImplementedBy annotation or something.
            if (key.getAnnotationType() != null) {
              errors.missingImplementationWithHint(key, injector);
              putBinding(invalidBinding(injector, key, source));
              return true;
            }

            // This cast is safe after the preceeding check.
            try {
              BindingImpl<T> binding =
                  injector.createUninitializedBinding(key, scoping, source, errors, false);
              scheduleInitialization(binding);
              putBinding(binding);
            } catch (ErrorsException e) {
              errors.merge(e.getErrors());
              putBinding(invalidBinding(injector, key, source));
            }

            return true;
          }

          @Override
          protected Boolean visitOther(Binding<? extends T> binding) {
            return false;
          }
        });
  }
}
