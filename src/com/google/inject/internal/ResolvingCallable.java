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


package com.google.inject.internal;

import com.google.inject.spi.SourceProviders;

import java.util.concurrent.Callable;

/**
 * An arbitrary body of code that throws a {@link ResolveFailedException}.
 * Only necessary because it's difficult to throw specific types with Callable.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public abstract class ResolvingCallable<T> implements Callable<T> {
  public abstract T call() throws ResolveFailedException;

  public T runWithDefaultSource(Object source) throws ResolveFailedException {
    try {
      return SourceProviders.withDefaultChecked(source, this);
    } catch (ResolveFailedException e) {
      throw e;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new AssertionError();
    }
  }
}
