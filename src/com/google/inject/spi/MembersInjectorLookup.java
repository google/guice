/**
 * Copyright (C) 2009 Google Inc.
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

import com.google.inject.Binder;
import com.google.inject.MembersInjector;
import com.google.inject.TypeLiteral;
import static com.google.inject.internal.Preconditions.checkNotNull;
import static com.google.inject.internal.Preconditions.checkState;

/**
 * A lookup of the members injector for a type. Lookups are created explicitly in a module using
 * {@link com.google.inject.Binder#getMembersInjector(Class) getMembersInjector()} statements:
 * <pre>
 *     MembersInjector&lt;PaymentService&gt; membersInjector
 *         = getMembersInjector(PaymentService.class);</pre>
 *
 * @author crazybob@google.com (Bob Lee)
 * @since 2.0
 */
public final class MembersInjectorLookup<T> implements Element {

  private final Object source;
  private final TypeLiteral<T> typeLiteral;
  private MembersInjector<T> delegate;

  MembersInjectorLookup(Object source, TypeLiteral<T> typeLiteral) {
    this.source = checkNotNull(source, "source");
    this.typeLiteral = checkNotNull(typeLiteral, "typeLiteral");
  }

  public Object getSource() {
    return source;
  }

  /**
   * Gets the type containing the members to be injected.
   */
  public TypeLiteral<T> getTypeLiteral() {
    return typeLiteral;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }

  /**
   * Sets the actual members injector.
   *
   * @throws IllegalStateException if the delegate is already set
   */
  public void initializeDelegate(MembersInjector<T> delegate) {
    checkState(this.delegate == null, "delegate already initialized");
    this.delegate = checkNotNull(delegate, "delegate");
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).getMembersInjector(typeLiteral);
  }

  /**
   * Returns the delegate members injector, or {@code null} if it has not yet been initialized.
   * The delegate will be initialized when this element is processed, or otherwise used to create
   * an injector.
   */
  public MembersInjector<T> getDelegate() {
    return delegate;
  }
}