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

import com.google.common.base.Objects;
import com.google.common.testing.EqualsTester;
import com.google.inject.Provider;
import javax.inject.Inject;
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

  public void testOfEquality() {
    new EqualsTester()
        .addEqualityGroup(Providers.of(null), Providers.of(null))
        .addEqualityGroup(Providers.of("Hello"), Providers.of("Hello"))
        .testEquals();
  }

  public void testGuicifyEquality() {
    new EqualsTester()
        .addEqualityGroup(
            Providers.guicify(new JavaxProvider(10)), Providers.guicify(new JavaxProvider(10)))
        .addEqualityGroup(
            Providers.guicify(new JavaxProvider(11)), Providers.guicify(new JavaxProvider(11)))
        .addEqualityGroup(
            Providers.guicify(new JavaxProviderWithDependencies()),
            Providers.guicify(new JavaxProviderWithDependencies()))
        .testEquals();
  }

  private static class JavaxProvider implements javax.inject.Provider<Integer> {
    private final int value;

    public JavaxProvider(int value) {
      this.value = value;
    }

    @Override
    public Integer get() {
      return value;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof JavaxProvider) && (value == ((JavaxProvider) obj).value);
    }
  }

  private static class JavaxProviderWithDependencies implements javax.inject.Provider<Integer> {
    private int value;

    @Inject
    void setValue(int value) {
      this.value = value;
    }

    @Override
    public Integer get() {
      return value;
    }

    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public boolean equals(Object obj) {
      return (obj instanceof JavaxProviderWithDependencies);
    }
  }
}
