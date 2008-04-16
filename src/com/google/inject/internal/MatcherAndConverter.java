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

package com.google.inject.internal;

import com.google.inject.matcher.Matcher;
import com.google.inject.spi.TypeConverter;
import com.google.inject.spi.SourceProviders;
import com.google.inject.internal.Objects;
import com.google.inject.TypeLiteral;

/**
 *
 * @author crazybob@google.com (Bob Lee)
 */
public class MatcherAndConverter<T> {

  static {
    SourceProviders.skip(MatcherAndConverter.class);
  }
  
  private final Matcher<? super TypeLiteral<?>> typeMatcher;
  private final TypeConverter typeConverter;
  private final Object source;

  MatcherAndConverter(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter, Object source) {
    this.typeMatcher = Objects.nonNull(typeMatcher, "type matcher");
    this.typeConverter = Objects.nonNull(typeConverter, "converter");
    this.source = source;
  }

  MatcherAndConverter(Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter) {
    this.typeMatcher = Objects.nonNull(typeMatcher, "type matcher");
    this.typeConverter = Objects.nonNull(typeConverter, "converter");
    this.source = SourceProviders.defaultSource();
  }

  public TypeConverter getTypeConverter() {
    return typeConverter;
  }

  public Matcher<? super TypeLiteral<?>> getTypeMatcher() {
    return typeMatcher;
  }

  public static <T> MatcherAndConverter<T> newInstance(
      Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter) {
    return new MatcherAndConverter<T>(typeMatcher, typeConverter);
  }

  public static <T> MatcherAndConverter<T> newInstance(
      Matcher<? super TypeLiteral<?>> typeMatcher,
      TypeConverter typeConverter, Object source) {
    return new MatcherAndConverter<T>(typeMatcher, typeConverter, source);
  }

  public Object getSource() {
    return source;
  }

  @Override public String toString() {
    return typeConverter + " which matches " + typeMatcher
        + " (bound at " + source + ")";
  }
}
