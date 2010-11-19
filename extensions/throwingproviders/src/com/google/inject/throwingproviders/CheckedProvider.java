/**
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

package com.google.inject.throwingproviders;

/**
 * Alternative to the Guice {@link com.google.inject.Provider} that throws
 * a checked Exception. Users may not inject {@code T} directly.
 *
 * <p>This interface must be extended to use application-specific exception types.
 * Such subinterfaces may not define new methods, but may narrow the exception type.
 * <pre>
 * public interface RemoteProvider&lt;T&gt; extends CheckedProvider&lt;T&gt; { 
 *   T get() throws CustomExceptionOne, CustomExceptionTwo;
 * }
 * </pre>
 *
 * <p>When this type is bound using {@link ThrowingProviderBinder}, the value returned
 * or exception thrown by {@link #get} will be scoped. As a consequence, {@link #get}
 * will invoked at most once within each scope.
 * 
 * @since 3.0
 */
public interface CheckedProvider<T> {
  T get() throws Exception;
}
