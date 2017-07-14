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

package com.google.inject.spi;

import com.google.common.collect.ImmutableList;
import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.matcher.Matcher;
import java.util.List;

/**
 * Binds keys (picked using a Matcher) to a provision listener. Listeners are created explicitly in
 * a module using {@link Binder#bindListener(Matcher, ProvisionListener...)} statements:
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.0
 */
public final class ProvisionListenerBinding implements Element {

  private final Object source;
  private final Matcher<? super Binding<?>> bindingMatcher;
  private final List<ProvisionListener> listeners;

  ProvisionListenerBinding(
      Object source, Matcher<? super Binding<?>> bindingMatcher, ProvisionListener[] listeners) {
    this.source = source;
    this.bindingMatcher = bindingMatcher;
    this.listeners = ImmutableList.copyOf(listeners);
  }

  /** Returns the registered listeners. */
  public List<ProvisionListener> getListeners() {
    return listeners;
  }

  /**
   * Returns the binding matcher which chooses which bindings the listener should be notified of.
   */
  public Matcher<? super Binding<?>> getBindingMatcher() {
    return bindingMatcher;
  }

  @Override
  public Object getSource() {
    return source;
  }

  @Override
  public <R> R acceptVisitor(ElementVisitor<R> visitor) {
    return visitor.visit(this);
  }

  @Override
  public void applyTo(Binder binder) {
    binder
        .withSource(getSource())
        .bindListener(bindingMatcher, listeners.toArray(new ProvisionListener[listeners.size()]));
  }
}
