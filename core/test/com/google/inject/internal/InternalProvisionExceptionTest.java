/*
 * Copyright (C) 2017 Google Inc.
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

import static com.google.common.truth.Truth.assertThat;

import com.google.inject.internal.util.SourceProvider;
import junit.framework.TestCase;

public final class InternalProvisionExceptionTest extends TestCase {

  public void testSourceFormatting() {
    // Note that the duplicate source gets dropped as well as the unknown source
    final String LINE_SEPARATOR = System.lineSeparator();
    assertThat(
            InternalProvisionException.create("An error")
                .addSource("Source1")
                .addSource(SourceProvider.UNKNOWN_SOURCE)
                .addSource("Source2")
                .addSource("Source2")
                .toProvisionException()
                .getMessage())
        .isEqualTo(
            ""
                + "Unable to provision, see the following errors:" + LINE_SEPARATOR 
                + LINE_SEPARATOR 
                + "1) An error" + LINE_SEPARATOR 
                + "  at Source1" + LINE_SEPARATOR 
                + "  at Source2" + LINE_SEPARATOR 
                + "" + LINE_SEPARATOR 
                + "1 error");
  }
}
