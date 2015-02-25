/**
 * Copyright (C) 2015 Google Inc.
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

package com.google.inject.multibindings;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.inject.Module;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotates methods of a {@link Module} to add items to a {@link Multibinder}.
 * The method's return type and binding annotation determines what Set this will
 * contribute to. For example,
 *
 * <pre>
 * {@literal @}ProvidesIntoSet
 * {@literal @}Named("urls")
 * String provideFooUrl(FooManager fm) { returm fm.getUrl(); }
 *
 * {@literal @}ProvidesIntoSet
 * {@literal @}Named("urls")
 * String provideBarUrl(BarManager bm) { return bm.getUrl(); }
 * </pre>
 *
 * will add two items to the {@code @Named("urls") Set<String>} set. The items are bound as
 * providers and will be evaluated at injection time.
 *
 * @author sameb@google.com (Sam Berlin)
 * @since 4.0
 */
@Documented
@Target(METHOD)
@Retention(RUNTIME)
public @interface ProvidesIntoSet {
}
