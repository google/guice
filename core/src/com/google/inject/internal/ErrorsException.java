/*
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

/**
 * Indicates that a result could not be returned while preparing or resolving a binding. The caller
 * should {@link Errors#merge(Errors) merge} the errors from this exception with their existing
 * errors.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
public class ErrorsException extends Exception {
  // NOTE: this is used by Gin which is abandoned.  So changing this API will prevent Gin users from
  // upgrading Guice version.

  private final Errors errors;

  public ErrorsException(Errors errors) {
    this.errors = errors;
  }

  public Errors getErrors() {
    return errors;
  }
}
