/**
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

import java.util.List;

import com.google.inject.internal.util.Lists;

/**
 * Keeps track of creation listeners & uninitialized bindings,
 * so they can be processed after bindings are recorded.
 * 
 * @author sameb@google.com (Sam Berlin)
 */
class ProcessedBindingData {
  
  private final List<CreationListener> creationListeners = Lists.newArrayList();
  private final List<Runnable> uninitializedBindings = Lists.newArrayList();
  
  void addCreationListener(CreationListener listener) {
    creationListeners.add(listener);
  }
  
  void addUninitializedBinding(Runnable runnable) {
    uninitializedBindings.add(runnable);
  }
  
  void initializeBindings() {
    for (Runnable initializer : uninitializedBindings) {
      initializer.run();
    }
  }

  void runCreationListeners(Errors errors) {
    for (CreationListener creationListener : creationListeners) {
      creationListener.notify(errors);
    }
  }

}
