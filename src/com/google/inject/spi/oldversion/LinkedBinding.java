/**
 * Copyright (C) 2007 Google Inc.
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

package com.google.inject.spi.oldversion;

/**
 * A binding that links to another binding.
 *
 * <p>Example: {@code bind(Collection.class).to(List.class);}
 *
 * @deprecated replaced with {@link
 * com.google.inject.spi.BindingTargetVisitor#visitKey(com.google.inject.Key)}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface LinkedBinding<T> extends OldVersionBinding<T> {

  /**
   * Gets the target of this link.
   */
  OldVersionBinding<? extends T> getTargetBinding();
}
