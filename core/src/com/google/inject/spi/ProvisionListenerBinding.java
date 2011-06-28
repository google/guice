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

package com.google.inject.spi;

import java.util.List;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.common.collect.ImmutableList;
import com.google.inject.matcher.Matcher;

/**
 * Binds keys (picked using a Matcher) to a provision listener. Listeners are created explicitly in
 * a module using {@link Binder#bindListener(Matcher, ProvisionListener)} statements:
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.0
 */
public final class ProvisionListenerBinding implements Element {

  private final Object source;
  private final Matcher<? super Key<?>> keyMatcher;
  private final List<ProvisionListener> listeners;

  ProvisionListenerBinding(Object source,
      Matcher<? super Key<?>> typeMatcher,
      ProvisionListener[] listeners) {
    this.source = source;
    this.keyMatcher = typeMatcher;
    this.listeners = ImmutableList.of(listeners);
  }

  /** Returns the registered listeners. */
  public List<ProvisionListener> getListeners() {
    return listeners;
  }

  /** Returns the key matcher which chooses which keys the listener should be notified of. */
  public Matcher<? super Key<?>> getKeyMatcher() {
    return keyMatcher;
  }

  public Object getSource() {
    return source;
  }

  public <R> R acceptVisitor(ElementVisitor<R> visitor) {
    return visitor.visit(this);
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).bindListener(keyMatcher,
        listeners.toArray(new ProvisionListener[listeners.size()]));
  }
}
