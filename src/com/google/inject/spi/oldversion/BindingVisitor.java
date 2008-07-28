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
 * Visits bindings. Pass an implementation of {@code BindingVisitor} to
 * {@link OldVersionBinding#accept(BindingVisitor)} and the binding
 * will call back to the appropriate visitor method for its type.
 *
 * @deprecated replaced with {@link com.google.inject.Binding.Visitor}
 *
 * @author crazybob@google.com (Bob Lee)
 */
@Deprecated
public interface BindingVisitor<T> {

  /**
   * Visits a linked binding.
   */
  void visit(LinkedBinding<? extends T> binding);

  /**
   * Visits a binding to an instance.
   */
  void visit(InstanceBinding<? extends T> binding);

  /**
   * Visits a binding to a provider instance.
   */
  void visit(ProviderInstanceBinding<? extends T> binding);

  /**
   * Visits a binding which resolves instances from a bound provider.
   */
  void visit(LinkedProviderBinding<? extends T> binding);

  /**
   * Visits a synthetic binding to the provider from a binding.
   */
  // To provide any more type safety, the compiler would have to know that
  // T extends Provider<X>.
  void visit(ProviderBinding<?> binding);

  /**
   * Visits a class binding.
   */
  void visit(ClassBinding<? extends T> binding);

  /**
   * Visits a constant binding.
   */
  void visit(ConstantBinding<? extends T> binding);

  /**
   * Visits a converted constant binding.
   */
  void visit(ConvertedConstantBinding<? extends T> binding);
}
