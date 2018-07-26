/*
 * Copyright (C) 2006 Google Inc.
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

package com.google.inject.matcher;

import java.io.Serializable;

public class AndMatcher<T> extends AbstractMatcher<T> implements Serializable {
  private final Matcher<? super T> a, b;

  public AndMatcher(Matcher<? super T> a, Matcher<? super T> b) {
    this.a = a;
    this.b = b;
  }

  @Override
  public boolean matches(T t) {
    return a.matches(t) && b.matches(t);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AndMatcher
        && ((AndMatcher) other).a.equals(a)
        && ((AndMatcher) other).b.equals(b);
  }

  @Override
  public int hashCode() {
    return 41 * (a.hashCode() ^ b.hashCode());
  }

  @Override
  public String toString() {
    return "and(" + a + ", " + b + ")";
  }

  private static final long serialVersionUID = 0;
}