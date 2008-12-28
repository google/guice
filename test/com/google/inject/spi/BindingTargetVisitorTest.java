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
package com.google.inject.spi;

import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import junit.framework.TestCase;

/**
 * Simple little test that should compile. Ensures that wildcards on the
 * generics are correct.
 *
 * @author phopkins@gmail.com
 */
public class BindingTargetVisitorTest extends TestCase {
  public void testBindingTargetVisitorTypeTest() throws Exception {
    Injector injector = Guice.createInjector();
    for (Binding<?> binding : injector.getBindings().values()) {
      binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<Object, Object>() {});
    }
  }
}
