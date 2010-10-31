/**
 * Copyright (C) 2010 Google Inc.
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

package com.google.inject.spi;

import com.google.inject.Binding;
import com.google.inject.Provider;

/**
 * A Provider that is part of an extension which supports a custom
 * BindingTargetVisitor.
 * <p> 
 * When an extension binds a provider instance, the provider can implement this
 * interface to allow users using the
 * {@link Binding#acceptTargetVisitor(BindingTargetVisitor)} method to visit a
 * custom visitor designed for that extension. A typical implementation within
 * the extension would look like
 * <pre> 
 * &lt;V, B> V acceptExtensionVisitor(BindingTargetVisitor&lt;B, V> visitor, ProviderInstanceBinding&lt;? extends B> binding) {
 *   if(visitor instanceof MyCustomExtensionVisitor) {
 *     return ((MyCustomExtensionVisitor&lt;B, V>)visitor).visitCustomExtension(customProperties, binding);
 *   } else {
 *     return visitor.visit(binding);
 *   }
 * }</pre> 
 * 'MyCustomExtensionVisitor' in the example above would be an interface the
 * extension provides that users can implement in order to be notified of custom
 * extension information. These visitor interfaces must extend from
 * BindingTargetVisitor.
 *
 * @since 3.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ProviderWithExtensionVisitor<T> extends Provider<T> {

  /**
   * Instructs the extension determine if the visitor is an instance of a custom
   * extension visitor, and if so, visit it using that method. If the visitor is
   * not an instance of the custom extension visitor, this method <b>MUST</b>
   * call visitor.visit(binding).
   * <p> 
   * Due to issues with generics, the type parameters of this method do not
   * relate to the type of the provider. In practice, the 'B' type will always
   * be a supertype of 'T'.
   */
  <B, V> V acceptExtensionVisitor(BindingTargetVisitor<B, V> visitor,
      ProviderInstanceBinding<? extends B> binding);
}
