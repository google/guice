/*
 * Copyright (C) 2019 Google Inc.
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
package com.google.inject.daggeradapter;

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.CreationException;
import com.google.inject.Guice;
import dagger.Module;
import dagger.Subcomponent;
import junit.framework.TestCase;

/** Tests for {@code @Module(subcomponents = Foo.class)} */

public class ModuleSubcomponentsTest extends TestCase {

  @Module(subcomponents = TestSubcomponent.class)
  static class ModuleWithSubcomponents {}

  @Subcomponent
  interface TestSubcomponent {
    @Subcomponent.Builder
    interface Builder {
      TestSubcomponent build();
    }
  }

  public void testModuleSubcomponentsNotSupported() {
    try {
      Guice.createInjector(DaggerAdapter.from(ModuleWithSubcomponents.class));
      fail();
    } catch (CreationException expected) {
      assertThat(expected)
          .hasMessageThat()
          .contains("Subcomponents cannot be configured for modules used with DaggerAdapter");
      assertThat(expected).hasMessageThat().contains("ModuleWithSubcomponents specifies: ");
      assertThat(expected).hasMessageThat().contains("TestSubcomponent");
    }
  }
}
