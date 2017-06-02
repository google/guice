/*
 * Copyright (C) 2014 Google Inc.
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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.spi.ProvisionListenerBinding;
import junit.framework.TestCase;

import java.util.Collections;

/** Tests for {@link com.google.inject.internal.ProvisionListenerCallbackStore}. */
public class ProvisionListenerCallbackStoreTest extends TestCase {
  private ProvisionListenerCallbackStore store;
  private Injector injector;

  @Override
  protected void setUp() throws Exception {
    store = new ProvisionListenerCallbackStore(Collections.<ProvisionListenerBinding>emptyList());
    injector = Guice.createInjector(
        new AbstractModule() {
          @Override
          protected void configure() {
            bind(TestObject1.class);
          }
        });
  }

  public void testRemoveFromEmpty() {
    assertFalse(store.remove(injector.getBinding(TestObject1.class)));
  }

  public void testRemoveNonExisting() {
    store.get(injector.getBinding(TestObject1.class));
    assertFalse(store.remove(injector.getBinding(TestObject2.class)));
  }

  public void testRemoveExisting() {
    store.get(injector.getBinding(TestObject1.class));
    assertTrue(store.remove(injector.getBinding(TestObject1.class)));
  }

  private static class TestObject1 {};
  private static class TestObject2 {};
}
