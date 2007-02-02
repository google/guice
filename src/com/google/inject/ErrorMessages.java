/**
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

package com.google.inject;

/**
 * Error message templates.
 *
 * @author crazybob@google.com (Bob Lee)
 */
class ErrorMessages {

  static final String MISSING_BINDING =
      "Binding to %2$s not found, but %1$s requires it.";

  static final String CONSTRUCTOR_RULES = "Classes must have either one (and"
      + " only one) constructor annotated with @Inject or a zero-argument"
      + " constructor.";

  static final String MISSING_CONSTRUCTOR = "Could not find a suitable"
      + " constructor in %s. " + CONSTRUCTOR_RULES;

  static final String TOO_MANY_CONSTRUCTORS = "More than one constructor"
      + " annotated with @Inject found in %s. " + CONSTRUCTOR_RULES;

  static final String DUPLICATE_SCOPES =
      "A scope named '%s' already exists.";

  static final String MISSING_CONSTANT_VALUE = "Missing constant value. Please"
      + " call to(...).";

  static final String MISSING_LINK_DESTINATION = "Missing link destination."
      + " Please call to(Key<?>).";

  static final String LINK_DESTINATION_NOT_FOUND = "Binding to %s not found.";

  static final String CANNOT_INJECT_INTERFACE = "Injecting into interfaces is"
      + " not supported. Please use a concrete type instead of %s.";

  static final String NAME_ALREADY_SET = "Binding name is set more than once.";

  static final String IMPLEMENTATION_ALREADY_SET =
      "Implementation is set more than once.";

  static final String SCOPE_NOT_FOUND = "Scope named '%s' not found."
      + " Available scope names: %s";

  static final String SCOPE_ALREADY_SET = "Scope is set more than once."
      + " You can set the scope by calling in(...) or by annotating the"
      + " implementation class with @Scoped.";

  static final String CONSTANT_VALUE_ALREADY_SET =
      "Constant value is set more than once.";

  static final String LINK_DESTINATION_ALREADY_SET = "Link destination is"
      + " set more than once.";

  static final String BINDING_ALREADY_SET = "A binding to %s was already"
      + " configured at %s.";

  static final String NAME_ON_MEMBER_WITH_MULTIPLE_PARAMS = "Member-level"
      + " @Inject name is not allowed when the member has more than one"
      + " parameter: %s";

  static final String NAME_ON_MEMBER_AND_PARAMETER = "Ambiguous binding name"
      + " between @Inject on member and parameter: %s. Please remove the name"
      + " from the member-level @Inject or remove @Inject from the parameter.";

  static final String PRELOAD_NOT_ALLOWED = "Preloading is only supported for"
      + " container-scoped bindings.";
}
