/**
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.injectioncontroller;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.visitable.intercepting.InterceptingInjectorBuilder;
import junit.framework.TestCase;

import java.util.ArrayList;

/**
 * @author jessewilson@google.com (Jesse Wilson)
 * @author jmourits@google.com (Jerome Mourits)
 */
public class InjectionControllerTest extends TestCase {

  private InjectionController injectionController = new InjectionController();

  public void testSimpleOverride() throws Exception {
    Injector injector = new InterceptingInjectorBuilder()
        .bindModules(injectionController.getModule(),
            new AbstractModule() {
              protected void configure() {
                bind(String.class).toInstance("a");
              }
            })
        .intercept(String.class)
        .build();

    assertEquals("a", injector.getInstance(String.class));
    injectionController.set(String.class, "b");
    assertEquals("b", injector.getInstance(String.class));
  }

  public void testOverrideRequiresWhitelist() throws Exception {
    Injector injector = new InterceptingInjectorBuilder()
        .bindModules(injectionController.getModule(),
            new AbstractModule() {
              protected void configure() {
                bind(String.class).toInstance("a");
              }
            })
        .build();

    injectionController.set(String.class, "b");
    assertEquals("a", injector.getInstance(String.class));
  }

  public void testBareBindingFails() throws Exception {
    InterceptingInjectorBuilder builder = new InterceptingInjectorBuilder()
        .bindModules(injectionController.getModule(),
            new AbstractModule() {
              protected void configure() {
                bind(ArrayList.class);
              }
            })
        .intercept(ArrayList.class);

    try {
      builder.build();
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  public void testCannotOverrideDouble() throws Exception {
    injectionController.set(String.class, "b");
    try {
      injectionController.set(String.class, "c");
      fail();
    } catch(IllegalStateException expected) {
    }
  }
}
