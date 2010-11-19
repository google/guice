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

package com.google.inject.spi;

import com.google.inject.Binder;
import com.google.inject.TypeLiteral;
import static com.google.inject.internal.util.Preconditions.checkNotNull;
import com.google.inject.matcher.Matcher;

/**
 * Registration of type converters for matching target types. Instances are created
 * explicitly in a module using {@link com.google.inject.Binder#convertToTypes(Matcher,
 * TypeConverter) convertToTypes()} statements:
 * <pre>
 *     convertToTypes(Matchers.only(TypeLiteral.get(DateTime.class)), new DateTimeConverter());</pre>
 *
 * @author jessewilson@google.com (Jesse Wilson)
 * @since 2.0
 */
public final class TypeConverterBinding implements Element {
  private final Object source;
  private final Matcher<? super TypeLiteral<?>> typeMatcher;
  private final TypeConverter typeConverter;

  /** @since 3.0 */
  public TypeConverterBinding(Object source, Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter) {
    this.source = checkNotNull(source, "source");
    this.typeMatcher = checkNotNull(typeMatcher, "typeMatcher");
    this.typeConverter = checkNotNull(typeConverter, "typeConverter");
  }

  public Object getSource() {
    return source;
  }

  public Matcher<? super TypeLiteral<?>> getTypeMatcher() {
    return typeMatcher;
  }

  public TypeConverter getTypeConverter() {
    return typeConverter;
  }

  public <T> T acceptVisitor(ElementVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public void applyTo(Binder binder) {
    binder.withSource(getSource()).convertToTypes(typeMatcher, typeConverter);
  }

  @Override public String toString() {
    return typeConverter + " which matches " + typeMatcher
        + " (bound at " + source + ")";
  }
}
