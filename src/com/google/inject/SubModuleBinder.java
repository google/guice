/*
Copyright (C) 2008 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.google.inject;

/**
 * This provides an opportunity for sub-modules to export any bindings
 * they wish to their parent binder.  This allows them to share their
 * unique bindings with other modules or the parent injector.
 *
 * @author dan.halem@gmail.com (Dan Halem)
 */
public interface SubModuleBinder {
  /** Exports the key into the parent module at the same key as the child **/
  <T> SubModuleBinder export(Key<T> key);

  /** Exports the key from the child to the parent as parentKey **/
  <T> SubModuleBinder exportKeyAs(Key<T> childKey, Key<T> parentKey);

  /** Exports the class from the child to the parent at the default key for the class **/
  <T> SubModuleBinder export(Class <? extends T> clazz);
}
