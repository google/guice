/**
 * Copyright (C) 2010 Google Inc.
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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.Injector;
import com.google.inject.Stage;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Instructs an {@link Injector} running in {@link Stage#TOOL} that a method should be injected.
 * This is typically useful for for extensions to Guice that perform additional validation in an
 * injected method or field.  This only applies to objects that are already constructed when
 * bindings are created (ie., something bound using {@link
 * com.google.inject.binder.LinkedBindingBuilder#toProvider toProvider}, {@link
 * com.google.inject.binder.LinkedBindingBuilder#toInstance toInstance}, or {@link
 * com.google.inject.Binder#requestInjection requestInjection}.
 * 
 * @author sberlin@gmail.com (Sam Berlin)
 * @since 3.0
 */
@Target({ METHOD })
@Retention(RUNTIME)
@Documented
public @interface Toolable {
}
