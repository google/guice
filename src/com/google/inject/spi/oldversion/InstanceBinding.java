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

import com.google.inject.spi.HasInjections;

/**
 * A binding to a single instance or constant.
 *
 * <p>Examples: <pre>
 *   bind(Runnable.class).toInstance(new MyRunnable());
 *   bindConstant().annotatedWith(PoolSize.class).to(5);
 * </pre>
 *
 * @deprecated replaced with {@link
 * com.google.inject.spi.BindingTargetVisitor#visitInstance(Object)}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface InstanceBinding<T> extends OldVersionBinding<T>, HasInjections {

  /**
   * Gets the instance associated with this binding.
   */
  T getInstance();
}
