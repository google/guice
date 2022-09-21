/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.inject;

import static com.google.common.base.StandardSystemProperty.JAVA_SPECIFICATION_VERSION;

import java.net.InetAddress;
import junit.framework.TestCase;

/**
 * This test is split out from ImplicitBindingTest so that we can run it with --add_opens to
 * InetAddress, so validate that it will work when opened.
 */
public class ImplicitBindingJdkPackagePrivateTest extends TestCase {

  public void testImplicitJdkBindings_packagePrivateCxtor() {
    Injector injector = Guice.createInjector();
    // Validate that, when the JDK allows it, we can construct package private JDK things.
    if (Double.parseDouble(JAVA_SPECIFICATION_VERSION.value()) < 17) {
      assertNotNull(injector.getInstance(InetAddress.class));
    }
  }
}
