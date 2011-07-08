/*
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

package com.google.inject.util;

import com.google.inject.Provider;

import junit.framework.TestCase;

/**
 * Unit tests for {@link Providers}.
 *
 * @author Kevin Bourrillion (kevinb9n@gmail.com)
 */
public class ProvidersTest extends TestCase {

  public void testOfInstance() {
    String foo = "foo";
    Provider<String> p = Providers.of(foo);
    assertSame(foo, p.get());
    assertSame(foo, p.get());
  }

  public void testOfNull() {
    Provider<String> p = Providers.of(null);
    assertNull(p.get());
  }
}
