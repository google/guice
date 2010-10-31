/**
 * Copyright (C) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.inject.servlet;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;

import com.google.inject.servlet.ServletModule.FilterKeyBindingBuilder;
import com.google.inject.servlet.ServletModule.ServletKeyBindingBuilder;
import com.google.inject.spi.BindingTargetVisitor;

/**
 * A visitor for the servlet extension.
 * 
 * If your {@link BindingTargetVisitor} implements this interface, bindings created by using
 * {@link ServletModule} will be visited through this interface.
 * 
 * @since 3.0
 * @author sameb@google.com (Sam Berlin)
 */
public interface ServletModuleTargetVisitor<T, V> extends BindingTargetVisitor<T, V> {

  /**
   * Visits a filter binding created by {@link ServletModule#filter}, where
   * {@link FilterKeyBindingBuilder#through} is called with a Class or Key.
   * 
   * If multiple patterns were specified, this will be called multiple times.
   */
  V visit(LinkedFilterBinding binding);

  /**
   * Visits a filter binding created by {@link ServletModule#filter} where
   * {@link FilterKeyBindingBuilder#through} is called with a {@link Filter}.
   * 
   * If multiple patterns were specified, this will be called multiple times. 
   */
  V visit(InstanceFilterBinding binding);

  /**
   * Visits a servlet binding created by {@link ServletModule#serve} where
   * {@link ServletKeyBindingBuilder#with}, is called with a Class or Key.
   * 
   * If multiple patterns were specified, this will be called multiple times.
   */
  V visit(LinkedServletBinding binding);

  /**
   * Visits a servlet binding created by {@link ServletModule#serve} where 
   * {@link ServletKeyBindingBuilder#with}, is called with an {@link HttpServlet}.
   * 
   * If multiple patterns were specified, this will be called multiple times.
   */
  V visit(InstanceServletBinding binding);
}